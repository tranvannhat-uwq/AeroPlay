package com.aeroplay.app

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.SearchContentType
import com.aeroplay.app.feature.search.SearchQueryAnalyzer
import com.aeroplay.app.feature.search.VideoSearchRanker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in live retrieval probe; results are real NewPipe candidates, not a quality ground-truth label. */
@RunWith(AndroidJUnit4::class)
class SearchQualityLiveTest {
    private lateinit var graph: YouTubeTestEntryPoint

    @Before fun requireExplicitLiveOptIn() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("aeroplayLive") == "true")
        graph = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext<Context>(), YouTubeTestEntryPoint::class.java
        )
    }

    @Test fun allTenRequiredQueriesReturnRankableRealCandidates() = runBlocking {
        val queries = listOf(
            "nhạc trẻ", "nhạc remix 2026", "hài việt nam", "review điện thoại", "game mobile",
            "tin tức hôm nay", "sơn chống thấm", "Sơn Tùng M-TP", "Taylor Swift", "MrBeast"
        )
        for (query in queries) {
            val bundle = graph.search().search(
                query,
                sourceOverride = ContentSourceType.EXPERIMENTAL_EXTRACTOR,
                contentType = SearchContentType.VIDEO
            ).getOrThrow()
            val ranked = VideoSearchRanker.rank(bundle.videos, SearchQueryAnalyzer.analyze(query))
            assertFalse("NewPipe returned no video candidates for: $query", bundle.videos.isEmpty())
            assertFalse("Local relevance threshold removed every result for: $query", ranked.isEmpty())
            assertTrue("Duplicate IDs remained for: $query", ranked.map { it.video.id }.distinct().size == ranked.size)
            Log.i("AeroPlaySearchProbe", "$query -> ${ranked.take(5).joinToString { "${it.video.title} [${it.score.total}]" }}")
            delay(350L)
        }
    }
}
