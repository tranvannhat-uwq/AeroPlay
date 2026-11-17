package com.aeroplay.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aeroplay.app.core.model.ContentSourceType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchLayoutUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun searchFieldHasTheSameHeightWithPlaceholderAndEnteredText() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onAllNodesWithContentDescription("Tìm kiếm").onLast().performClick()
            val input = compose.onNode(hasSetTextAction())
            input.performTextReplacement("")
            val emptyHeight = input.fetchSemanticsNode().boundsInRoot.height
            input.performTextReplacement("Sintel")
            assertEquals(emptyHeight, input.fetchSemanticsNode().boundsInRoot.height, 1f)
            input.performTextReplacement("")
        }
    }

    @Test fun compactResultsKeepOverflowActionsWithoutStartingPlayback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = EntryPointAccessors.fromApplication(context, YouTubeTestEntryPoint::class.java)
        val before = runBlocking { graph.settings().getSettingsSnapshot() }
        val video = runBlocking { graph.content().getHomeFeed(ContentSourceType.DEMO_LOCAL).getOrThrow().first { it.id == "demo_escapes" } }
        val wasFavorite = runBlocking { graph.history().observeIsFavorite(video.id).first() }
        try {
            runBlocking { graph.settings().setActiveSourceType(ContentSourceType.DEMO_LOCAL) }
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.onAllNodesWithContentDescription("Tìm kiếm").onLast().performClick()
                val input = compose.onNode(hasSetTextAction())
                input.performTextReplacement("open")
                input.performImeAction()
                compose.onNodeWithText("Video", substring = false).performClick()
                compose.waitUntil(8_000) { compose.onAllNodesWithTag("SearchVideoResult").fetchSemanticsNodes().size >= 3 }
                val row = compose.onAllNodesWithTag("SearchVideoResult").onFirst().fetchSemanticsNode().boundsInRoot
                assertTrue("A search result should be a compact horizontal row", row.height < row.width * 0.45f)
                input.performTextReplacement("escapes")
                input.performImeAction()
                compose.waitUntil(8_000) { compose.onAllNodesWithText(video.title).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithContentDescription("Tùy chọn video: ${video.title}").performClick()
                compose.onNodeWithText(if (wasFavorite) "Bỏ yêu thích" else "Yêu thích").performClick()
                compose.waitUntil(5_000) { runBlocking { graph.history().observeIsFavorite(video.id).first() } != wasFavorite }
                compose.onNodeWithTag("SearchVideoResult").assertIsDisplayed()
                compose.onNodeWithContentDescription("Tùy chọn video: ${video.title}").performClick()
                compose.onNodeWithText("Thêm vào danh sách phát").performClick()
                compose.onNodeWithText("Lưu vào Danh sách phát").assertIsDisplayed()
                compose.onNodeWithTag("FullVideoStage").assertDoesNotExist()
            }
        } finally {
            if (runBlocking { graph.history().observeIsFavorite(video.id).first() } != wasFavorite) runBlocking { graph.history().toggleFavorite(video) }
            runBlocking { graph.settings().setActiveSourceType(before.activeSourceType) }
        }
    }
}
