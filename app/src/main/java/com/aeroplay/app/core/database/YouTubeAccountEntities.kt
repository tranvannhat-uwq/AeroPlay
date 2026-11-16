package com.aeroplay.app.core.database

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "youtube_liked_videos", primaryKeys = ["accountId", "videoId"], indices = [Index("accountId")])
data class YouTubeLikedVideoEntity(
    val accountId: String,
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val publishedAt: String,
    val cachedAtEpochMs: Long
)

@Entity(tableName = "youtube_remote_playlists", primaryKeys = ["accountId", "playlistId"], indices = [Index("accountId")])
data class YouTubeRemotePlaylistEntity(
    val accountId: String,
    val playlistId: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val itemCount: Int,
    val privacyStatus: String,
    val etag: String,
    val cachedAtEpochMs: Long
)

@Entity(
    tableName = "youtube_remote_playlist_items",
    primaryKeys = ["accountId", "playlistId", "playlistItemId"],
    indices = [Index("accountId"), Index("playlistId"), Index("videoId")]
)
data class YouTubeRemotePlaylistItemEntity(
    val accountId: String,
    val playlistId: String,
    val playlistItemId: String,
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val position: Int,
    val cachedAtEpochMs: Long
)

@Entity(
    tableName = "youtube_subscriptions",
    primaryKeys = ["accountId", "subscriptionId"],
    indices = [Index("accountId"), Index("channelId")]
)
data class YouTubeSubscriptionEntity(
    val accountId: String,
    val subscriptionId: String,
    val channelId: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val description: String,
    val cachedAtEpochMs: Long
)
