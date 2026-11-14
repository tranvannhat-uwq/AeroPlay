package com.aeroplay.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.data.source.DemoLocalMediaSource
import com.aeroplay.app.playback.controller.PlaybackControllerManager
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OpenMediaDeviceTest {
    @Test fun everyPackagedArtworkDecodesWithoutNetwork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (video in DemoLocalMediaSource().getRecommendedVideos()) {
            assertTrue(video.thumbnailUrl.startsWith("android.resource:"))
            context.contentResolver.openInputStream(Uri.parse(video.thumbnailUrl)).use { stream ->
                val bitmap = requireNotNull(BitmapFactory.decodeStream(stream)) { "Invalid artwork: ${video.id}" }
                assertTrue(bitmap.width > 0 && bitmap.height > 0)
                bitmap.recycle()
            }
        }
    }

    @Test fun allFiveOpenMediaVideosActuallyPlayAndDedicatedAudioWorks() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("aeroplayLive") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = EntryPointAccessors.fromApplication(context, YouTubeTestEntryPoint::class.java)
        graph.settings().setActiveSourceType(ContentSourceType.DEMO_LOCAL)
        val videos = graph.content().getHomeFeed(ContentSourceType.DEMO_LOCAL).getOrThrow()
        assertEquals(5, videos.size)
        val activity = ActivityScenario.launch(MainActivity::class.java)
        val playback = withContext(Dispatchers.Main) { graph.playback() }
        try {
            for (video in videos) {
                withContext(Dispatchers.Main) {
                    playback.dismissPlayer()
                    playback.loadAndPlayVideo(video, queueItems = listOf(video))
                }
                awaitProgress(playback)
                withContext(Dispatchers.Main) {
                    val tracks = requireNotNull(playback.playerFlow.value).currentTracks
                    assertTrue("No selected video for ${video.id}", tracks.isTypeSelected(C.TRACK_TYPE_VIDEO))
                    assertTrue("No selected audio for ${video.id}", tracks.isTypeSelected(C.TRACK_TYPE_AUDIO))
                }
                Log.i("AeroPlayTest", "OPEN_MEDIA_PLAYING id=${video.id} position=${playback.playbackState.value.currentPositionMs}")
                if (video.id == "demo_bbb") {
                    val variants = requireNotNull(playback.playbackState.value.currentDetail).streamVariants
                    for (variant in variants.drop(1)) {
                        withContext(Dispatchers.Main) { playback.selectQualityVariant(variant) }
                        awaitProgress(playback)
                        assertEquals(variant.id, playback.playbackState.value.selectedStreamVariant?.id)
                        Log.i("AeroPlayTest", "OPEN_MEDIA_QUALITY_PLAYING height=${variant.height}")
                    }
                }
                saveScreenshot(context, video.id)
            }
            val sintel = videos.first { it.id == "demo_sintel" }
            withContext(Dispatchers.Main) {
                playback.dismissPlayer()
                playback.loadAndPlayVideo(sintel)
            }
            awaitProgress(playback)
            withContext(Dispatchers.Main) { playback.setAudioOnlyMode(true) }
            awaitProgress(playback)
            assertTrue(playback.playbackState.value.isAudioOnlyMode)
            withContext(Dispatchers.Main) {
                assertFalse(requireNotNull(playback.playerFlow.value).currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO))
            }
            Log.i("AeroPlayTest", "OPEN_MEDIA_AUDIO_ONLY_PLAYING")
        } finally {
            withContext(Dispatchers.Main) { playback.dismissPlayer() }
            activity.close()
        }
    }

    private suspend fun awaitProgress(playback: PlaybackControllerManager) {
        val deadline = System.currentTimeMillis() + 60_000
        var firstPosition: Long? = null
        while (System.currentTimeMillis() < deadline) {
            val state = playback.playbackState.value
            check(state.error == null) { "Open media failure: ${state.error}" }
            if (state.isPlaying) {
                if (firstPosition == null) firstPosition = state.currentPositionMs
                if (state.currentPositionMs > firstPosition + 1_500) return
            }
            delay(200)
        }
        error("Open media never advanced")
    }

    private fun saveScreenshot(context: Context, id: String) {
        val file = File(context.getExternalFilesDir(null), "${id}-playing-api${android.os.Build.VERSION.SDK_INT}.png")
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
