package com.aeroplay.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
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
}
