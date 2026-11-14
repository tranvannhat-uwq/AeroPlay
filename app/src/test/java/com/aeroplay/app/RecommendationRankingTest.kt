package com.aeroplay.app

import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.data.repository.RecommendationRanking
import com.aeroplay.app.data.repository.RecommendationSeed
import org.junit.Assert.*
import org.junit.Test

class RecommendationRankingTest {
    private fun video(id: String, title: String, channel: String) = VideoItem(
        id, "https://www.youtube.com/watch?v=$id", title, channel,
        thumbnailUrl = "", durationSeconds = 180, sourceType = ContentSourceType.EXPERIMENTAL_EXTRACTOR
    )

    @Test fun repeatedArtistOutranksOneOffViewingAndWatchedVideosAreExcluded() {
        val music = video("watched", "Sơn Tùng M-TP Chúng Ta", "Sơn Tùng M-TP")
        val unrelated = video("once", "Cooking noodles", "Kitchen")
        val candidates = listOf(
            video("food", "Cooking rice", "Kitchen"),
            video("song", "Sơn Tùng M-TP Lạc Trôi", "Sơn Tùng M-TP"),
            video("random", "Traffic in the city", "Travel"),
            music
        )
        val ranked = RecommendationRanking.rank(candidates,
            listOf(RecommendationSeed(music, 8, 1_000), RecommendationSeed(unrelated, 1, 1_000)), now = 1_000)
        assertEquals("song", ranked.first().id)
        assertFalse(ranked.any { it.id == "watched" || it.id == "random" })
    }

    @Test fun titlesMatchWithoutAccentsAndHistoryEmptyNeverProducesFakeSuggestions() {
        assertEquals(RecommendationRanking.terms("Sơn Tùng Official MV"), RecommendationRanking.terms("son tung"))
        val candidates = listOf(video("a", "Ballad piano", "Piano"))
        assertTrue(RecommendationRanking.rank(candidates, emptyList()).isEmpty())
    }

    @Test fun recommendationsContainUniqueVideosAndLimitRepeatedChannels() {
        val seed = video("seed", "Piano ballad", "Piano")
        val songs = (1..8).map { video("song$it", "Piano ballad $it", "Piano") }
        val result = RecommendationRanking.rank(songs + songs,
            listOf(RecommendationSeed(seed, 3, 1_000)), now = 1_000)
        assertEquals(2, result.size)
        assertEquals(result.size, result.distinctBy { it.id }.size)
    }

    @Test fun aFullyWatchedSmallCatalogStillOffersSimilarVideosToRevisit() {
        val first = video("first", "Blender animation", "Blender")
        val second = video("second", "Blender short film", "Blender")
        val seeds = listOf(RecommendationSeed(first, 2, 1_000), RecommendationSeed(second, 1, 900))
        val result = RecommendationRanking.rank(listOf(first, second), seeds, now = 1_000)
        assertEquals(listOf("second"), result.map { it.id })
    }
}
