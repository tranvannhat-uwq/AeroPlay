package com.aeroplay.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE watch_history ADD COLUMN playCount INTEGER NOT NULL DEFAULT 1")
            db.execSQL("""CREATE TABLE IF NOT EXISTS device_videos (
                videoId TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, fileName TEXT NOT NULL,
                thumbnailName TEXT NOT NULL, mimeType TEXT NOT NULL, durationMs INTEGER NOT NULL,
                width INTEGER NOT NULL, height INTEGER NOT NULL, sizeBytes INTEGER NOT NULL,
                importedAtEpochMs INTEGER NOT NULL)""")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE watch_history ADD COLUMN totalWatchedMs INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE watch_history ADD COLUMN completedPlayCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("""CREATE TABLE IF NOT EXISTS recommendation_feedback (
                videoId TEXT NOT NULL PRIMARY KEY, feedbackType TEXT NOT NULL,
                channelName TEXT NOT NULL, updatedAtEpochMs INTEGER NOT NULL)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS blocked_recommendation_channels (
                normalizedChannelName TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL,
                blockedAtEpochMs INTEGER NOT NULL)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS recommendation_impressions (
                videoId TEXT NOT NULL PRIMARY KEY, channelName TEXT NOT NULL,
                impressionCount INTEGER NOT NULL, clickCount INTEGER NOT NULL,
                lastImpressedAtEpochMs INTEGER NOT NULL, lastClickedAtEpochMs INTEGER NOT NULL)""")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS youtube_liked_videos (
                accountId TEXT NOT NULL, videoId TEXT NOT NULL, title TEXT NOT NULL,
                channelTitle TEXT NOT NULL, thumbnailUrl TEXT NOT NULL, publishedAt TEXT NOT NULL,
                cachedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(accountId, videoId))""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_youtube_liked_videos_accountId ON youtube_liked_videos(accountId)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS youtube_remote_playlists (
                accountId TEXT NOT NULL, playlistId TEXT NOT NULL, title TEXT NOT NULL,
                description TEXT NOT NULL, thumbnailUrl TEXT NOT NULL, itemCount INTEGER NOT NULL,
                privacyStatus TEXT NOT NULL, etag TEXT NOT NULL, cachedAtEpochMs INTEGER NOT NULL,
                PRIMARY KEY(accountId, playlistId))""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_youtube_remote_playlists_accountId ON youtube_remote_playlists(accountId)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS youtube_remote_playlist_items (
                accountId TEXT NOT NULL, playlistId TEXT NOT NULL, playlistItemId TEXT NOT NULL,
                videoId TEXT NOT NULL, title TEXT NOT NULL, channelTitle TEXT NOT NULL,
                thumbnailUrl TEXT NOT NULL, position INTEGER NOT NULL, cachedAtEpochMs INTEGER NOT NULL,
                PRIMARY KEY(accountId, playlistId, playlistItemId))""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_youtube_remote_playlist_items_accountId ON youtube_remote_playlist_items(accountId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_youtube_remote_playlist_items_playlistId ON youtube_remote_playlist_items(playlistId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_youtube_remote_playlist_items_videoId ON youtube_remote_playlist_items(videoId)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS youtube_subscriptions (
                accountId TEXT NOT NULL, subscriptionId TEXT NOT NULL, channelId TEXT NOT NULL,
                channelTitle TEXT NOT NULL, thumbnailUrl TEXT NOT NULL, description TEXT NOT NULL,
                cachedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(accountId, subscriptionId))""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_youtube_subscriptions_accountId ON youtube_subscriptions(accountId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_youtube_subscriptions_channelId ON youtube_subscriptions(channelId)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AeroPlayDatabase {
        return Room.databaseBuilder(
            context,
            AeroPlayDatabase::class.java,
            "aeroplay_local.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }

    @Provides
    @Singleton
    fun provideDao(database: AeroPlayDatabase): AeroPlayDao = database.aeroPlayDao()
}
