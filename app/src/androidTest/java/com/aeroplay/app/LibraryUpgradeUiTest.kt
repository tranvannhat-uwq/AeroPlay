package com.aeroplay.app

import android.content.Context
import android.os.Environment
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aeroplay.app.core.model.ContentSourceType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LibraryUpgradeUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun graph(): YouTubeTestEntryPoint {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (android.os.Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        return EntryPointAccessors.fromApplication(context, YouTubeTestEntryPoint::class.java)
    }

    private fun tab(label: String) = compose.onAllNodesWithContentDescription(label).onLast().performClick()

    @Test fun shuffleWorksInEveryLibrarySectionAndRestartsFromBeginning() = runBlocking<Unit> {
        val graph = graph()
        val before = graph.settings().getSettingsSnapshot()
        val clip = graph.content().getHomeFeed(ContentSourceType.DEMO_LOCAL).getOrThrow().first { it.id == "demo_escapes" }
        val favoriteBefore = graph.history().observeIsFavorite(clip.id).first()
        val playlistId = graph.playlists().createPlaylist("QA Library Upgrades")
        graph.playlists().addVideoToPlaylist(playlistId, clip)
        if (!favoriteBefore) graph.history().toggleFavorite(clip)
        graph.history().recordWatchAndPosition(clip, 12000, 15000)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates").apply { mkdirs() }
        val file = File(directory, "library-shuffle-test.mp4")
        context.resources.openRawResource(R.raw.open_media_escapes).use { input -> file.outputStream().use { input.copyTo(it) } }
        val imported = graph.deviceMedia().importOne(FileProvider.getUriForFile(context, "${context.packageName}.updates", file))
        val device = graph.deviceMedia().videos.first().first { it.id == "device_" + com.aeroplay.app.updates.UpdateHash.sha256(file) }
        try {
            graph.settings().setActiveSourceType(ContentSourceType.DEMO_LOCAL)
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                activity.onActivity { graph.playback().dismissPlayer() }
                tab("Thư viện")
                for ((label, query, expected) in listOf(
                    Triple("Lịch sử xem", "For Bigger Escapes", clip.id),
                    Triple("Trên thiết bị", "library-shuffle-test", device.id),
                    Triple("Yêu thích", "For Bigger Escapes", clip.id),
                    Triple("Danh sách phát", "QA Library Upgrades", clip.id))) {
                    compose.onNodeWithText(label).performScrollTo().performClick()
                    compose.onNode(hasSetTextAction()).performTextReplacement(query)
                    compose.waitUntil(8_000) { compose.onAllNodesWithText("Phát ngẫu nhiên").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithText("Phát ngẫu nhiên").performClick()
                    compose.waitUntil(20_000) { graph.playback().playbackState.value.let { it.currentVideo?.id == expected && it.isPlaying } }
                    val state = graph.playback().playbackState.value
                    assertTrue(state.queue.queueTitle.startsWith("Ngẫu nhiên"))
                    assertTrue(state.queue.startFromBeginning)
                    assertTrue(state.currentPositionMs < 5000)
                    activity.onActivity { graph.playback().dismissPlayer() }
                }
                compose.onAllNodesWithText("QA Library Upgrades").onLast().performClick()
                compose.onNode(hasSetTextAction()).performTextReplacement("")
                compose.waitUntil(5_000) { compose.onAllNodesWithText("Phát ngẫu nhiên").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Phát ngẫu nhiên").performClick()
                compose.waitUntil(20_000) { graph.playback().playbackState.value.let { it.currentVideo?.id == clip.id && it.isPlaying } }
                assertTrue(graph.playback().playbackState.value.queue.startFromBeginning)
                activity.onActivity { graph.playback().dismissPlayer() }
            }
        } finally {
            graph.playlists().deletePlaylist(playlistId)
            if (!favoriteBefore && graph.history().observeIsFavorite(clip.id).first()) graph.history().toggleFavorite(clip)
            if (imported) graph.deviceMedia().remove(device.id)
            file.delete()
            graph.settings().setActiveSourceType(before.activeSourceType)
        }
    }

    @Test fun editingMixPersistsAndRestrictionCanBeManagedWithoutStartingPlayback() = runBlocking<Unit> {
        val graph = graph()
        val before = graph.settings().getSettingsSnapshot()
        val dao = graph.database().aeroPlayDao()
        val mixId = "DEMO_LOCAL:discovery"
        val oldMix = dao.observeEditedMixes().first().firstOrNull { it.mixId == mixId }
        val oldRestrictions = dao.getContentRestrictions()
        val catalog = graph.content().getHomeFeed(ContentSourceType.DEMO_LOCAL).getOrThrow()
        val removed = catalog.first { it.id == "demo_bbb" }
        val added = catalog.first { it.id == "demo_tos" }
        try {
            dao.resetEditedMix(mixId)
            graph.settings().setActiveSourceType(ContentSourceType.DEMO_LOCAL)
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                activity.onActivity { graph.playback().dismissPlayer() }
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("AutoMixCard").fetchSemanticsNodes().isNotEmpty() }
                compose.onAllNodesWithTag("AutoMixCard").onFirst().performClick()
                compose.onNodeWithText("Sửa bản phối").performClick()
                compose.onNodeWithContentDescription("Xóa khỏi bản phối: ${removed.title}").performClick()
                compose.onNodeWithText("Thêm bài hoặc video").performClick()
                compose.onNode(hasSetTextAction()).performTextReplacement("Tears")
                compose.onNode(hasSetTextAction()).performImeAction()
                compose.waitUntil(8_000) { compose.onAllNodesWithContentDescription("Thêm vào bản phối: ${added.title}").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithContentDescription("Thêm vào bản phối: ${added.title}").performClick()
                compose.onNodeWithText("Lưu thay đổi").performClick()
                compose.waitUntil(5_000) { compose.onAllNodesWithText("Sửa bản phối").fetchSemanticsNodes().isEmpty() }
                val saved = dao.observeEditedMixes().first().single { it.mixId == mixId }
                assertFalse(saved.videosJson.contains("demo_bbb"))
                assertTrue(saved.videosJson.contains("demo_tos"))
                compose.onAllNodesWithTag("AutoMixCard").onFirst().performClick()
                compose.onNodeWithContentDescription("Hạn chế gợi ý: ${added.title}").performClick()
                compose.waitUntil(5_000) { runBlocking { dao.getContentRestrictions().any { it.restrictionId == "DEMO_LOCAL:demo_tos" } } }
                assertFalse(graph.playback().playbackState.value.isPlaying)
                compose.onNodeWithText("Đóng").performClick()
                tab("Cài đặt")
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Danh sách hạn chế"))
                compose.onNodeWithText("Danh sách hạn chế").performClick()
                compose.onNodeWithText(added.title).assertIsDisplayed()
                compose.onAllNodesWithText("Gỡ").onFirst().performClick()
                compose.onNodeWithText("Phong cách").performClick()
                compose.onNode(hasSetTextAction()).performTextReplacement("remix")
                compose.onNodeWithText("Thêm hạn chế").performClick()
                compose.waitUntil(5_000) { runBlocking { dao.getContentRestrictions().any { it.restrictionId == "style:remix" } } }
            }
        } finally {
            if (oldMix == null) dao.resetEditedMix(mixId) else dao.saveEditedMix(oldMix)
            dao.getContentRestrictions().filterNot { current -> oldRestrictions.any { it.restrictionId == current.restrictionId } }
                .forEach { dao.removeContentRestriction(it.restrictionId) }
            oldRestrictions.forEach { dao.saveContentRestriction(it) }
            graph.settings().setActiveSourceType(before.activeSourceType)
        }
    }
}
