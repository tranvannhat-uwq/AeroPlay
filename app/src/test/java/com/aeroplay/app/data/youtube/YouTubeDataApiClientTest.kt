package com.aeroplay.app.data.youtube

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class YouTubeDataApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: YouTubeDataApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = YouTubeDataApiClient(OkHttpClient(), server.url("youtube/v3/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun likedVideosAreReadOnlyAndPaginationIsFollowed() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"items":[{"id":"video-a","snippet":{"title":"Track A","channelTitle":"Artist A","publishedAt":"2026-01-01T00:00:00Z","thumbnails":{"high":{"url":"https://img.example/a.jpg"}}}}],"nextPageToken":"next-page"}
        """.trimIndent()))
        server.enqueue(MockResponse().setBody("""
            {"items":[{"id":"video-b","snippet":{"title":"Track B","channelTitle":"Artist B"}}]}
        """.trimIndent()))

        val videos = client.listLikedVideos("sample-access-token")

        assertEquals(listOf("video-a", "video-b"), videos.map { it.videoId })
        assertEquals("Track A", videos.first().title)
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("GET", first.method)
        assertEquals("Bearer sample-access-token", first.getHeader("Authorization"))
        assertEquals("like", first.requestUrl?.queryParameter("myRating"))
        assertEquals("snippet", first.requestUrl?.queryParameter("part"))
        assertEquals(null, first.requestUrl?.queryParameter("key"))
        assertEquals("next-page", second.requestUrl?.queryParameter("pageToken"))
    }

    @Test
    fun quotaErrorIsReportedWithoutRetryingForbiddenRequest() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody("""
                {"error":{"message":"Quota exceeded","errors":[{"reason":"quotaExceeded"}]}}
            """.trimIndent())
        )

        val error = runCatching { client.listMySubscriptions("token") }.exceptionOrNull()

        assertTrue(error is YouTubeApiException)
        assertEquals(403, (error as YouTubeApiException).httpCode)
        assertTrue(error.message.orEmpty().contains("quota", ignoreCase = true))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun transientHttpErrorRetriesWithBoundedAttempts() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        server.enqueue(MockResponse().setBody("""{"items":[]}"""))

        val result = client.listMyChannels("token")

        assertTrue(result.isEmpty())
        assertEquals(2, server.requestCount)
        assertEquals("GET", server.takeRequest().method)
        assertEquals("GET", server.takeRequest().method)
    }

    @Test
    fun apiClientNeverSendsRequestBodies() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"items":[]}"""))

        client.listMyPlaylists("token")

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertFalse(request.body.readUtf8().isNotBlank())
    }

    @Test
    fun paginationSafetyLimitFailsInsteadOfSilentlyTruncating() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setBody("""{"items":[],"nextPageToken":"continued"}""")
        }

        val error = runCatching { client.listMyChannels("token") }.exceptionOrNull()

        assertTrue(error is YouTubeApiException)
        assertEquals("paginationLimit", (error as YouTubeApiException).reason)
        assertEquals(100, server.requestCount)
    }
}
