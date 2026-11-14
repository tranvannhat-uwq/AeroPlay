package com.aeroplay.app.data.repository

import com.aeroplay.app.core.database.AeroPlayDao
import com.aeroplay.app.core.database.FavoriteEntity
import com.aeroplay.app.core.database.HistoryEntity
import com.aeroplay.app.core.database.PlaybackPositionEntity
import com.aeroplay.app.core.database.PlaylistEntity
import com.aeroplay.app.core.database.PlaylistVideoEntity
import com.aeroplay.app.core.model.PlaybackPosition
import com.aeroplay.app.core.model.UserPlaylist
import com.aeroplay.app.core.model.VideoItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val dao: AeroPlayDao
) : HistoryRepository {

    override fun observeWatchHistory(): Flow<List<VideoItem>> {
        return dao.observeWatchHistory().map { list -> list.map { it.toVideoItem() } }
    }

    override fun observePlaybackPositions(): Flow<Map<String, PlaybackPosition>> {
        return dao.observeAllPlaybackPositions().map { entities ->
            entities.associate {
                it.videoId to PlaybackPosition(
                    videoId = it.videoId,
                    positionMs = it.positionMs,
                    durationMs = it.durationMs,
                    updatedAtEpochMs = it.updatedAtEpochMs
                )
            }
        }
    }

    override suspend fun getSavedPosition(videoId: String): PlaybackPosition? {
        val entity = dao.getPlaybackPosition(videoId) ?: return null
        return PlaybackPosition(
            videoId = entity.videoId,
            positionMs = entity.positionMs,
            durationMs = entity.durationMs,
            updatedAtEpochMs = entity.updatedAtEpochMs
        )
    }

    override suspend fun recordWatchAndPosition(video: VideoItem, positionMs: Long, durationMs: Long) {
        val now = System.currentTimeMillis()
        dao.recordHistory(
            HistoryEntity(
                videoId = video.id,
                url = video.url,
                title = video.title,
                channelName = video.channelName,
                channelUrl = video.channelUrl,
                channelAvatarUrl = video.channelAvatarUrl,
                thumbnailUrl = video.thumbnailUrl,
                durationSeconds = video.durationSeconds,
                viewCount = video.viewCount,
                uploadedText = video.uploadedText,
                sourceTypeName = video.sourceType.name,
                lastPositionMs = positionMs,
                totalDurationMs = durationMs,
                watchedAtEpochMs = now
            )
        )
        if (durationMs > 0L) {
            dao.upsertPlaybackPosition(
                PlaybackPositionEntity(
                    videoId = video.id,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    updatedAtEpochMs = now
                )
            )
        }
    }

    override suspend fun recordWatchedDuration(videoId: String, deltaMs: Long, durationMs: Long, positionMs: Long) {
        if (videoId.isNotBlank() && deltaMs in 1L..10_000L) {
            dao.addWatchedDuration(videoId, deltaMs, durationMs, positionMs)
        }
    }

    override suspend fun recordCompletion(videoId: String) {
        if (videoId.isNotBlank()) dao.addCompletedPlay(videoId)
    }

    override suspend fun updatePositionOnly(videoId: String, positionMs: Long, durationMs: Long) {
        if (videoId.isBlank() || durationMs <= 0L) return
        // If user reached the final 3 seconds of a video, reset resume position to 0 so next play restarts cleanly
        val effectivePosition = if (durationMs - positionMs < 3_000L && durationMs > 10_000L) 0L else positionMs
        dao.upsertPlaybackPosition(
            PlaybackPositionEntity(
                videoId = videoId,
                positionMs = effectivePosition,
                durationMs = durationMs,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    override suspend fun removeFromHistory(videoId: String) {
        dao.deleteHistoryItem(videoId)
    }

    override suspend fun clearAllHistory() {
        dao.clearWatchHistory()
        dao.clearPlaybackPositions()
    }

    override fun observeFavorites(): Flow<List<VideoItem>> {
        return dao.observeFavorites().map { list -> list.map { it.toVideoItem() } }
    }

    override fun observeIsFavorite(videoId: String): Flow<Boolean> {
        return dao.observeIsFavorite(videoId)
    }

    override suspend fun toggleFavorite(video: VideoItem): Boolean {
        val currentlyFav = dao.isFavorite(video.id)
        return if (currentlyFav) {
            dao.deleteFavorite(video.id)
            false
        } else {
            dao.insertFavorite(
                FavoriteEntity(
                    videoId = video.id,
                    url = video.url,
                    title = video.title,
                    channelName = video.channelName,
                    channelUrl = video.channelUrl,
                    channelAvatarUrl = video.channelAvatarUrl,
                    thumbnailUrl = video.thumbnailUrl,
                    durationSeconds = video.durationSeconds,
                    viewCount = video.viewCount,
                    uploadedText = video.uploadedText,
                    sourceTypeName = video.sourceType.name
                )
            )
            true
        }
    }
}

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val dao: AeroPlayDao
) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<UserPlaylist>> {
        return combine(
            dao.observePlaylists(),
            dao.observeAllPlaylistVideos()
        ) { playlists, videos ->
            val grouped = videos.groupBy { it.playlistId }
            playlists.map { pl ->
                val items = grouped[pl.playlistId].orEmpty()
                UserPlaylist(
                    id = pl.playlistId,
                    name = pl.name,
                    description = pl.description,
                    createdAtEpochMs = pl.createdAtEpochMs,
                    videoCount = items.size,
                    coverThumbnailUrl = items.firstOrNull()?.thumbnailUrl.orEmpty()
                )
            }
        }
    }

    override fun observePlaylistVideos(playlistId: Long): Flow<List<VideoItem>> {
        return dao.observePlaylistVideos(playlistId).map { list -> list.map { it.toVideoItem() } }
    }

    override suspend fun createPlaylist(name: String, description: String): Long {
        return dao.insertPlaylist(
            PlaylistEntity(
                name = name.trim(),
                description = description.trim()
            )
        )
    }

    override suspend fun renamePlaylist(playlistId: Long, newName: String, newDescription: String) {
        dao.updatePlaylist(playlistId, newName.trim(), newDescription.trim())
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        dao.deleteAllVideosFromPlaylist(playlistId)
        dao.deletePlaylist(playlistId)
    }

    override suspend fun addVideoToPlaylist(playlistId: Long, video: VideoItem) {
        dao.insertPlaylistVideo(
            PlaylistVideoEntity(
                playlistId = playlistId,
                videoId = video.id,
                url = video.url,
                title = video.title,
                channelName = video.channelName,
                channelUrl = video.channelUrl,
                channelAvatarUrl = video.channelAvatarUrl,
                thumbnailUrl = video.thumbnailUrl,
                durationSeconds = video.durationSeconds,
                viewCount = video.viewCount,
                uploadedText = video.uploadedText,
                sourceTypeName = video.sourceType.name
            )
        )
    }

    override suspend fun addToWatchLater(video: VideoItem) {
        val playlistId = dao.findPlaylistIdByName("Xem sau") ?: dao.insertPlaylist(
            PlaylistEntity(name = "Xem sau", description = "Video đã lưu để xem sau")
        )
        dao.insertPlaylistVideo(
            PlaylistVideoEntity(
                playlistId = playlistId,
                videoId = video.id,
                url = video.url,
                title = video.title,
                channelName = video.channelName,
                channelUrl = video.channelUrl,
                channelAvatarUrl = video.channelAvatarUrl,
                thumbnailUrl = video.thumbnailUrl,
                durationSeconds = video.durationSeconds,
                viewCount = video.viewCount,
                uploadedText = video.uploadedText,
                sourceTypeName = video.sourceType.name
            )
        )
    }

    override suspend fun removeVideoFromPlaylist(playlistId: Long, videoId: String) {
        dao.removeVideoFromPlaylist(playlistId, videoId)
    }
}
