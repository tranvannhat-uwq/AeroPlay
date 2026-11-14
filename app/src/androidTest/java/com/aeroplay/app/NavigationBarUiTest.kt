package com.aeroplay.app

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationBarUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun selectingTabsKeepsEveryTabInPlace() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val labels = listOf("Trang chủ", "Tìm kiếm", "Thư viện", "Cài đặt")
            val tabs = labels.map { compose.onAllNodesWithContentDescription(it).onLast() }
            val originalBounds = tabs.map { it.fetchSemanticsNode().boundsInRoot }

            for (index in listOf(1, 3, 2, 0, 3, 1, 0)) {
                tabs[index].performClick().assertIsSelected()
                tabs.forEachIndexed { position, tab ->
                    assertEquals(labels[position], originalBounds[position], tab.fetchSemanticsNode().boundsInRoot)
                }
            }
        }
    }
}
