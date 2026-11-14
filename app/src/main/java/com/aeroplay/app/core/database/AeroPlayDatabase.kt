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
        RecommendationImpressionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AeroPlayDatabase : RoomDatabase() {
    abstract fun aeroPlayDao(): AeroPlayDao
}
