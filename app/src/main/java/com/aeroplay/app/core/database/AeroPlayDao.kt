package com.aeroplay.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AeroPlayDao {

    // --- Watch History ---
    @Query("SELECT * FROM watch_history ORDER BY watchedAtEpochMs DESC LIMIT 100")
    fun observeWatchHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entity: HistoryEntity)

    @Query("SELECT * FROM watch_history WHERE videoId = :videoId LIMIT 1")
    suspend fun getHistory(videoId: String): HistoryEntity?

    @Transaction
    suspend fun recordHistory(entity: HistoryEntity) {
        val previousCount = getHistory(entity.videoId)?.playCount ?: 0
        upsertHistory(entity.copy(playCount = (previousCount + 1).coerceAtMost(100_000)))
    }

    @Query("SELECT * FROM device_videos ORDER BY importedAtEpochMs DESC")
    fun observeDeviceVideos(): Flow<List<DeviceVideoEntity>>

    @Query("SELECT * FROM device_videos WHERE videoId = :videoId LIMIT 1")
    suspend fun getDeviceVideo(videoId: String): DeviceVideoEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDeviceVideo(video: DeviceVideoEntity): Long

    @Query("DELETE FROM device_videos WHERE videoId = :videoId")
    suspend fun deleteDeviceVideo(videoId: String)

    @Query("UPDATE device_videos SET title = :title WHERE videoId = :videoId")
    suspend fun updateDeviceTitle(videoId: String, title: String)

    @Query("UPDATE watch_history SET title = :title WHERE videoId = :videoId")
    suspend fun updateHistoryTitle(videoId: String, title: String)

    @Query("UPDATE favorites SET title = :title WHERE videoId = :videoId")
    suspend fun updateFavoriteTitle(videoId: String, title: String)

    @Query("UPDATE playlist_videos SET title = :title WHERE videoId = :videoId")
    suspend fun updatePlaylistVideoTitle(videoId: String, title: String)

    @Transaction
    suspend fun renameDeviceVideo(videoId: String, title: String) {
        updateDeviceTitle(videoId, title)
        updateHistoryTitle(videoId, title)
        updateFavoriteTitle(videoId, title)
        updatePlaylistVideoTitle(videoId, title)
    }

    @Query("DELETE FROM playlist_videos WHERE videoId = :videoId")
    suspend fun deleteVideoFromAllPlaylists(videoId: String)

    @Query("DELETE FROM playback_positions WHERE videoId = :videoId")
    suspend fun deletePosition(videoId: String)

    @Transaction
    suspend fun removeDeviceReferences(videoId: String) {
        deleteDeviceVideo(videoId)
        deleteHistoryItem(videoId)
        deleteFavorite(videoId)
        deleteVideoFromAllPlaylists(videoId)
        deletePosition(videoId)
    }

    @Query("DELETE FROM watch_history WHERE videoId = :videoId")
    suspend fun deleteHistoryItem(videoId: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearWatchHistory()

    // --- Playback Positions ---
    @Query("SELECT * FROM playback_positions WHERE videoId = :videoId LIMIT 1")
    suspend fun getPlaybackPosition(videoId: String): PlaybackPositionEntity?

    @Query("SELECT * FROM playback_positions")
    fun observeAllPlaybackPositions(): Flow<List<PlaybackPositionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaybackPosition(entity: PlaybackPositionEntity)

    @Query("DELETE FROM playback_positions")
    suspend fun clearPlaybackPositions()

    // --- Favorites ---
    @Query("SELECT * FROM favorites ORDER BY addedAtEpochMs DESC")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE videoId = :videoId)")
    fun observeIsFavorite(videoId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE videoId = :videoId)")
    suspend fun isFavorite(videoId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE videoId = :videoId")
    suspend fun deleteFavorite(videoId: String)

    // --- Playlists ---
    @Query("SELECT * FROM user_playlists ORDER BY createdAtEpochMs DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(entity: PlaylistEntity): Long

    @Query("UPDATE user_playlists SET name = :newName, description = :newDescription WHERE playlistId = :playlistId")
    suspend fun updatePlaylist(playlistId: Long, newName: String, newDescription: String)

    @Query("DELETE FROM user_playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId")
    suspend fun deleteAllVideosFromPlaylist(playlistId: Long)

    @Query("SELECT * FROM playlist_videos WHERE playlistId = :playlistId ORDER BY addedAtEpochMs DESC")
    fun observePlaylistVideos(playlistId: Long): Flow<List<PlaylistVideoEntity>>

    @Query("SELECT * FROM playlist_videos")
    fun observeAllPlaylistVideos(): Flow<List<PlaylistVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistVideo(entity: PlaylistVideoEntity)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun removeVideoFromPlaylist(playlistId: Long, videoId: String)

    // --- Search History ---
    @Query("SELECT * FROM search_history ORDER BY searchedAtEpochMs DESC LIMIT 20")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchQuery(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}
