package com.aeroplay.app

import com.aeroplay.app.core.network.NetworkModule
import com.aeroplay.app.core.network.NewPipeOkHttpDownloader
import com.aeroplay.app.data.repository.StreamResolverImpl
import com.aeroplay.app.data.source.ExperimentalExtractorSource
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

/** Opt in: ./gradlew testDebugUnitTest -Paeroplay.liveTests=true --tests '*YouTubeLiveTest'. */
class YouTubeLiveTest {
    private lateinit var source: ExperimentalExtractorSource
    private lateinit var client: OkHttpClient

    @Before fun setup() {
        assumeTrue("Live network tests require -Paeroplay.liveTests=true", java.lang.Boolean.getBoolean("aeroplay.liveTests"))
        client = NetworkModule.provideOkHttpClient().newBuilder().addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            println("HTTP ${request.method} ${request.url.host} -> ${response.code} ${response.header("Content-Type")}")
            response
        }.build()
        val downloader = NewPipeOkHttpDownloader(client)
        NewPipe.init(downloader, Localization("vi", "VN"), ContentCountry("VN"))
        source = ExperimentalExtractorSource(downloader)
    }

    @Test fun homeReturnsRealYouTubeVideos() = runBlocking {
        val videos = source.fetchTrendingOrFeatured().getOrThrow()
        assertTrue("Home must contain real videos", videos.isNotEmpty())
        println("HOME videos=${videos.size} first=${videos.first().id}")
    }

    @Test fun searchAndPaginationReturnRealYouTubeVideos() = runBlocking {
        val result = source.search("Blender Big Buck Bunny").getOrThrow()
        assertTrue("Search must contain real videos", result.videos.isNotEmpty())
        println("SEARCH videos=${result.videos.size} first=${result.videos.first().id}")
        val token = requireNotNull(result.nextPageToken) { "Expected pagination token" }
        val next = source.search(result.query, token).getOrThrow()
        assertTrue("Next page must contain real videos", next.videos.isNotEmpty())
        println("NEXT videos=${next.videos.size}")
    }

    @Test fun metadataAndSelectedStreamsAreReadable() = runBlocking {
        val detail = source.extractVideoDetail("https://www.youtube.com/watch?v=aqz-KE-bpKQ").getOrThrow()
        assertTrue(detail.title.isNotBlank())
        println("DETAIL id=${detail.id} video=${detail.streamVariants.size} audio=${detail.audioTracks.size}")
        val selection = StreamResolverImpl().resolveStream(detail).getOrThrow()
        for (url in listOfNotNull(selection.primaryUrl, selection.secondaryAudioUrl)) {
            client.newCall(okhttp3.Request.Builder().url(url).header("Range", "bytes=0-1023").build()).execute().use { response ->
                assertTrue("Selected stream HTTP ${response.code}", response.isSuccessful)
                assertTrue("Selected stream must return bytes", (response.body?.source()?.readByteArray(128)?.size ?: 0) > 0)
            }
        }
    }
}
