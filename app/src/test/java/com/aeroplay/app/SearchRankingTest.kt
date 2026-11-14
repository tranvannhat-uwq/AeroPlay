package com.aeroplay.app

import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.feature.search.QueryLanguage
import com.aeroplay.app.feature.search.SearchIntent
import com.aeroplay.app.feature.search.SearchQueryAnalyzer
import com.aeroplay.app.feature.search.SearchSortOrder
import com.aeroplay.app.feature.search.SearchTimeRange
import com.aeroplay.app.feature.search.VideoSearchRanker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankingTest {
    private fun video(id: String, title: String, channel: String = "", views: Long = -1L, uploaded: String = "") = VideoItem(
        id = id,
        url = "https://www.youtube.com/watch?v=$id",
        title = title,
        channelName = channel,
        thumbnailUrl = "",
        durationSeconds = 120,
        viewCount = views,
        uploadedText = uploaded,
        sourceType = ContentSourceType.EXPERIMENTAL_EXTRACTOR
    )

    @Test fun understandsVietnameseRecentPopularAndExactInternationalQueries() {
        assertEquals(SearchIntent.MUSIC, SearchQueryAnalyzer.analyze("nhac remix").intent)
        assertEquals(SearchIntent.RECENT, SearchQueryAnalyzer.analyze("nhạc remix 2026").intent)
        assertEquals(SearchIntent.POPULAR, SearchQueryAnalyzer.analyze("nhạc phổ biến").intent)
        val international = SearchQueryAnalyzer.analyze("Taylor Swift")
        assertEquals(QueryLanguage.UNKNOWN, international.language)
        assertEquals(SearchIntent.EXACT_ENTITY, international.intent)
        assertTrue(SearchQueryAnalyzer.analyze("nhac tre").isVietnameseContext)
    }

    @Test fun requiredVietnameseAndInternationalQueriesKeepTheirIntentAndText() {
        val cases = listOf(
            "nhạc trẻ" to SearchIntent.MUSIC,
            "nhạc remix 2026" to SearchIntent.RECENT,
            "hài việt nam" to SearchIntent.GENERAL,
            "review điện thoại" to SearchIntent.GENERAL,
            "game mobile" to SearchIntent.GENERAL,
            "tin tức hôm nay" to SearchIntent.RECENT,
            "sơn chống thấm" to SearchIntent.GENERAL,
            "Sơn Tùng M-TP" to SearchIntent.EXACT_ENTITY,
            "Taylor Swift" to SearchIntent.EXACT_ENTITY,
            "MrBeast" to SearchIntent.EXACT_ENTITY
        )
        cases.forEach { (query, expected) ->
            val result = SearchQueryAnalyzer.analyze(query)
            assertEquals(query, expected, result.intent)
            assertEquals(query, query, result.originalQuery)
        }
        assertTrue(SearchQueryAnalyzer.analyze("hài việt nam").isVietnameseContext)
        assertTrue(SearchQueryAnalyzer.analyze("review điện thoại").isVietnameseContext)
    }

    @Test fun relevantVietnameseResultBeatsPopularOffTopicVideoAndUnaccentedMatches() {
        val analysis = SearchQueryAnalyzer.analyze("sơn chống thấm")
        val results = VideoSearchRanker.rank(
            listOf(
                video("wrong", "Review điện thoại Samsung", views = 9_000_000),
                video("right", "Sơn chống thấm ngoài trời", views = 20_000)
            ), analysis
        )
        assertEquals("right", results.first().video.id)
        assertFalse(results.any { it.video.id == "wrong" })

        val unaccented = VideoSearchRanker.rank(
            listOf(video("accentless", "Son chong tham tuong nha")), analysis
        )
        assertEquals("accentless", unaccented.first().video.id)
    }

    @Test fun internationalEntityDoesNotReceiveVietnameseDownranking() {
        val analysis = SearchQueryAnalyzer.analyze("Taylor Swift")
        val result = VideoSearchRanker.rank(
            listOf(video("swift", "Taylor Swift - Anti-Hero", "Taylor Swift")), analysis
        ).single()
        assertEquals(100, result.score.queryRelevance)
        assertEquals(50, result.score.languageRegion)
    }

    @Test fun popularityAndRelativeAgeFiltersOnlyUseReturnedMetadata() {
        val analysis = SearchQueryAnalyzer.analyze("deep house")
        val candidates = listOf(
            video("old", "Deep House Mix", views = 100_000_000, uploaded = "2 years ago"),
            video("new", "Deep House Mix 2026", views = 10_000, uploaded = "2 days ago"),
            video("unknown", "Deep House Session")
        )
        val popular = VideoSearchRanker.rank(candidates, analysis, SearchSortOrder.POPULAR)
        assertEquals("old", popular.first().video.id)
        val recent = VideoSearchRanker.rank(candidates, analysis, SearchSortOrder.RECENT)
        assertEquals("new", recent.first().video.id)
        val filtered = VideoSearchRanker.rank(candidates, analysis, timeRange = SearchTimeRange.WEEK)
        assertEquals(listOf("new"), filtered.map { it.video.id })
    }

    @Test fun deduplicatesBySourceAndVideoIdentity() {
        val duplicate = video("same", "Deep House Mix")
        val ranked = VideoSearchRanker.rank(listOf(duplicate, duplicate), SearchQueryAnalyzer.analyze("deep house"))
        assertEquals(1, ranked.size)
    }

    @Test fun exactExtractorTimestampUsesVietnamLocalCalendarForTodayFilter() {
        val now = java.time.Instant.parse("2026-11-14T12:00:00Z").toEpochMilli()
        val today = video("today", "Deep House Mix", uploaded = "2 weeks ago").copy(
            publishedAtEpochMs = java.time.Instant.parse("2026-11-14T02:00:00Z").toEpochMilli()
        )
        val yesterday = video("yesterday", "Deep House Mix").copy(
            publishedAtEpochMs = java.time.Instant.parse("2026-11-13T15:30:00Z").toEpochMilli()
        )
        val ranked = VideoSearchRanker.rank(
            listOf(today, yesterday), SearchQueryAnalyzer.analyze("deep house"),
            timeRange = SearchTimeRange.TODAY, nowEpochMs = now
        )
        assertEquals(listOf("today"), ranked.map { it.video.id })
    }
}
