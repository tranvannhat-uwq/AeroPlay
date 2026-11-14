package com.aeroplay.app

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.playback.controller.PlaybackControllerManager
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Run explicitly with adb shell am instrument -w -e aeroplayLive true ... */
@RunWith(AndroidJUnit4::class)
class YouTubeDeviceTest {
    private lateinit var graph: YouTubeTestEntryPoint

    @Before fun setup() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("aeroplayLive") == "true")
        graph = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext<Context>(), YouTubeTestEntryPoint::class.java
        )
    }

    @Test fun homeThroughProductionRepository() = runBlocking<Unit> {
        val videos = graph.content().getHomeFeed(ContentSourceType.EXPERIMENTAL_EXTRACTOR).getOrThrow()
        assertTrue("Home must contain real YouTube videos", videos.isNotEmpty())
        Log.i("AeroPlayTest", "HOME videos=${videos.size}")
    }

    @Test fun searchAndMetadataThroughProductionRepositories() = runBlocking<Unit> {
        val bundle = graph.search().search("Blender Big Buck Bunny", sourceOverride = ContentSourceType.EXPERIMENTAL_EXTRACTOR).getOrThrow()
        assertTrue("Search must contain real YouTube videos", bundle.videos.isNotEmpty())
        val next = graph.search().search(bundle.query, requireNotNull(bundle.nextPageToken), ContentSourceType.EXPERIMENTAL_EXTRACTOR).getOrThrow()
        assertTrue(next.videos.isNotEmpty())
        val detail = graph.metadata().getVideoDetail("https://www.youtube.com/watch?v=aqz-KE-bpKQ").getOrThrow()
        assertTrue(detail.streamVariants.isNotEmpty())
        Log.i("AeroPlayTest", "SEARCH videos=${bundle.videos.size} next=${next.videos.size} DETAIL video=${detail.streamVariants.size} audio=${detail.audioTracks.size}")
    }

    @Test fun selectedYouTubeVideoActuallyPlaysThroughMediaSession() = runBlocking {
        val bundle = graph.search().search("Blender Big Buck Bunny", sourceOverride = ContentSourceType.EXPERIMENTAL_EXTRACTOR).getOrThrow()
        val video = bundle.videos.first { it.id == "aqz-KE-bpKQ" }
        val activity = ActivityScenario.launch(MainActivity::class.java)
        val playback = withContext(Dispatchers.Main) { graph.playback() }
        try {
            withContext(Dispatchers.Main) { playback.loadAndPlayVideo(video) }
            val deadline = System.currentTimeMillis() + 60_000
            while (System.currentTimeMillis() < deadline) {
                val state = playback.playbackState.value
                check(state.error == null) { "Playback failed: ${state.error}" }
                if (state.isPlaying && state.currentPositionMs > 2_000) {
                    Log.i("AeroPlayTest", "PLAYING position=${state.currentPositionMs} buffered=${state.bufferedPositionMs}")
                    return@runBlocking
                }
                delay(250)
            }
            val state = playback.playbackState.value
            error("Player never advanced: playing=${state.isPlaying} loading=${state.isLoadingMetadata} buffering=${state.isBuffering} position=${state.currentPositionMs} error=${state.error}")
        } finally {
            withContext(Dispatchers.Main) { playback.dismissPlayer() }
            activity.close()
        }
    }

    @Test fun adaptiveQualityAndAudioOnlyBothAdvance() = runBlocking<Unit> {
        val bundle = graph.search().search("Blender Big Buck Bunny", sourceOverride = ContentSourceType.EXPERIMENTAL_EXTRACTOR).getOrThrow()
        val video = bundle.videos.first { it.id == "aqz-KE-bpKQ" }
        val activity = ActivityScenario.launch(MainActivity::class.java)
        val playback = withContext(Dispatchers.Main) { graph.playback() }
        try {
            withContext(Dispatchers.Main) { playback.loadAndPlayVideo(video) }
            awaitProgress(playback, 2_000)
            val variant = requireNotNull(playback.playbackState.value.currentDetail).streamVariants.first {
                it.isVideoOnly && it.height == 720 && it.formatMimeType == "video/mp4"
            }
            val beforeQuality = playback.playbackState.value.currentPositionMs
            withContext(Dispatchers.Main) { playback.selectQualityVariant(variant) }
            awaitProgress(playback, beforeQuality + 1_500)
            assertTrue("Adaptive playback must retain the requested video variant", playback.playbackState.value.selectedStreamVariant?.id == variant.id)
            Log.i("AeroPlayTest", "ADAPTIVE_PLAYING api=${android.os.Build.VERSION.SDK_INT} height=${variant.height}")
            val beforeAudio = playback.playbackState.value.currentPositionMs
            withContext(Dispatchers.Main) { playback.setAudioOnlyMode(true) }
            awaitProgress(playback, beforeAudio + 1_500)
            assertTrue(playback.playbackState.value.isAudioOnlyMode)
            assertTrue(playback.playbackState.value.selectedAudioTrack != null)
            Log.i("AeroPlayTest", "AUDIO_ONLY_PLAYING api=${android.os.Build.VERSION.SDK_INT}")
        } finally {
            withContext(Dispatchers.Main) { playback.dismissPlayer() }
            activity.close()
        }
    }

    private suspend fun awaitProgress(playback: PlaybackControllerManager, position: Long) {
        val deadline = System.currentTimeMillis() + 45_000
        while (System.currentTimeMillis() < deadline) {
            val state = playback.playbackState.value
            check(state.error == null) { "Playback failure: ${state.error}" }
            if (state.isPlaying && state.currentPositionMs > position) return
            delay(250)
        }
        error("Playback did not advance past $position ms")
    }
}
