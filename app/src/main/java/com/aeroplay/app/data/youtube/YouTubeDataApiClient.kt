package com.aeroplay.app.data.youtube

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class YouTubeApiPage<T>(val items: List<T>, val nextPageToken: String?)

class YouTubeApiException(
    val httpCode: Int,
    val reason: String,
    override val message: String
) : IOException(message)

@Singleton
class YouTubeDataApiClient @Inject constructor(
    private val httpClient: OkHttpClient
) {
    private var apiBaseUrl = DEFAULT_API_BASE_URL

    suspend fun popularVideos(token: String): List<com.aeroplay.app.core.model.VideoItem> {
        val root = request(token, "videos", mapOf("part" to "snippet,contentDetails,statistics",
            "chart" to "mostPopular", "regionCode" to "VN", "maxResults" to "30"))
        return videoItems(root, search = false)
    }

    suspend fun searchVideos(token: String, query: String, pageToken: String?): com.aeroplay.app.core.model.SearchResultBundle {
        val params = mutableMapOf("part" to "snippet", "type" to "video", "q" to query, "maxResults" to "25")
        if (!pageToken.isNullOrBlank()) params["pageToken"] = pageToken
        val root = request(token, "search", params)
        return com.aeroplay.app.core.model.SearchResultBundle(query = query, videos = videoItems(root, search = true),
            nextPageToken = root.optString("nextPageToken").takeIf { it.isNotBlank() },
            sourceType = com.aeroplay.app.core.model.ContentSourceType.EXPERIMENTAL_EXTRACTOR)
    }

    private fun videoItems(root: JSONObject, search: Boolean): List<com.aeroplay.app.core.model.VideoItem> {
        val items = root.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val id = if (search) item.optJSONObject("id")?.optString("videoId").orEmpty() else item.optString("id")
            if (!id.matches(Regex("[A-Za-z0-9_-]{11}"))) return@mapNotNull null
            val snippet = item.optJSONObject("snippet") ?: return@mapNotNull null
            com.aeroplay.app.core.model.VideoItem(id = id, url = "https://www.youtube.com/watch?v=$id",
                title = snippet.optString("title"), channelName = snippet.optString("channelTitle"),
                thumbnailUrl = thumbnail(snippet.optJSONObject("thumbnails")),
                durationSeconds = runCatching { java.time.Duration.parse(item.optJSONObject("contentDetails")?.optString("duration")).seconds }.getOrDefault(0L),
                viewCount = item.optJSONObject("statistics")?.optLong("viewCount", -1L) ?: -1L,
                sourceType = com.aeroplay.app.core.model.ContentSourceType.EXPERIMENTAL_EXTRACTOR,
                publishedAtEpochMs = runCatching { java.time.Instant.parse(snippet.optString("publishedAt")).toEpochMilli() }.getOrNull())
        }
    }

    internal constructor(httpClient: OkHttpClient, apiBaseUrl: String) : this(httpClient) {
        this.apiBaseUrl = apiBaseUrl.trimEnd('/') + "/"
    }

    suspend fun listMyChannels(token: String): List<YouTubeChannelOption> = getAllPages(
        token = token,
        path = "channels",
        params = mapOf("part" to "snippet", "mine" to "true", "maxResults" to "50")
    ) { item ->
        YouTubeChannelOption(
            channelId = item.optString("id"),
            title = item.optJSONObject("snippet")?.optString("title").orEmpty(),
            thumbnailUrl = thumbnail(item.optJSONObject("snippet")?.optJSONObject("thumbnails"))
        )
    }.filter { it.channelId.isNotBlank() }

    suspend fun listLikedVideos(token: String): List<YouTubeLikedVideo> = getAllPages(
        token = token,
        path = "videos",
        params = mapOf("part" to "snippet", "myRating" to "like", "maxResults" to "50")
    ) { item ->
        val snippet = item.optJSONObject("snippet") ?: JSONObject()
        YouTubeLikedVideo(
            videoId = item.optString("id"),
            title = snippet.optString("title"),
            channelTitle = snippet.optString("channelTitle"),
            thumbnailUrl = thumbnail(snippet.optJSONObject("thumbnails")),
            publishedAt = snippet.optString("publishedAt")
        )
    }.filter { it.videoId.isNotBlank() }

    suspend fun listMyPlaylists(token: String): List<YouTubePlaylist> = getAllPages(
        token = token,
        path = "playlists",
        params = mapOf("part" to "snippet,contentDetails,status", "mine" to "true", "maxResults" to "50")
    ) { item -> item.toPlaylist() }.filter { it.playlistId.isNotBlank() }

    suspend fun listPlaylistItems(token: String, playlistId: String): List<YouTubePlaylistItem> = getAllPages(
        token = token,
        path = "playlistItems",
        params = mapOf("part" to "snippet,contentDetails", "playlistId" to playlistId, "maxResults" to "50")
    ) { item -> item.toPlaylistItem() }.filter { it.playlistItemId.isNotBlank() }

    suspend fun listMySubscriptions(token: String): List<YouTubeSubscription> = getAllPages(
        token = token,
        path = "subscriptions",
        params = mapOf("part" to "snippet", "mine" to "true", "maxResults" to "50")
    ) { item ->
        val snippet = item.optJSONObject("snippet") ?: JSONObject()
        val resourceId = snippet.optJSONObject("resourceId") ?: JSONObject()
        YouTubeSubscription(
            subscriptionId = item.optString("id"),
            channelId = resourceId.optString("channelId"),
            channelTitle = snippet.optString("title"),
            thumbnailUrl = thumbnail(snippet.optJSONObject("thumbnails")),
            description = snippet.optString("description")
        )
    }.filter { it.subscriptionId.isNotBlank() && it.channelId.isNotBlank() }

    suspend fun getRatings(token: String, videoIds: List<String>): Map<String, String> {
        if (videoIds.isEmpty()) return emptyMap()
        return videoIds.distinct().chunked(50).flatMap { ids ->
            getAllPages(
                token = token,
                path = "videos/getRating",
                params = mapOf("id" to ids.joinToString(","))
            ) { item -> item.optString("videoId") to item.optString("rating") }
        }.filter { it.first.isNotBlank() }.toMap()
    }

    private suspend fun <T> getAllPages(
        token: String,
        path: String,
        params: Map<String, String>,
        parse: (JSONObject) -> T
    ): List<T> {
        val result = mutableListOf<T>()
        var pageToken: String? = null
        var pageCount = 0
        do {
            val query = params.toMutableMap()
            if (!pageToken.isNullOrBlank()) query["pageToken"] = pageToken!!
            val page = request(token, path, query).let { root ->
                val items = root.optJSONArray("items") ?: JSONArray()
                YouTubeApiPage(
                    items = (0 until items.length()).mapNotNull { index -> items.optJSONObject(index)?.let(parse) },
                    nextPageToken = root.optString("nextPageToken").takeIf { it.isNotBlank() }
                )
            }
            result += page.items
            pageToken = page.nextPageToken
            pageCount++
        } while (pageToken != null && pageCount < MAX_PAGES)
        if (pageToken != null) {
            throw YouTubeApiException(
                httpCode = 0,
                reason = "paginationLimit",
                message = "YouTube API pagination exceeded the 100-page safety limit. Narrow the request or refresh later."
            )
        }
        return result
    }

    private suspend fun request(token: String, path: String, params: Map<String, String>): JSONObject {
        require(token.isNotBlank()) { "Missing OAuth access token." }
        val url = "$apiBaseUrl$path".toHttpUrl().newBuilder()
        params.forEach { (name, value) -> url.addQueryParameter(name, value) }
        val request = Request.Builder()
            .url(url.build())
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .get()
            .build()
        var attempt = 0
        while (true) {
            try {
                val response = withTimeout(REQUEST_TIMEOUT_MS) { request.awaitResponse(httpClient) }
                response.use { return parseResponse(it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: YouTubeApiException) {
                if (error.httpCode in RETRYABLE_HTTP_CODES && attempt < MAX_RETRIES) {
                    delay(RETRY_DELAY_MS * (attempt + 1))
                    attempt++
                } else throw error
            } catch (error: IOException) {
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAY_MS * (attempt + 1))
                    attempt++
                } else throw error
            }
        }
    }

    private suspend fun Request.awaitResponse(client: OkHttpClient): Response = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(this)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        })
    }

    private suspend fun parseResponse(response: Response): JSONObject = withContext(Dispatchers.IO) {
        val body = response.body?.string().orEmpty()
        val json = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
        if (response.isSuccessful) return@withContext json
        val error = json.optJSONObject("error") ?: JSONObject()
        val details = error.optJSONArray("errors")?.optJSONObject(0) ?: JSONObject()
        val reason = details.optString("reason")
        val message = when {
            response.code == 401 -> "Google authorization expired. Sign in and authorize YouTube access again."
            response.code == 403 && reason.contains("quota", ignoreCase = true) -> "YouTube Data API quota has been exceeded. Try again later."
            response.code == 403 && reason == "insufficientPermissions" -> "Read-only YouTube permission is missing. Reauthorize the account."
            response.code == 404 -> "YouTube resource was not found or is no longer accessible."
            response.code == 429 -> "YouTube is rate limiting requests. Try again later."
            response.code >= 500 -> "YouTube API is temporarily unavailable."
            else -> error.optString("message").ifBlank { "YouTube API request failed (${response.code})." }
        }
        throw YouTubeApiException(response.code, reason, message)
    }

    private fun JSONObject.toPlaylist(): YouTubePlaylist {
        val snippet = optJSONObject("snippet") ?: JSONObject()
        val details = optJSONObject("contentDetails") ?: JSONObject()
        val status = optJSONObject("status") ?: JSONObject()
        return YouTubePlaylist(
            playlistId = optString("id"),
            title = snippet.optString("title"),
            description = snippet.optString("description"),
            thumbnailUrl = thumbnail(snippet.optJSONObject("thumbnails")),
            itemCount = details.optInt("itemCount", 0),
            privacyStatus = status.optString("privacyStatus"),
            etag = optString("etag")
        )
    }

    private fun JSONObject.toPlaylistItem(): YouTubePlaylistItem {
        val snippet = optJSONObject("snippet") ?: JSONObject()
        val content = optJSONObject("contentDetails") ?: JSONObject()
        val resourceId = snippet.optJSONObject("resourceId") ?: JSONObject()
        return YouTubePlaylistItem(
            playlistItemId = optString("id"),
            videoId = resourceId.optString("videoId").ifBlank { content.optString("videoId") },
            title = snippet.optString("title"),
            channelTitle = snippet.optString("videoOwnerChannelTitle").ifBlank { snippet.optString("channelTitle") },
            thumbnailUrl = thumbnail(snippet.optJSONObject("thumbnails")),
            position = snippet.optInt("position", 0)
        )
    }

    private fun thumbnail(thumbnails: JSONObject?): String = thumbnails?.let {
        listOf("maxres", "high", "medium", "default").firstNotNullOfOrNull { key ->
            it.optJSONObject(key)?.optString("url")?.takeIf(String::isNotBlank)
        }
    }.orEmpty()

    private companion object {
        const val DEFAULT_API_BASE_URL = "https://www.googleapis.com/youtube/v3/"
        const val MAX_PAGES = 100
        const val MAX_RETRIES = 2
        const val RETRY_DELAY_MS = 400L
        const val REQUEST_TIMEOUT_MS = 20_000L
        val RETRYABLE_HTTP_CODES = setOf(429, 500, 502, 503, 504)
    }
}
