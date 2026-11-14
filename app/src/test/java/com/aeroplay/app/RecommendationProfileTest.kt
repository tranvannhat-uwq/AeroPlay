package com.aeroplay.app

import com.aeroplay.app.core.database.BlockedRecommendationChannelEntity
import com.aeroplay.app.core.database.FavoriteEntity
import com.aeroplay.app.core.database.HistoryEntity
import com.aeroplay.app.core.database.RecommendationFeedbackEntity
import com.aeroplay.app.core.database.RecommendationImpressionEntity
import com.aeroplay.app.core.database.SearchHistoryEntity
import com.aeroplay.app.core.database.PlaylistVideoEntity
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.data.repository.RecommendationRanking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationProfileTest {
    private fun history(id: String, title: String, channel: String, watchedAt: Long) = HistoryEntity(
        videoId = id, url = "https://youtube.com/watch?v=$id", title = title, channelName = channel,
        channelUrl = "", channelAvatarUrl = "", thumbnailUrl = "", durationSeconds = 100,
        viewCount = 500, uploadedText = "", sourceTypeName = ContentSourceType.EXPERIMENTAL_EXTRACTOR.name,
        totalDurationMs = 100_000, totalWatchedMs = 90_000, watchedAtEpochMs = watchedAt
    )

    private fun candidate(id: String, title: String, channel: String) = VideoItem(
        id, "https://youtube.com/watch?v=$id", title, channel,
        thumbnailUrl = "", durationSeconds = 100, sourceType = ContentSourceType.EXPERIMENTAL_EXTRACTOR
    )

    @Test fun profileCombinesRecentHistorySearchesFavoritesAndPlaylistsOnDevice() {
        val now = 1_000_000L
        val favorite = FavoriteEntity(
            videoId = "fav", url = "url", title = "Camera Review", channelName = "Tech Lab",
            channelUrl = "", channelAvatarUrl = "", thumbnailUrl = "", durationSeconds = 100,
            viewCount = 0, uploadedText = "", sourceTypeName = ContentSourceType.EXPERIMENTAL_EXTRACTOR.name
        )
        val playlist = PlaylistVideoEntity(
            playlistId = 1, videoId = "saved", url = "url", title = "Mirrorless Camera Guide",
            channelName = "Photo Class", channelUrl = "", channelAvatarUrl = "", thumbnailUrl = "",
            durationSeconds = 100, viewCount = -1, uploadedText = "",
            sourceTypeName = ContentSourceType.EXPERIMENTAL_EXTRACTOR.name
        )
        val profile = RecommendationRanking.buildProfile(
            history = listOf(history("recent", "Camera Lens Review", "Tech Lab", now)),
            favorites = listOf(favorite),
            playlists = listOf(playlist),
            searches = listOf(SearchHistoryEntity("camera comparison", now)),
            feedback = emptyList(), blockedChannels = emptyList(), impressions = emptyList(), now = now
        )
        assertTrue(profile.preferredTopics.containsKey("camera"))
        assertTrue(profile.preferredChannels.containsKey("tech lab"))
        assertTrue(profile.preferredSearchTerms.contains("camera comparison"))
        assertEquals(2, profile.positiveInteractions.size)
        assertTrue(profile.recentWatchHistory.isNotEmpty())
    }

    @Test fun notInterestedAndBlockedChannelsOnlyRemoveHomeRecommendationCandidates() {
        val unwanted = candidate("bad", "Camera setup", "Blocked Tech")
        val wanted = candidate("good", "Camera lens tutorial", "Photo Class")
        val profile = RecommendationRanking.buildProfile(
            history = listOf(history("seed", "Camera lens review", "Photo Class", 1_000_000L)),
            favorites = emptyList(), playlists = emptyList(), searches = emptyList(),
            feedback = listOf(RecommendationFeedbackEntity("EXPERIMENTAL_EXTRACTOR:bad", RecommendationRanking.NOT_INTERESTED, unwanted.channelName)),
            blockedChannels = listOf(BlockedRecommendationChannelEntity("blocked tech", "Blocked Tech")),
            impressions = listOf(RecommendationImpressionEntity("EXPERIMENTAL_EXTRACTOR:bad", unwanted.channelName, impressionCount = 5)),
            now = 1_000_000L
        )
        val recs = RecommendationRanking.rankForProfile(listOf(unwanted, wanted), profile, now = 1_000_000L)
        assertEquals(listOf("good"), recs.map { it.id })
        assertFalse(profile.blockedChannels.isEmpty())
    }
}
