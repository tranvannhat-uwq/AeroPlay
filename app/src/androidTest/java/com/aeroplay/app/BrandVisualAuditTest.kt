package com.aeroplay.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
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
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Actual Compose screens and bundled licensed media; no replacement mock UI. */
@RunWith(AndroidJUnit4::class)
class BrandVisualAuditTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun captureLightAndDarkScreensDialogsAndPlayer() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val graph = EntryPointAccessors.fromApplication(context, YouTubeTestEntryPoint::class.java)
        val before = runBlocking { graph.settings().getSettingsSnapshot() }
        val clip = runBlocking { graph.content().getHomeFeed(ContentSourceType.DEMO_LOCAL).getOrThrow().first { it.id == "demo_escapes" } }
        val alreadyFavorite = runBlocking { graph.history().observeIsFavorite(clip.id).first() }
        if (!alreadyFavorite) runBlocking { graph.history().toggleFavorite(clip) }
        try {
            for ((mode, suffix) in listOf(ThemeMode.LIGHT to "light", ThemeMode.DARK to "dark")) {
                runBlocking { graph.settings().setActiveSourceType(ContentSourceType.DEMO_LOCAL); graph.settings().setThemeMode(mode) }
                ActivityScenario.launch(MainActivity::class.java).use { activity ->
                    val playback = graph.playback()
                    activity.onActivity { playback.dismissPlayer(); it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                    compose.waitForIdle()
                    compose.waitUntil(8_000) { compose.onAllNodesWithTag("AutoMixCard").fetchSemanticsNodes().isNotEmpty() }
                    screenshot(context, "home-$suffix")
                    tab("Tìm kiếm")
                    compose.onNode(hasSetTextAction()).performTextReplacement("escapes")
                    compose.onNode(hasSetTextAction()).performImeAction()
                    compose.waitUntil(8_000) { compose.onAllNodesWithText(clip.title).fetchSemanticsNodes().isNotEmpty() }
                    screenshot(context, "search-$suffix")
                    compose.onNodeWithText("Video", substring = false).performClick()
                    compose.onNode(hasSetTextAction()).performTextReplacement("open")
                    compose.onNode(hasSetTextAction()).performImeAction()
                    compose.waitUntil(8_000) { compose.onAllNodesWithTag("SearchVideoResult").fetchSemanticsNodes().size >= 3 }
                    screenshot(context, "search-compact-$suffix")
                    compose.onNodeWithText("Tất cả").performClick()
                    compose.onNode(hasSetTextAction()).performTextReplacement("khongcotrongdanhmuc123")
                    compose.onNode(hasSetTextAction()).performImeAction()
                    compose.waitUntil(8_000) { compose.onAllNodesWithText("Không có kết quả").fetchSemanticsNodes().isNotEmpty() }
                    screenshot(context, "search-empty-$suffix")
                    tab("Thư viện")
                    compose.onNodeWithText("Yêu thích").performScrollTo().performClick()
                    screenshot(context, "favorites-$suffix")
                    compose.onNodeWithText("Danh sách phát").performScrollTo().performClick()
                    compose.waitUntil(5_000) { compose.onAllNodesWithText("Tạo mới").fetchSemanticsNodes().isNotEmpty() }
                    screenshot(context, "playlists-$suffix")
                    compose.onNodeWithText("Tạo mới").performClick()
                    screenshot(context, "create-playlist-dialog-$suffix")
                    compose.onNodeWithText("Hủy").performClick()
                    tab("Cài đặt")
                    compose.waitUntil(5_000) {
                        compose.onAllNodesWithText(if (mode == ThemeMode.DARK) "Tối (Midnight)" else "Sáng")
                            .filter(isSelected()).fetchSemanticsNodes().isNotEmpty()
                    }
                    screenshot(context, "settings-$suffix")
                    compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Phiên bản AeroPlay"))
                    screenshot(context, "about-$suffix")
                    compose.onNodeWithText("Giấy phép mã nguồn mở (Open Source Licenses)").performClick()
                    screenshot(context, "licenses-$suffix")
                    compose.onNodeWithText("Đóng").performClick()
                    activity.onActivity { playback.loadAndPlayVideo(clip) }
                    compose.waitUntil(20_000) { playback.playbackState.value.isPlaying }
                    activity.onActivity { playback.playerFlow.value?.pause() }
                    compose.waitUntil(5_000) { !playback.playbackState.value.isPlaying }
                    screenshot(context, "player-portrait-$suffix")
                    compose.onAllNodesWithText(requireNotNull(playback.playbackState.value.selectedStreamVariant).resolutionLabel).onFirst().performClick()
                    screenshot(context, "quality-dialog-$suffix")
                    compose.onNodeWithText("Đóng").performClick()
                    compose.onNodeWithContentDescription("Toàn màn hình / Xoay ngang").performClick()
                    compose.waitUntil(8_000) {
                        var landscape = false
                        activity.onActivity { landscape = it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
                        landscape
                    }
                    screenshot(context, "player-landscape-$suffix")
                    activity.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                    compose.waitUntil(8_000) {
                        var portrait = false
                        activity.onActivity { portrait = it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
                        portrait
                    }
                    activity.onActivity { playback.minimizeToMiniPlayer() }
                    screenshot(context, "mini-player-$suffix")
                    activity.onActivity { playback.dismissPlayer() }
                }
            }
        } finally {
            if (!alreadyFavorite) runBlocking { graph.history().toggleFavorite(clip) }
            // Explicit emulator preview mode keeps a deterministic, offline demo after this audit.
            val preview = InstrumentationRegistry.getArguments().getString("keepPreview") == "true"
            runBlocking {
                graph.settings().setThemeMode(if (preview) ThemeMode.DARK else before.themeMode)
                graph.settings().setActiveSourceType(if (preview) ContentSourceType.DEMO_LOCAL else before.activeSourceType)
            }
        }
    }

    private fun tab(label: String) { compose.onAllNodesWithContentDescription(label).onLast().performClick(); compose.waitForIdle() }
    private fun screenshot(context: Context, name: String) {
        compose.waitForIdle()
        // Semantics can settle before SurfaceFlinger presents the corresponding frame.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val dir = File(context.getExternalFilesDir(null), "brand-review").apply { mkdirs() }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val variant = InstrumentationRegistry.getArguments().getString("brandVariant", "normal")
        val file = File(dir, "$variant-$name.png")
        automation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        // Preserve review artifacts after Gradle uninstalls the test APK/application.
        for (command in listOf("mkdir -p /sdcard/Download/aeroplay-brand-review", "cp ${file.absolutePath} /sdcard/Download/aeroplay-brand-review/${file.name}")) {
            automation.executeShellCommand(command).use {
                java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() }
            }
        }
    }
}
