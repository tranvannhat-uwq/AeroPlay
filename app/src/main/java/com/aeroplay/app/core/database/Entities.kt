package com.aeroplay.app.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.VideoItem

@Entity(tableName = "watch_history")
data class HistoryEntity(
    @PrimaryKey val videoId: String,
    val url: String,
    val title: String,
    val channelName: String,
    val channelUrl: String,
    val channelAvatarUrl: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val viewCount: Long,
    val uploadedText: String,
    val sourceTypeName: String,
    val lastPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val watchedAtEpochMs: Long = System.currentTimeMillis()
) {
    fun toVideoItem(): VideoItem = VideoItem(
        id = videoId,
        url = url,
        title = title,
        channelName = channelName,
        channelUrl = channelUrl,
        channelAvatarUrl = channelAvatarUrl,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        viewCount = viewCount,
        uploadedText = uploadedText,
        sourceType = runCatching { ContentSourceType.valueOf(sourceTypeName) }
            .getOrDefault(ContentSourceType.DEMO_LOCAL)
    )
}

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val videoId: String,
    val url: String,
    val title: String,
    val channelName: String,
    val channelUrl: String,
    val channelAvatarUrl: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val viewCount: Long,
    val uploadedText: String,
    val sourceTypeName: String,
    val addedAtEpochMs: Long = System.currentTimeMillis()
) {
    fun toVideoItem(): VideoItem = VideoItem(
        id = videoId,
        url = url,
        title = title,
        channelName = channelName,
        channelUrl = channelUrl,
        channelAvatarUrl = channelAvatarUrl,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        viewCount = viewCount,
        uploadedText = uploadedText,
        sourceType = runCatching { ContentSourceType.valueOf(sourceTypeName) }
            .getOrDefault(ContentSourceType.DEMO_LOCAL)
    )
}

@Entity(tableName = "user_playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0L,
    val name: String,
    val description: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_videos",
    primaryKeys = ["playlistId", "videoId"],
    indices = [Index("playlistId"), Index("videoId")]
)
data class PlaylistVideoEntity(
    val playlistId: Long,
    val videoId: String,
    val url: String,
    val title: String,
    val channelName: String,
    val channelUrl: String,
    val channelAvatarUrl: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val viewCount: Long,
    val uploadedText: String,
    val sourceTypeName: String,
    val addedAtEpochMs: Long = System.currentTimeMillis()
) {
    fun toVideoItem(): VideoItem = VideoItem(
        id = videoId,
        url = url,
        title = title,
        channelName = channelName,
        channelUrl = channelUrl,
        channelAvatarUrl = channelAvatarUrl,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        viewCount = viewCount,
        uploadedText = uploadedText,
        sourceType = runCatching { ContentSourceType.valueOf(sourceTypeName) }
            .getOrDefault(ContentSourceType.DEMO_LOCAL)
    )
}

@Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @PrimaryKey val videoId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAtEpochMs: Long = System.currentTimeMillis()
)
