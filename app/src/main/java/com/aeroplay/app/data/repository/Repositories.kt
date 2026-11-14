package com.aeroplay.app.data.repository

import com.aeroplay.app.core.model.AppSettings
import com.aeroplay.app.core.model.AudioTrack
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.PlaybackPosition
import com.aeroplay.app.core.model.PlaybackQueue
import com.aeroplay.app.core.model.SearchResultBundle
import com.aeroplay.app.core.model.StreamVariant
import com.aeroplay.app.core.model.ThemeMode
import com.aeroplay.app.core.model.UserPlaylist
import com.aeroplay.app.core.model.VideoDetail
import com.aeroplay.app.core.model.VideoItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class ResolvedStreamSelection(
    val primaryUrl: String,
    val secondaryAudioUrl: String? = null,
    val mimeType: String? = null,
    val selectedVariant: StreamVariant? = null,
    val selectedAudioTrack: AudioTrack? = null,
    val isAudioOnly: Boolean = false,
    val isMergedAdaptive: Boolean = false,
    val fallbackProgressiveUrl: String? = null
)

interface ContentRepository {
    suspend fun getHomeFeed(sourceOverride: ContentSourceType? = null): Result<List<VideoItem>>
}

interface SearchRepository {
    suspend fun search(
        query: String,
        pageToken: Any? = null,
        sourceOverride: ContentSourceType? = null
    ): Result<SearchResultBundle>

    fun observeSearchHistory(): Flow<List<String>>
    suspend fun saveSearchQuery(query: String)
    suspend fun deleteSearchQuery(query: String)
    suspend fun clearSearchHistory()
}

interface VideoMetadataRepository {
    suspend fun getVideoDetail(
        videoIdOrUrl: String,
        sourceHint: ContentSourceType? = null
    ): Result<VideoDetail>
}

interface StreamResolver {
    fun resolveStream(
        detail: VideoDetail,
        preferredVariantId: String? = null,
        preferredQualityLabel: String = "720p",
        isAudioOnly: Boolean = false,
        dataSaver: Boolean = false
    ): Result<ResolvedStreamSelection>

    fun resolveProgressiveFallback(detail: VideoDetail): ResolvedStreamSelection?
}

interface PlaybackRepository {
    val activeQueue: StateFlow<PlaybackQueue>
    fun updateQueue(queue: PlaybackQueue)
    fun selectQueueIndex(index: Int): VideoItem?
    fun nextInQueue(): VideoItem?
    fun previousInQueue(): VideoItem?
}

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<UserPlaylist>>
    fun observePlaylistVideos(playlistId: Long): Flow<List<VideoItem>>
    suspend fun createPlaylist(name: String, description: String = ""): Long
    suspend fun renamePlaylist(playlistId: Long, newName: String, newDescription: String = "")
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun addVideoToPlaylist(playlistId: Long, video: VideoItem)
    suspend fun removeVideoFromPlaylist(playlistId: Long, videoId: String)
}

interface HistoryRepository {
    fun observeWatchHistory(): Flow<List<VideoItem>>
    fun observePlaybackPositions(): Flow<Map<String, PlaybackPosition>>
    suspend fun getSavedPosition(videoId: String): PlaybackPosition?
    suspend fun recordWatchAndPosition(video: VideoItem, positionMs: Long, durationMs: Long)
    suspend fun updatePositionOnly(videoId: String, positionMs: Long, durationMs: Long)
    suspend fun removeFromHistory(videoId: String)
    suspend fun clearAllHistory()

    fun observeFavorites(): Flow<List<VideoItem>>
    fun observeIsFavorite(videoId: String): Flow<Boolean>
    suspend fun toggleFavorite(video: VideoItem): Boolean
}

interface SettingsRepository {
    val settingsFlow: Flow<AppSettings>
    suspend fun getSettingsSnapshot(): AppSettings
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setActiveSourceType(sourceType: ContentSourceType, warningAccepted: Boolean = true)
    suspend fun setDefaultQuality(qualityLabel: String)
    suspend fun setDataSaverEnabled(enabled: Boolean)
    suspend fun setDefaultPlaybackSpeed(speed: Float)
    suspend fun setAutoAudioOnlyWhenBackground(enabled: Boolean)
    suspend fun setPauseOnHeadphoneDisconnect(enabled: Boolean)
    suspend fun setPipEnabled(enabled: Boolean)
    suspend fun setBackgroundPlaybackEnabled(enabled: Boolean)
}
