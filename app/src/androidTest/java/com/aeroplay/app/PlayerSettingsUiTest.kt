package com.aeroplay.app

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aeroplay.app.core.model.ContentSourceType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerSettingsUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun settingsPagesReturnToTheirParentAndRepeatAndSleepControlsWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = EntryPointAccessors.fromApplication(context, YouTubeTestEntryPoint::class.java)
        val video = runBlocking {
            graph.content().getHomeFeed(ContentSourceType.DEMO_LOCAL).getOrThrow()
                .first { it.id == "demo_sintel" }
        }
        val previousSettings = runBlocking { graph.settings().getSettingsSnapshot() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val evidenceDirectory = File(context.getExternalFilesDir(null), "demo-evidence").apply { mkdirs() }
        fun captureEvidence(name: String) {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            FileOutputStream(File(evidenceDirectory, name)).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            val playback = graph.playback()
            fun currentOrientation(): Int {
                var orientation = Configuration.ORIENTATION_UNDEFINED
                activity.onActivity { orientation = it.resources.configuration.orientation }
                return orientation
            }
            try {
                activity.onActivity { playback.loadAndPlayVideo(video) }
                compose.waitUntil(20_000) { playback.playbackState.value.isPlaying }

                compose.onNodeWithContentDescription("Cài đặt phát").performClick()
                compose.onNodeWithText("Cài đặt phát").assertIsDisplayed()
                captureEvidence("player-settings-main.png")
                compose.onNodeWithText("Chất lượng").assertIsDisplayed().performClick()
                compose.onNodeWithContentDescription("Quay lại mục cài đặt trước").performClick()
                compose.onNodeWithText("Cài đặt phát").assertIsDisplayed()
                compose.onNodeWithText("Tùy chọn khác").assertIsDisplayed()

                compose.onNodeWithText("Tùy chọn khác").performClick()
                compose.onNodeWithText("Cho video lặp lại").assertIsDisplayed()
                captureEvidence("player-settings-more.png")
                if (Build.VERSION.SDK_INT < 28) {
                    compose.onNodeWithText("Cần Android 9 trở lên.").assertIsDisplayed()
                }
                compose.onNodeWithText("Cho video lặp lại").performClick()
                compose.waitUntil(3_000) { playback.playbackState.value.isRepeatOneEnabled }

                compose.onNodeWithText("Hẹn giờ ngủ").performClick()
                compose.onNodeWithText("5 phút").performClick()
                compose.waitUntil(3_000) { playback.playbackState.value.sleepTimerDeadlineRealtimeMs != null }
                compose.onNodeWithText("Hủy hẹn giờ").performClick()
                compose.waitUntil(3_000) { playback.playbackState.value.sleepTimerDeadlineRealtimeMs == null }
                compose.onNodeWithContentDescription("Quay lại mục cài đặt trước").performClick()
                compose.onNodeWithText("Tùy chọn khác").assertIsDisplayed()

                compose.onNodeWithContentDescription("Đóng cài đặt phát").performClick()
                compose.onNodeWithContentDescription("Toàn màn hình / Xoay ngang").performClick()
                compose.waitUntil(10_000) { currentOrientation() == Configuration.ORIENTATION_LANDSCAPE }
                compose.onNodeWithContentDescription("Cài đặt phát").performClick()
                compose.onNodeWithText("Cài đặt phát").assertIsDisplayed()
                compose.waitForIdle()
                captureEvidence("player-settings-landscape.png")
                compose.onNodeWithText("Khóa màn hình").performClick()
                compose.onNodeWithContentDescription("Mở khóa màn hình").assertIsDisplayed()
                compose.onNodeWithContentDescription("Cài đặt phát").assertDoesNotExist()
                compose.onNodeWithContentDescription("Toàn màn hình / Xoay ngang").assertDoesNotExist()
                compose.onNodeWithTag("FullVideoStage").performTouchInput { click() }
                compose.onNodeWithContentDescription("Cài đặt phát").assertDoesNotExist()
                compose.onNodeWithContentDescription("Mở khóa màn hình").performClick()
                compose.onNodeWithContentDescription("Mở khóa màn hình").assertDoesNotExist()
                compose.onNodeWithContentDescription("Toàn màn hình / Xoay ngang").performClick()
                compose.waitUntil(10_000) { currentOrientation() == Configuration.ORIENTATION_PORTRAIT }

                compose.onNodeWithContentDescription("Đóng cài đặt phát").assertDoesNotExist()

                compose.onNodeWithContentDescription("Cài đặt phát").performClick()
                compose.onNodeWithText("Khóa màn hình").assertIsNotEnabled()
                compose.onNodeWithContentDescription("Mở khóa màn hình").assertDoesNotExist()

                compose.onNodeWithText("Trên đường đi").performClick()
                compose.waitUntil(10_000) { playback.playbackState.value.isAudioOnlyMode }
                compose.onNodeWithText("Trở về xem video").assertIsDisplayed().performClick()
                compose.waitUntil(10_000) { !playback.playbackState.value.isAudioOnlyMode }
            } finally {
                activity.onActivity {
                    playback.setRepeatOne(false)
                    playback.setSleepTimer(0)
                    playback.dismissPlayer()
                }
                runBlocking {
                    graph.settings().setAmbientLightingEnabled(previousSettings.ambientLightingEnabled)
                    graph.settings().setStableVolumeEnabled(previousSettings.stableVolumeEnabled)
                }
            }
        }
    }
}
