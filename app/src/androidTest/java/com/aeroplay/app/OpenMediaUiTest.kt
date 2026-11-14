package com.aeroplay.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.ThemeMode
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OpenMediaUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun switchToOpenMediaShowsHomeAndSearchResults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = EntryPointAccessors.fromApplication(context, YouTubeTestEntryPoint::class.java)
        val previousTheme = runBlocking { graph.settings().getSettingsSnapshot().themeMode }
        runBlocking {
            graph.settings().setThemeMode(ThemeMode.LIGHT)
            graph.settings().setActiveSourceType(ContentSourceType.EXPERIMENTAL_EXTRACTOR)
        }
        val activity = ActivityScenario.launch(MainActivity::class.java)
        val title = "Big Buck Bunny – Blender Open Movie (CC-BY 3.0)"
        try {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Extractor Thử nghiệm").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Extractor Thử nghiệm").performClick()
            compose.onNodeWithText(ContentSourceType.DEMO_LOCAL.displayName).performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Nguồn Chuẩn Mở").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasText(title))
            compose.onAllNodesWithText(title).onLast().performScrollTo().assertIsDisplayed()
            screenshot(context, "open-media-home")
            Log.i("AeroPlayTest", "OPEN_MEDIA_HOME_VISIBLE")
            compose.onAllNodesWithContentDescription("Tìm kiếm").onLast().performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement("Big Buck Bunny")
            compose.onNode(hasSetTextAction()).performImeAction()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onAllNodesWithText(title).onLast().assertIsDisplayed()
            screenshot(context, "open-media-search")
            Log.i("AeroPlayTest", "OPEN_MEDIA_SEARCH_VISIBLE")
        } finally {
            activity.close()
            runBlocking { graph.settings().setThemeMode(previousTheme) }
        }
    }

    private fun screenshot(context: Context, name: String) {
        val output = File(context.getExternalFilesDir(null), "$name-api${android.os.Build.VERSION.SDK_INT}.png")
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
