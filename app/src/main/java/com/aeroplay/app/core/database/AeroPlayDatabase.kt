package com.aeroplay.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        HistoryEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistVideoEntity::class,
        PlaybackPositionEntity::class,
        SearchHistoryEntity::class,
        DeviceVideoEntity::class,
        RecommendationFeedbackEntity::class,
        BlockedRecommendationChannelEntity::class,
        RecommendationImpressionEntity::class,
        EditedMixEntity::class,
        ContentRestrictionEntity::class,
        YouTubeLikedVideoEntity::class,
        YouTubeRemotePlaylistEntity::class,
        YouTubeRemotePlaylistItemEntity::class,
        YouTubeSubscriptionEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AeroPlayDatabase : RoomDatabase() {
    abstract fun aeroPlayDao(): AeroPlayDao
}
