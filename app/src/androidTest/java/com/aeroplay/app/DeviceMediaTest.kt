package com.aeroplay.app

import android.content.Context
import android.os.Environment
import android.os.PowerManager
import android.util.Log
import androidx.core.content.FileProvider
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aeroplay.app.core.model.ContentSourceType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DeviceMediaTest {
    @Test fun importedVideoSurvivesSourceRemovalAndPlaysWithScreenOff() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = EntryPointAccessors.fromApplication(context, YouTubeTestEntryPoint::class.java)
        val media = graph.deviceMedia()
        val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates").apply { mkdirs() }
        val original = File(folder, "device-test.mp4")
        context.resources.openRawResource(R.raw.open_media_escapes).use { input ->
            original.outputStream().use { input.copyTo(it) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", original)
        val videoId = "device_" + com.aeroplay.app.updates.UpdateHash.sha256(original)
        val settings = graph.settings().getSettingsSnapshot()
        val activity = ActivityScenario.launch(MainActivity::class.java)
        var id: String? = null
        try {
            media.importOne(uri)
            val videos = media.videos.first()
            val video = videos.first { it.id == videoId }
            id = video.id
            val previousPlays = graph.database().aeroPlayDao().getHistory(video.id)?.playCount ?: 0
            assertEquals(ContentSourceType.DEVICE_LOCAL, video.sourceType)
            assertFalse(media.importOne(uri))
            assertTrue(original.delete())
            assertTrue(media.detail(video.id).isSuccess)
            media.rename(video.id, "For Bigger Escapes")
            assertEquals("For Bigger Escapes", media.detail(video.id).getOrThrow().title)
            val invalid = File(folder, "device-invalid.mp4").apply { writeText("not a video") }
            val invalidUri = FileProvider.getUriForFile(context, "${context.packageName}.updates", invalid)
            try {
                media.importOne(invalidUri)
                fail("Invalid media must be rejected")
            } catch (_: IllegalStateException) {
                // The file is not committed to the library.
            } catch (_: IllegalArgumentException) {
                // Some platform retrievers reject the container before reading tracks.
            } catch (_: RuntimeException) {
                // MediaMetadataRetriever reports unsupported input on older Android.
            } finally { invalid.delete() }
            assertEquals(videos.size, media.videos.first().size)
            graph.settings().setPipEnabled(false)
            graph.settings().setBackgroundPlaybackEnabled(true)
            graph.settings().setAutoAudioOnlyWhenBackground(false)
            val playback = withContext(Dispatchers.Main) { graph.playback() }
            withContext(Dispatchers.Main) {
                playback.loadAndPlayVideo(video)
            }
            val deadline = System.currentTimeMillis() + 25_000
            while (playback.playbackState.value.currentPositionMs < 800 && System.currentTimeMillis() < deadline) {
                check(playback.playbackState.value.error == null) { playback.playbackState.value.error.toString() }
                delay(150)
            }
            withContext(Dispatchers.Main) {
                val player = requireNotNull(playback.playerFlow.value)
                assertTrue(player.currentTracks.isTypeSelected(C.TRACK_TYPE_AUDIO))
                player.repeatMode = Player.REPEAT_MODE_ONE
                playback.seekTo(0)
            }
            val seekDeadline = System.currentTimeMillis() + 8_000
            while (System.currentTimeMillis() < seekDeadline &&
                (!playback.playbackState.value.isPlaying || playback.playbackState.value.isBuffering ||
                    playback.playbackState.value.currentPositionMs < 300)) {
                delay(150)
            }
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val plays = graph.database().aeroPlayDao().getHistory(video.id)?.playCount
            assertEquals(previousPlays + 1, plays)
            automation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            delay(800)
            val backgroundPosition = playback.playbackState.value.currentPositionMs
            val backgroundDeadline = System.currentTimeMillis() + 8_000
            while (System.currentTimeMillis() < backgroundDeadline &&
                playback.playbackState.value.currentPositionMs <= backgroundPosition + 700) delay(150)
            assertTrue(playback.playbackState.value.currentPositionMs > backgroundPosition + 700)
            assertTrue(playback.playbackState.value.isPlaying)
            automation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
            delay(800)
            assertFalse(power.isInteractive)
            val position = playback.playbackState.value.currentPositionMs
            delay(2_000)
            assertTrue("Playback stopped when screen turned off", playback.playbackState.value.isPlaying)
            assertTrue(playback.playbackState.value.currentPositionMs > position + 1_000)
            Log.i("AeroPlayTest", "DEVICE_MEDIA_SCREEN_OFF_PLAYING api=${android.os.Build.VERSION.SDK_INT}")
            automation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
            withContext(Dispatchers.Main) { playback.dismissPlayer() }
            media.remove(video.id)
            assertFalse(media.videos.first().any { it.id == video.id })
            assertTrue(media.detail(video.id).isFailure)
            assertFalse(File(context.filesDir, "device_media").listFiles().orEmpty().any { it.name.endsWith(".part") })
        } finally {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
            withContext(Dispatchers.Main) { graph.playback().dismissPlayer() }
            id?.let { media.remove(it) }
            graph.settings().setPipEnabled(settings.pipEnabled)
            graph.settings().setBackgroundPlaybackEnabled(settings.backgroundPlaybackEnabled)
            graph.settings().setAutoAudioOnlyWhenBackground(settings.autoAudioOnlyWhenBackground)
            activity.close()
            original.delete()
        }
    }
}
