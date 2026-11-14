package com.aeroplay.app

import com.aeroplay.app.core.model.SourceError
import com.aeroplay.app.core.network.NewPipeOkHttpDownloader
import com.aeroplay.app.data.source.ExperimentalExtractorSource
import com.aeroplay.app.data.source.SourceErrorException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.net.SocketTimeoutException
import java.io.InterruptedIOException
import kotlin.coroutines.coroutineContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NewPipeDownloaderTest {
    private lateinit var server: MockWebServer

    @Before fun setup() { server = MockWebServer().apply { start() } }
    @After fun close() { server.shutdown() }

    @Test fun preservesPostBytesMultivalueHeadersAndExtractorUserAgent() {
        server.enqueue(MockResponse().setBody("{\"ok\":true}").addHeader("X-Reply", "one").addHeader("X-Reply", "two"))
        val body = "{\"query\":\"âm nhạc\"}".toByteArray()
        val request = Request.newBuilder().post(server.url("/search").toString(), body)
            .setHeader("Content-Type", "application/json")
            .setHeader("User-Agent", "ExtractorClient/1")
            .setHeaders("X-Test", listOf("one", "two")).build()
        val response = NewPipeOkHttpDownloader(OkHttpClient()).execute(request)
        val recorded = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("POST", recorded.method)
        assertArrayEquals(body, recorded.body.readByteArray())
        assertEquals("application/json", recorded.getHeader("Content-Type"))
        assertEquals("ExtractorClient/1", recorded.getHeader("User-Agent"))
        assertEquals(listOf("one", "two"), recorded.headers.values("X-Test"))
        assertEquals(200, response.responseCode())
        assertEquals("{\"ok\":true}", response.responseBody())
        assertEquals(listOf("one", "two"), response.responseHeaders().entries.first { it.key.equals("X-Reply", true) }.value)
    }

    @Test fun followsRedirectAndReturnsFinalUrl() {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/final")))
        server.enqueue(MockResponse().setBody("result"))
        val response = NewPipeOkHttpDownloader(OkHttpClient()).get(server.url("/start").toString())
        assertEquals(server.url("/final").toString(), response.latestUrl())
        assertEquals("result", response.responseBody())
    }

    @Test fun decodesGzipUtf8WithoutManualCompressionHeader() {
        val body = Buffer()
        GzipSink(body).buffer().use { it.writeUtf8("Tìm kiếm tiếng Việt") }
        server.enqueue(MockResponse().setBody(body).addHeader("Content-Encoding", "gzip").addHeader("Content-Type", "text/plain; charset=utf-8"))
        val response = NewPipeOkHttpDownloader(OkHttpClient()).get(server.url("/").toString())
        assertEquals("Tìm kiếm tiếng Việt", response.responseBody())
    }

    @Test fun preservesHttpErrorsForExtractorValidationAndMaps429() {
        val downloader = NewPipeOkHttpDownloader(OkHttpClient())
        for (code in listOf(403, 404, 500)) {
            server.enqueue(MockResponse().setResponseCode(code).setBody("failure"))
            val response = downloader.get(server.url("/").toString())
            assertEquals(code, response.responseCode())
            assertEquals("failure", response.responseBody())
        }
        server.enqueue(MockResponse().setResponseCode(429))
        assertThrows(ReCaptchaException::class.java) { downloader.get(server.url("/").toString()) }
    }

    @Test fun supportsHeadAndEmptyPost() {
        val downloader = NewPipeOkHttpDownloader(OkHttpClient())
        server.enqueue(MockResponse())
        assertEquals("", downloader.execute(Request.newBuilder().head(server.url("/").toString()).build()).responseBody())
        assertEquals("HEAD", server.takeRequest().method)
        server.enqueue(MockResponse())
        downloader.execute(Request.newBuilder().httpMethod("POST").url(server.url("/").toString()).build())
        assertEquals("POST", server.takeRequest().method)
    }

    @Test fun cancellationAbortsStalledResponseBody() = runBlocking {
        val canceled = CountDownLatch(1)
        val client = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun canceled(call: Call) { canceled.countDown() }
        }).build()
        val downloader = NewPipeOkHttpDownloader(client)
        server.enqueue(MockResponse().setBody("delayed").setBodyDelay(3, TimeUnit.SECONDS))
        val job = launch(Dispatchers.IO) {
            try {
                runInterruptible { downloader.get(server.url("/").toString()) }
            } catch (error: InterruptedIOException) {
                coroutineContext.ensureActive()
                throw error
            }
        }
        assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
        withTimeout(1_000) { job.cancelAndJoin() }
        assertTrue("Coroutine cancellation must cancel the underlying socket", canceled.await(1, TimeUnit.SECONDS))
        assertTrue(job.isCancelled)
    }

    @Test fun socketTimeoutReturnsNetworkFailureRatherThanCoroutineCancellation() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { throw SocketTimeoutException("test timeout") }.build()
        val downloader = NewPipeOkHttpDownloader(client)
        NewPipe.init(downloader, Localization("vi", "VN"), ContentCountry("VN"))
        val result = ExperimentalExtractorSource(downloader).search("timeout regression")
        assertTrue(result.isFailure)
        assertTrue((result.exceptionOrNull() as? SourceErrorException)?.sourceError is SourceError.NetworkError)
    }
}
