package com.aeroplay.app

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aeroplay.app.core.model.ContentSourceType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the unchanged Compose UI, ViewModels, repositories, extractor and Media3. */
@RunWith(AndroidJUnit4::class)
class YouTubeUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun realYouTubeSearchSelectionAndRenderedPlayback() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("aeroplayLive") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = EntryPointAccessors.fromApplication(context, YouTubeTestEntryPoint::class.java)
        runBlocking { graph.settings().setActiveSourceType(ContentSourceType.EXPERIMENTAL_EXTRACTOR) }
        val activity = ActivityScenario.launch(MainActivity::class.java)
        try {
            compose.onAllNodesWithContentDescription("Tìm kiếm").onLast().performClick()
            compose.onNode(hasSetTextAction()).performTextInput("Blender Big Buck Bunny")
            compose.onNode(hasSetTextAction()).performImeAction()
            val videoResult = hasText("Big Buck Bunny", substring = true) and hasClickAction() and !hasSetTextAction()
            compose.waitUntil(30_000) {
                compose.onAllNodes(videoResult).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onAllNodes(videoResult).onFirst().performClick()
            compose.waitUntil(60_000) {
                val state = graph.playback().playbackState.value
                check(state.error == null) { "UI playback failed: ${state.error}" }
                state.isPlaying && state.currentPositionMs > 2_000
            }
            compose.onAllNodesWithText("Big Buck Bunny", substring = true).onFirst().assertIsDisplayed()
            val state = graph.playback().playbackState.value
            assertTrue(state.currentVideo?.sourceType == ContentSourceType.EXPERIMENTAL_EXTRACTOR)
            assertTrue(state.currentDetail?.streamVariants?.isNotEmpty() == true)
            Log.i("AeroPlayTest", "UI_PLAYING api=${Build.VERSION.SDK_INT} position=${state.currentPositionMs} video=${state.currentVideo?.id}")
            val output = File(context.getExternalFilesDir(null), "youtube-playing-api${Build.VERSION.SDK_INT}.png")
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        } finally {
            compose.runOnUiThread { graph.playback().dismissPlayer() }
            activity.close()
        }
    }
}
