package com.aeroplay.app

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aeroplay.app.core.database.HistoryEntity
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.VideoItem
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalizedRecommendationsTest {
    private val graph get() = EntryPointAccessors.fromApplication(
        ApplicationProvider.getApplicationContext<Context>(), YouTubeTestEntryPoint::class.java
    )

    @Test fun openCatalogSuggestionsUseHistoryWithoutRepeatingWatchedVideo() = runBlocking<Unit> {
        val video = graph.content().getHomeFeed(ContentSourceType.DEMO_LOCAL).getOrThrow().first { it.id == "demo_sintel" }
        val result = graph.recommendations().recommendations(listOf(history(video)), emptyList(), emptyList(), emptyList(), ContentSourceType.DEMO_LOCAL)
        assertTrue(result.message, result.videos.isNotEmpty())
        assertFalse(result.videos.any { it.id == video.id })
        assertTrue(result.videos.all { it.sourceType == ContentSourceType.DEMO_LOCAL })
        assertTrue(graph.recommendations().recommendations(emptyList(), emptyList(), emptyList(), emptyList(), ContentSourceType.DEMO_LOCAL).videos.isEmpty())
    }

    @Test fun realYouTubeSuggestionsComeFromTheExtractor() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("aeroplayLive") == "true")
        val url = "https://www.youtube.com/watch?v=aqz-KE-bpKQ"
        val detail = graph.metadata().getVideoDetail(url, ContentSourceType.EXPERIMENTAL_EXTRACTOR).getOrThrow()
        val seed = VideoItem(detail.id, url, detail.title, detail.channelName,
            thumbnailUrl = detail.thumbnailUrl, durationSeconds = detail.durationSeconds,
            sourceType = ContentSourceType.EXPERIMENTAL_EXTRACTOR)
        val result = graph.recommendations().recommendations(listOf(history(seed)), emptyList(), emptyList(), emptyList(), ContentSourceType.EXPERIMENTAL_EXTRACTOR)
        assertTrue(result.message, result.videos.isNotEmpty())
        assertTrue(result.videos.all { it.sourceType == ContentSourceType.EXPERIMENTAL_EXTRACTOR && it.id != seed.id })
        Log.i("AeroPlayTest", "PERSONALIZED_YOUTUBE_REAL_RESULTS count=${result.videos.size}")
    }

    private fun history(video: VideoItem) = HistoryEntity(
        videoId = video.id, url = video.url, title = video.title, channelName = video.channelName,
        channelUrl = video.channelUrl, channelAvatarUrl = video.channelAvatarUrl,
        thumbnailUrl = video.thumbnailUrl, durationSeconds = video.durationSeconds,
        viewCount = video.viewCount, uploadedText = video.uploadedText, sourceTypeName = video.sourceType.name,
        playCount = 4
    )
}
