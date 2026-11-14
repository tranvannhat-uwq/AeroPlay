package com.aeroplay.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.app.Activity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aeroplay.app.core.model.ContentSourceType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerFullscreenUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun landscapeHidesSystemBarsAndRestoresThemWhenPlayerIsMinimized() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = EntryPointAccessors.fromApplication(context, YouTubeTestEntryPoint::class.java)
        val clip = runBlocking {
            graph.content().getHomeFeed(ContentSourceType.DEMO_LOCAL).getOrThrow().first { it.id == "demo_escapes" }
        }
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            val playback = graph.playback()
            try {
                activity.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                compose.waitUntil(8_000) {
                    compose.waitForIdle()
                    var portrait = false
                    activity.onActivity { portrait = it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
                    portrait
                }
                activity.onActivity { playback.loadAndPlayVideo(clip) }
                compose.waitUntil(20_000) { playback.playbackState.value.isPlaying }
                activity.onActivity { playback.playerFlow.value?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE }
                compose.waitForIdle()
                compose.onNodeWithContentDescription("Toàn màn hình / Xoay ngang").performClick()
                compose.waitForIdle()
                compose.waitUntil(8_000) {
                    compose.waitForIdle()
                    var hidden = false
                    activity.onActivity {
                        hidden = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                            systemBarsHidden(it)
                    }
                    hidden
                }
                activity.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.waitForIdle()
                compose.waitUntil(8_000) {
                    compose.waitForIdle()
                    var portrait = false
                    activity.onActivity {
                        portrait = it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
                            statusBarVisible(it)
                    }
                    portrait
                }
                // Rotating the device directly also enters immersive playback.
                check(playback.playbackState.value.isFullPlayerOpen) { "Back from landscape unexpectedly minimized the player" }
                activity.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                compose.waitForIdle()
                compose.waitUntil(8_000) {
                    compose.waitForIdle()
                    var hidden = false
                    activity.onActivity {
                        hidden = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                            systemBarsHidden(it)
                    }
                    hidden
                }
                compose.onNodeWithTag("FullVideoStage").assertTopPositionInRootIsEqualTo(0.dp)
                activity.onActivity { playback.minimizeToMiniPlayer() }
                compose.waitForIdle()
                compose.waitUntil(8_000) {
                    compose.waitForIdle()
                    var visible = false
                    activity.onActivity {
                        visible = statusBarVisible(it)
                    }
                    visible
                }
            } catch (failure: Throwable) {
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                    java.io.File(context.cacheDir, "fullscreen-failure.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
                throw failure
            } finally {
                activity.onActivity {
                    playback.dismissPlayer()
                    it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun systemBarsHidden(activity: Activity): Boolean {
        // Insets visibility is only an approximation before API 30, particularly after rotation.
        // Check the platform fullscreen flags there, alongside the stage bounds and visual QA.
        if (Build.VERSION.SDK_INT < 30) {
            val flags = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            return activity.window.decorView.systemUiVisibility and flags == flags
        }
        val insets = ViewCompat.getRootWindowInsets(activity.window.decorView) ?: return false
        return !insets.isVisible(WindowInsetsCompat.Type.statusBars()) &&
            !insets.isVisible(WindowInsetsCompat.Type.navigationBars())
    }

    @Suppress("DEPRECATION")
    private fun statusBarVisible(activity: Activity): Boolean =
        if (Build.VERSION.SDK_INT < 30) {
            activity.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0
        } else {
            ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.statusBars()) == true
        }
}
