package com.aeroplay.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AeroPlayDao {

    // --- Watch History ---
    @Query("SELECT * FROM watch_history ORDER BY watchedAtEpochMs DESC LIMIT 100")
    fun observeWatchHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entity: HistoryEntity)

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
