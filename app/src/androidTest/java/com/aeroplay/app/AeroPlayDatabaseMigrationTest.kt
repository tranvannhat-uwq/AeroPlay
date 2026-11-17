package com.aeroplay.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aeroplay.app.core.database.AeroPlayDatabase
import com.aeroplay.app.core.database.DatabaseModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AeroPlayDatabaseMigrationTest {
    @Test
    fun versionTwoMigrationPreservesHistoryAndAddsYouTubeReadCache() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-2-to-3-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val old = SQLiteDatabase.openOrCreateDatabase(file, null)
        old.execSQL("""CREATE TABLE watch_history (
            videoId TEXT NOT NULL PRIMARY KEY, url TEXT NOT NULL, title TEXT NOT NULL,
            channelName TEXT NOT NULL, channelUrl TEXT NOT NULL, channelAvatarUrl TEXT NOT NULL,
            thumbnailUrl TEXT NOT NULL, durationSeconds INTEGER NOT NULL, viewCount INTEGER NOT NULL,
            uploadedText TEXT NOT NULL, sourceTypeName TEXT NOT NULL, lastPositionMs INTEGER NOT NULL,
            totalDurationMs INTEGER NOT NULL, watchedAtEpochMs INTEGER NOT NULL,
            playCount INTEGER NOT NULL DEFAULT 1)""")
        old.execSQL("""CREATE TABLE favorites (
            videoId TEXT NOT NULL PRIMARY KEY, url TEXT NOT NULL, title TEXT NOT NULL,
            channelName TEXT NOT NULL, channelUrl TEXT NOT NULL, channelAvatarUrl TEXT NOT NULL,
            thumbnailUrl TEXT NOT NULL, durationSeconds INTEGER NOT NULL, viewCount INTEGER NOT NULL,
            uploadedText TEXT NOT NULL, sourceTypeName TEXT NOT NULL, addedAtEpochMs INTEGER NOT NULL)""")
        old.execSQL("CREATE TABLE user_playlists (playlistId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL)")
        old.execSQL("""CREATE TABLE playlist_videos (
            playlistId INTEGER NOT NULL, videoId TEXT NOT NULL, url TEXT NOT NULL, title TEXT NOT NULL,
            channelName TEXT NOT NULL, channelUrl TEXT NOT NULL, channelAvatarUrl TEXT NOT NULL,
            thumbnailUrl TEXT NOT NULL, durationSeconds INTEGER NOT NULL, viewCount INTEGER NOT NULL,
            uploadedText TEXT NOT NULL, sourceTypeName TEXT NOT NULL, addedAtEpochMs INTEGER NOT NULL,
            PRIMARY KEY(playlistId, videoId))""")
        old.execSQL("CREATE INDEX index_playlist_videos_playlistId ON playlist_videos(playlistId)")
        old.execSQL("CREATE INDEX index_playlist_videos_videoId ON playlist_videos(videoId)")
        old.execSQL("CREATE TABLE playback_positions (videoId TEXT NOT NULL PRIMARY KEY, positionMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL)")
        old.execSQL("CREATE TABLE search_history (query TEXT NOT NULL PRIMARY KEY, searchedAtEpochMs INTEGER NOT NULL)")
        old.execSQL("""CREATE TABLE device_videos (
            videoId TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, fileName TEXT NOT NULL,
            thumbnailName TEXT NOT NULL, mimeType TEXT NOT NULL, durationMs INTEGER NOT NULL,
            width INTEGER NOT NULL, height INTEGER NOT NULL, sizeBytes INTEGER NOT NULL,
            importedAtEpochMs INTEGER NOT NULL)""")
        old.execSQL("""INSERT INTO watch_history VALUES (
            'preserved-id', 'https://example.invalid/watch', 'Preserved title', 'Preserved channel',
            '', '', '', 120, 99, '2 days ago', 'EXPERIMENTAL_EXTRACTOR', 42, 120000, 123456789, 7)""")
        old.version = 2
        old.execSQL("INSERT INTO favorites SELECT videoId,url,title,channelName,channelUrl,channelAvatarUrl,thumbnailUrl,durationSeconds,viewCount,uploadedText,sourceTypeName,123 FROM watch_history")
        old.execSQL("INSERT INTO user_playlists VALUES(42,'Preserved playlist','Description',123)")
        old.execSQL("INSERT INTO playlist_videos SELECT 42,videoId,url,title,channelName,channelUrl,channelAvatarUrl,thumbnailUrl,durationSeconds,viewCount,uploadedText,sourceTypeName,123 FROM watch_history")
        old.execSQL("INSERT INTO playback_positions VALUES('preserved-id',42000,120000,123)")
        old.close()

        val migrated = Room.databaseBuilder(context, AeroPlayDatabase::class.java, name)
            .addMigrations(DatabaseModule.MIGRATION_2_3, DatabaseModule.MIGRATION_3_4, DatabaseModule.MIGRATION_4_5)
            .build()
        try {
            val preserved = migrated.aeroPlayDao().observeWatchHistory().first().single()
            assertEquals("preserved-id", preserved.videoId)
            assertEquals("Preserved title", preserved.title)
            assertEquals(7, preserved.playCount)
            assertEquals(0L, preserved.totalWatchedMs)
            assertEquals(0, preserved.completedPlayCount)
            assertEquals("preserved-id", migrated.aeroPlayDao().observeFavorites().first().single().videoId)
            assertEquals("Preserved playlist", migrated.aeroPlayDao().observePlaylists().first().single().name)
            assertEquals("preserved-id", migrated.aeroPlayDao().observePlaylistVideos(42).first().single().videoId)
            assertEquals(42000L, migrated.aeroPlayDao().getPlaybackPosition("preserved-id")?.positionMs)
            assertEquals(0, migrated.aeroPlayDao().observeEditedMixes().first().size)
            assertEquals(0, migrated.aeroPlayDao().getContentRestrictions().size)
            val youtubeTables = migrated.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'youtube_%'"
            ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            assertEquals(
                setOf("youtube_liked_videos", "youtube_remote_playlists", "youtube_remote_playlist_items", "youtube_subscriptions"),
                youtubeTables
            )
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
