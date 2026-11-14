package com.aeroplay.app.data.source

import com.aeroplay.app.core.model.ChannelItem
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.PlaylistItem
import com.aeroplay.app.core.model.SearchResultBundle
import com.aeroplay.app.core.model.SourceError
import com.aeroplay.app.core.model.VideoDetail
import com.aeroplay.app.core.model.VideoItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Valid, open-license media source (Creative Commons / Open Movies) used to verify
 * the Media3 engine, quality selector, audio-only mode, background playback, and PiP
 * without depending on YouTube availability (Requirement IV.1).
 */
@Singleton
class DemoLocalMediaSource @Inject constructor() {

    private val demoVideos: List<VideoDetail> = OpenMediaCatalog.videos

    private fun VideoDetail.toSummaryItem(): VideoItem = VideoItem(
        id = id,
        url = url,
        title = title,
        channelName = channelName,
        channelUrl = channelUrl,
        channelAvatarUrl = channelAvatarUrl,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        viewCount = viewCount,
        uploadedText = uploadDateText,
        isLive = isLive,
        sourceType = ContentSourceType.DEMO_LOCAL
    )

    fun getRecommendedVideos(): List<VideoItem> {
        return demoVideos.map { it.toSummaryItem() }
    }

    fun search(query: String): SearchResultBundle {
        val trimmed = query.trim()
        val matchedVideos = if (trimmed.isBlank()) {
            demoVideos.map { it.toSummaryItem() }
        } else {
            demoVideos.filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                    it.channelName.contains(trimmed, ignoreCase = true) ||
                    it.description.contains(trimmed, ignoreCase = true)
            }.map { it.toSummaryItem() }
        }

        val channels = listOf(
            ChannelItem(
                id = "channel_blender",
                url = "https://studio.blender.org",
                name = "Blender Studio Open Movies",
                avatarUrl = OpenMediaCatalog.artwork("bbb"),
                description = "Phim hoạt hình & VFX mã nguồn mở chuẩn Creative Commons.",
                streamCount = 4L,
                sourceType = ContentSourceType.DEMO_LOCAL
            )
        ).filter { trimmed.isBlank() || it.name.contains(trimmed, ignoreCase = true) }

        val playlists = listOf(
            PlaylistItem(
                id = "playlist_open_movies",
                url = "https://studio.blender.org/films",
                title = "Tuyển tập Phim Mã Nguồn Mở Blender",
                uploaderName = "Blender Studio",
                thumbnailUrl = OpenMediaCatalog.artwork("sintel"),
                streamCount = 4L,
                sourceType = ContentSourceType.DEMO_LOCAL
            )
        ).filter { trimmed.isBlank() || it.title.contains(trimmed, ignoreCase = true) }

        return SearchResultBundle(
            query = query,
            videos = matchedVideos,
            channels = channels,
            playlists = playlists,
            nextPageToken = null,
            sourceType = ContentSourceType.DEMO_LOCAL
        )
    }

    fun getVideoDetail(idOrUrl: String): Result<VideoDetail> {
        if (idOrUrl.contains("unsupported_demo")) {
            return Result.failure(
                SourceErrorException(
                    SourceError.NoStreamAvailable("Video kiểm thử này được cấu hình không có stream khả dụng.")
                )
            )
        }
        // Old favorites/history retain the former public URLs. Keep them attached to
        // the same movie rather than redirecting every unknown URL to Big Buck Bunny.
        val legacyId = if (idOrUrl.startsWith("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/") ||
            idOrUrl.startsWith("http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/")) {
            when (idOrUrl.substringAfterLast('/').substringBefore('?')) {
                "BigBuckBunny.mp4" -> "demo_bbb"
                "Sintel.mp4" -> "demo_sintel"
                "TearsOfSteel.mp4" -> "demo_tos"
                "ElephantsDream.mp4" -> "demo_elephants"
                "ForBiggerEscapes.mp4" -> "demo_escapes"
                else -> null
            }
        } else null
        val found = demoVideos.find { it.id == idOrUrl || it.url == idOrUrl || it.id == legacyId }
            ?: return Result.failure(SourceErrorException(SourceError.NoStreamAvailable("Không tìm thấy video này trong nguồn Chuẩn Mở.")))
        val related = demoVideos.filter { it.id != found.id }.map { it.toSummaryItem() }
        return Result.success(found.copy(relatedVideos = related))
    }
}
