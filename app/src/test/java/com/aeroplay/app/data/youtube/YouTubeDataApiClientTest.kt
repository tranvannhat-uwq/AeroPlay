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
    fun browseUsesOfficialApiAndPreservesSearchPagination() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"abcdefghijk","snippet":{"title":"Song","channelTitle":"Artist"},"contentDetails":{"duration":"PT3M12S"},"statistics":{"viewCount":"12345"}}]}"""))
        val popular = client.popularVideos("sample-access-token")
        assertEquals(192L, popular.single().durationSeconds)
        assertEquals(12345L, popular.single().viewCount)
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", popular.single().url)
        val request = server.takeRequest()
        assertEquals("mostPopular", request.requestUrl?.queryParameter("chart"))
        assertEquals("VN", request.requestUrl?.queryParameter("regionCode"))
        server.enqueue(MockResponse().setBody("""{"items":[{"id":{"videoId":"ZYXwvutsrqp"},"snippet":{"title":"Result","channelTitle":"Artist"}}],"nextPageToken":"page3"}"""))
        val search = client.searchVideos("sample-access-token", "nhạc chill", "page2")
        assertEquals("page3", search.nextPageToken)
        assertEquals("ZYXwvutsrqp", search.videos.single().id)
        val second = server.takeRequest()
        assertEquals("page2", second.requestUrl?.queryParameter("pageToken"))
        assertEquals("video", second.requestUrl?.queryParameter("type"))
        assertEquals("nhạc chill", second.requestUrl?.queryParameter("q"))
        assertEquals("GET", second.method)
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
