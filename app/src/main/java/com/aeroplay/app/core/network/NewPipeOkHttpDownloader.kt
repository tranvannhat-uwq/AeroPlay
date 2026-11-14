package com.aeroplay.app.core.network

import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch

/**
 * Clean OkHttp implementation of NewPipe's [Downloader].
 * Strictly adheres to Rule II.8: Never collects, stores, or sends user login cookies,
 * passwords, or YouTube account tokens.
 */
class NewPipeOkHttpDownloader(
    private val okHttpClient: OkHttpClient
) : Downloader() {

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedIOException("Request cancelled because user left screen")
        }

        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBody = dataToSend?.toRequestBody(null, 0, dataToSend.size)
            ?: if (httpMethod.uppercase() in setOf("POST", "PUT", "PATCH", "PROPPATCH", "REPORT")) ByteArray(0).toRequestBody() else null

        val builder = okhttp3.Request.Builder()
            .url(url)
            .method(httpMethod, requestBody)
            .header("User-Agent", DEFAULT_USER_AGENT)

        for ((headerName, headerValues) in headers) {
            builder.removeHeader(headerName)
            for (value in headerValues) {
                builder.addHeader(headerName, value)
            }
        }

        val httpRequest = builder.build()
        val call = okHttpClient.newCall(httpRequest)
        SourceDiagnostics.event("http.request", "method=$httpMethod host=${httpRequest.url.host}")
        // Keep NewPipe's blocking API, but cancel the OkHttp call when runInterruptible
        // interrupts this worker. execute() alone can remain blocked in a socket read.
        return awaitResponse(call) { okResponse ->
            SourceDiagnostics.event("http.response", "status=${okResponse.code} host=${okResponse.request.url.host}")
            if (okResponse.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested by server (HTTP 429)", url)
            }

            val bodyString = okResponse.body?.string().orEmpty()
            val latestUrl = okResponse.request.url.toString()

            Response(
                okResponse.code,
                okResponse.message,
                okResponse.headers.toMultimap(),
                bodyString,
                latestUrl
            )
        }
    }

    private fun awaitResponse(call: Call, decode: (okhttp3.Response) -> Response): Response {
        val done = CountDownLatch(1)
        val lock = Any()
        var response: Response? = null
        var failure: Exception? = null
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                synchronized(lock) { failure = e }
                done.countDown()
            }

            override fun onResponse(call: Call, result: okhttp3.Response) {
                try {
                    // Read the body on the OkHttp worker too, so interruption can abort a
                    // stalled body as well as a connection waiting for response headers.
                    val decoded = result.use(decode)
                    synchronized(lock) { response = decoded }
                } catch (error: Exception) {
                    synchronized(lock) { failure = error }
                } finally {
                    done.countDown()
                }
            }
        })
        try {
            done.await()
        } catch (interrupted: InterruptedException) {
            call.cancel()
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Extractor request interrupted").apply { initCause(interrupted) }
        }
        return synchronized(lock) {
            failure?.let { throw it }
            checkNotNull(response) { "OkHttp completed without a response" }
        }
    }
}
