package com.aeroplay.app

import android.content.Context
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.ThemeMode
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloatingPlayerUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun swipeBetweenVideoViewsKeepsPlayingAndLetsUserBrowse() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = EntryPointAccessors.fromApplication(context, YouTubeTestEntryPoint::class.java)
        val settings = runBlocking { graph.settings().getSettingsSnapshot() }
        val clip = runBlocking {
            graph.settings().setActiveSourceType(ContentSourceType.DEMO_LOCAL)
            graph.settings().setThemeMode(ThemeMode.LIGHT)
            graph.content().getHomeFeed(ContentSourceType.DEMO_LOCAL).getOrThrow().first { it.id == "demo_escapes" }
        }
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            val playback = graph.playback()
            val full = compose.onNodeWithTag("FullVideoStage")
            val floating = compose.onNodeWithTag("FloatingVideoPlayer")
            val openVideo = hasClickAction() and hasAnyAncestor(hasTestTag("FloatingVideoPlayer"))
            try {
                activity.onActivity { playback.loadAndPlayVideo(clip) }
                compose.waitUntil(20_000) { playback.playbackState.value.currentPositionMs > 700 }
                val player = playback.playerFlow.value
                val before = playback.playbackState.value.currentPositionMs
                full.performTouchInput { swipeDown(startY = height * 0.17f, endY = height * 0.91f, durationMillis = 350) }
                compose.waitUntil(5_000) { playback.playbackState.value.isMiniPlayerVisible }
                floating.assertIsDisplayed()
                compose.waitUntil(5_000) { playback.playbackState.value.currentPositionMs > before + 500 }
                activity.onActivity { host ->
                    val texture = host.window.decorView.findTexture()
                    check(texture?.isAvailable == true) { "The popup has no video texture" }
                    val frame = requireNotNull(texture.bitmap)
                    try {
                        val pixels = IntArray(frame.width * frame.height)
                        frame.getPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)
                        check(pixels.any { it and 0x00FFFFFF != 0 }) { "The popup displays a black frame" }
                    } finally {
                        frame.recycle()
                    }
                }
                compose.onAllNodesWithContentDescription("Tìm kiếm").onLast().performClick()
                compose.onNode(hasSetTextAction()).assertIsDisplayed()
                floating.assertIsDisplayed()
                compose.onAllNodes(openVideo).onFirst().performClick()
                compose.waitUntil(5_000) { playback.playbackState.value.isFullPlayerOpen }
                full.assertIsDisplayed()
                assertSame(player, playback.playerFlow.value)
                assertEquals(clip.id, playback.playbackState.value.currentVideo?.id)
                full.performTouchInput { swipeDown(startY = height * 0.18f, endY = height * 0.9f, durationMillis = 350) }
                compose.waitUntil(5_000) { playback.playbackState.value.isMiniPlayerVisible }
                floating.performTouchInput { swipeUp(startY = height * 0.82f, endY = height * 0.12f, durationMillis = 320) }
                compose.waitUntil(5_000) { playback.playbackState.value.isFullPlayerOpen }
                full.performTouchInput { swipeDown(startY = height * 0.18f, endY = height * 0.9f, durationMillis = 350) }
                compose.waitUntil(5_000) { playback.playbackState.value.isMiniPlayerVisible }
                compose.onNodeWithContentDescription("Đóng trình phát").performClick()
                compose.waitUntil(5_000) { playback.playbackState.value.currentVideo == null }
            } finally {
                activity.onActivity { playback.dismissPlayer() }
                runBlocking {
                    graph.settings().setActiveSourceType(settings.activeSourceType)
                    graph.settings().setThemeMode(settings.themeMode)
                }
            }
        }
    }

    private fun View.findTexture(): TextureView? {
        if (this is TextureView) return this
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                getChildAt(index).findTexture()?.let { return it }
            }
        }
        return null
    }
}
