package com.aeroplay.app.data.youtube

import com.aeroplay.app.BuildConfig
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.VideoItem

/**
 * Deliberately keeps official YouTube API account features separate from NewPipe/Media3.
 * Enabling account sync selects API-only mode and disables the unofficial YouTube extractor.
 */
object YouTubePolicyFeatureFlags {
    val accountSyncEnabled: Boolean
        get() = BuildConfig.YOUTUBE_ACCOUNT_SYNC_ENABLED

    val apiOnlyMode: Boolean
        get() = BuildConfig.YOUTUBE_API_ONLY_MODE

    val allowNewPipeYouTube: Boolean
        get() = !apiOnlyMode

    val allowYouTubePlaybackWithMedia3: Boolean
        get() = !apiOnlyMode && !accountSyncEnabled

    fun isYouTubeVideo(video: VideoItem): Boolean =
        video.sourceType == ContentSourceType.EXPERIMENTAL_EXTRACTOR || isYouTubeUrl(video.url)

    fun isYouTubeUrl(url: String): Boolean = runCatching {
        val host = android.net.Uri.parse(url).host.orEmpty().lowercase()
        host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be" ||
            host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
    }.getOrDefault(false)

    fun canPlayInBackground(video: VideoItem): Boolean =
        !isYouTubeVideo(video) || allowYouTubePlaybackWithMedia3
}
