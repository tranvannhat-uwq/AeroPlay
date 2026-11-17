package com.aeroplay.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AeroPlayDao {
    @Query("SELECT * FROM edited_mixes ORDER BY updatedAtEpochMs DESC")
    fun observeEditedMixes(): Flow<List<EditedMixEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEditedMix(mix: EditedMixEntity)

    @Query("DELETE FROM edited_mixes WHERE mixId = :id")
    suspend fun resetEditedMix(id: String)

    @Query("SELECT * FROM content_restrictions ORDER BY updatedAtEpochMs DESC")
    fun observeContentRestrictions(): Flow<List<ContentRestrictionEntity>>

    @Query("SELECT * FROM content_restrictions ORDER BY updatedAtEpochMs DESC")
    suspend fun getContentRestrictions(): List<ContentRestrictionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveContentRestriction(restriction: ContentRestrictionEntity)

    @Query("DELETE FROM content_restrictions WHERE restrictionId = :id")
    suspend fun removeContentRestriction(id: String)

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

    @Query("UPDATE watch_history SET totalWatchedMs = MIN(totalWatchedMs + :deltaMs, 31536000000), totalDurationMs = CASE WHEN :durationMs > 0 THEN :durationMs ELSE totalDurationMs END, lastPositionMs = :positionMs WHERE videoId = :videoId")
    suspend fun addWatchedDuration(videoId: String, deltaMs: Long, durationMs: Long, positionMs: Long)

    @Query("UPDATE watch_history SET completedPlayCount = MIN(completedPlayCount + 1, 100000) WHERE videoId = :videoId")
    suspend fun addCompletedPlay(videoId: String)

    @Query("SELECT * FROM recommendation_feedback")
    suspend fun getRecommendationFeedback(): List<RecommendationFeedbackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecommendationFeedback(entity: RecommendationFeedbackEntity)

    @Query("DELETE FROM recommendation_feedback WHERE videoId = :videoId")
    suspend fun deleteRecommendationFeedback(videoId: String)

    @Query("SELECT * FROM blocked_recommendation_channels")
    suspend fun getBlockedRecommendationChannels(): List<BlockedRecommendationChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockRecommendationChannel(entity: BlockedRecommendationChannelEntity)

    @Query("SELECT * FROM recommendation_impressions")
    suspend fun getRecommendationImpressions(): List<RecommendationImpressionEntity>

    @Query("SELECT * FROM recommendation_impressions WHERE videoId = :videoId LIMIT 1")
    suspend fun getRecommendationImpression(videoId: String): RecommendationImpressionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecommendationImpression(entity: RecommendationImpressionEntity)

    @Query("DELETE FROM recommendation_feedback")
    suspend fun clearRecommendationFeedback()

    @Query("DELETE FROM blocked_recommendation_channels")
    suspend fun clearBlockedRecommendationChannels()

    @Query("DELETE FROM recommendation_impressions")
    suspend fun clearRecommendationImpressions()

    @Transaction
    suspend fun clearPersonalizationSignals() {
        clearWatchHistory()
        clearPlaybackPositions()
        clearSearchHistory()
        clearRecommendationFeedback()
        clearBlockedRecommendationChannels()
        clearRecommendationImpressions()
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

    @Query("SELECT playlistId FROM user_playlists WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findPlaylistIdByName(name: String): Long?

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

    // --- YouTube API account cache (partitioned by Google account, never used as player input) ---
    @Query("SELECT * FROM youtube_liked_videos WHERE accountId = :accountId ORDER BY publishedAt DESC")
    fun observeYouTubeLikedVideos(accountId: String): Flow<List<YouTubeLikedVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertYouTubeLikedVideos(items: List<YouTubeLikedVideoEntity>)

    @Query("DELETE FROM youtube_liked_videos WHERE accountId = :accountId")
    suspend fun clearYouTubeLikedVideos(accountId: String)

    @Transaction
    suspend fun replaceYouTubeLikedVideos(accountId: String, items: List<YouTubeLikedVideoEntity>) {
        clearYouTubeLikedVideos(accountId)
        if (items.isNotEmpty()) insertYouTubeLikedVideos(items)
    }

    @Query("SELECT * FROM youtube_remote_playlists WHERE accountId = :accountId ORDER BY title COLLATE NOCASE")
    fun observeYouTubePlaylists(accountId: String): Flow<List<YouTubeRemotePlaylistEntity>>

    @Query("SELECT * FROM youtube_remote_playlist_items WHERE accountId = :accountId ORDER BY playlistId, position")
    fun observeYouTubePlaylistItems(accountId: String): Flow<List<YouTubeRemotePlaylistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertYouTubePlaylists(items: List<YouTubeRemotePlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertYouTubePlaylistItems(items: List<YouTubeRemotePlaylistItemEntity>)

    @Query("DELETE FROM youtube_remote_playlist_items WHERE accountId = :accountId AND playlistId = :playlistId")
    suspend fun clearYouTubePlaylistItemsForPlaylist(accountId: String, playlistId: String)

    @Transaction
    suspend fun replaceYouTubePlaylistItems(
        accountId: String,
        playlistId: String,
        items: List<YouTubeRemotePlaylistItemEntity>
    ) {
        clearYouTubePlaylistItemsForPlaylist(accountId, playlistId)
        if (items.isNotEmpty()) insertYouTubePlaylistItems(items)
    }

    @Query("DELETE FROM youtube_remote_playlists WHERE accountId = :accountId")
    suspend fun clearYouTubePlaylists(accountId: String)

    @Query("DELETE FROM youtube_remote_playlist_items WHERE accountId = :accountId")
    suspend fun clearAllYouTubePlaylistItems(accountId: String)

    @Transaction
    suspend fun replaceYouTubePlaylists(accountId: String, playlists: List<YouTubeRemotePlaylistEntity>) {
        clearYouTubePlaylists(accountId)
        if (playlists.isNotEmpty()) insertYouTubePlaylists(playlists)
    }

    @Query("SELECT * FROM youtube_subscriptions WHERE accountId = :accountId ORDER BY channelTitle COLLATE NOCASE")
    fun observeYouTubeSubscriptions(accountId: String): Flow<List<YouTubeSubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertYouTubeSubscriptions(items: List<YouTubeSubscriptionEntity>)

    @Query("DELETE FROM youtube_subscriptions WHERE accountId = :accountId")
    suspend fun clearYouTubeSubscriptions(accountId: String)

    @Transaction
    suspend fun replaceYouTubeSubscriptions(accountId: String, items: List<YouTubeSubscriptionEntity>) {
        clearYouTubeSubscriptions(accountId)
        if (items.isNotEmpty()) insertYouTubeSubscriptions(items)
    }

    @Query("DELETE FROM youtube_liked_videos WHERE cachedAtEpochMs < :cutoffEpochMs")
    suspend fun pruneExpiredYouTubeLikes(cutoffEpochMs: Long)

    @Query("DELETE FROM youtube_remote_playlists WHERE cachedAtEpochMs < :cutoffEpochMs")
    suspend fun pruneExpiredYouTubePlaylists(cutoffEpochMs: Long)

    @Query("DELETE FROM youtube_remote_playlist_items WHERE cachedAtEpochMs < :cutoffEpochMs")
    suspend fun pruneExpiredYouTubePlaylistItems(cutoffEpochMs: Long)

    @Query("DELETE FROM youtube_subscriptions WHERE cachedAtEpochMs < :cutoffEpochMs")
    suspend fun pruneExpiredYouTubeSubscriptions(cutoffEpochMs: Long)

    @Query("DELETE FROM youtube_liked_videos WHERE substr(accountId, 1, length(:accountIdentity)) = :accountIdentity AND substr(accountId, length(:accountIdentity) + 1, 1) = '|'")
    suspend fun deleteYouTubeLikesForAccountIdentity(accountIdentity: String)

    @Query("DELETE FROM youtube_remote_playlists WHERE substr(accountId, 1, length(:accountIdentity)) = :accountIdentity AND substr(accountId, length(:accountIdentity) + 1, 1) = '|'")
    suspend fun deleteYouTubePlaylistsForAccountIdentity(accountIdentity: String)

    @Query("DELETE FROM youtube_remote_playlist_items WHERE substr(accountId, 1, length(:accountIdentity)) = :accountIdentity AND substr(accountId, length(:accountIdentity) + 1, 1) = '|'")
    suspend fun deleteYouTubePlaylistItemsForAccountIdentity(accountIdentity: String)

    @Query("DELETE FROM youtube_subscriptions WHERE substr(accountId, 1, length(:accountIdentity)) = :accountIdentity AND substr(accountId, length(:accountIdentity) + 1, 1) = '|'")
    suspend fun deleteYouTubeSubscriptionsForAccountIdentity(accountIdentity: String)

    @Transaction
    suspend fun deleteAllYouTubeDataForAccountIdentity(accountIdentity: String) {
        deleteYouTubeLikesForAccountIdentity(accountIdentity)
        deleteYouTubePlaylistItemsForAccountIdentity(accountIdentity)
        deleteYouTubePlaylistsForAccountIdentity(accountIdentity)
        deleteYouTubeSubscriptionsForAccountIdentity(accountIdentity)
    }

    @Transaction
    suspend fun pruneExpiredYouTubeData(cutoffEpochMs: Long) {
        pruneExpiredYouTubeLikes(cutoffEpochMs)
        pruneExpiredYouTubePlaylistItems(cutoffEpochMs)
        pruneExpiredYouTubePlaylists(cutoffEpochMs)
        pruneExpiredYouTubeSubscriptions(cutoffEpochMs)
    }
}
