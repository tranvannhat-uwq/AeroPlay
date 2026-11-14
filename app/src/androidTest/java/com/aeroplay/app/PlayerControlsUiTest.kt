package com.aeroplay.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
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
class PlayerControlsUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun controlsHideAfterFourSecondsAndStayVisibleWhilePausedOrUsingDialogs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = EntryPointAccessors.fromApplication(context, YouTubeTestEntryPoint::class.java)
        val video = runBlocking {
            graph.content().getHomeFeed(ContentSourceType.DEMO_LOCAL).getOrThrow().first { it.id == "demo_escapes" }
        }
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            val playback = graph.playback()
            val pauseButton = compose.onNodeWithContentDescription("Tạm dừng")
            val stage = compose.onNodeWithTag("FullVideoStage")
            try {
                activity.onActivity { playback.loadAndPlayVideo(video) }
                compose.waitUntil(20_000) { playback.playbackState.value.isPlaying }
                activity.onActivity { playback.playerFlow.value?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE }
                pauseButton.assertIsDisplayed()
                compose.mainClock.advanceTimeBy(3_500)
                pauseButton.assertIsDisplayed()
                compose.mainClock.advanceTimeBy(800)
                pauseButton.assertDoesNotExist()
                stage.performClick()
                pauseButton.assertIsDisplayed()
                pauseButton.performClick()
                compose.waitUntil(3_000) { !playback.playbackState.value.isPlaying }
                compose.mainClock.advanceTimeBy(4_500)
                compose.onNodeWithContentDescription("Phát").assertIsDisplayed().performClick()
                compose.waitUntil(5_000) { playback.playbackState.value.isPlaying }
                // A button action restarts the inactivity timer even for accessibility clicks.
                compose.mainClock.advanceTimeBy(2_000)
                compose.onNodeWithContentDescription("Lùi 10 giây").performClick()
                compose.waitUntil(5_000) { playback.playbackState.value.isPlaying }
                compose.mainClock.advanceTimeBy(2_500)
                pauseButton.assertIsDisplayed()
                compose.mainClock.advanceTimeBy(2_000)
                pauseButton.assertDoesNotExist()
                stage.performClick()
                compose.onAllNodesWithText(requireNotNull(playback.playbackState.value.selectedStreamVariant).resolutionLabel)
                    .onFirst().performClick()
                compose.mainClock.advanceTimeBy(4_500)
                compose.onNodeWithText("Đóng").performClick()
                pauseButton.assertIsDisplayed()
            } finally {
                activity.onActivity { playback.dismissPlayer() }
            }
        }
    }
}
