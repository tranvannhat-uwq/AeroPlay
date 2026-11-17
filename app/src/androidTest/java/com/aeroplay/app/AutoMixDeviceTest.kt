package com.aeroplay.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aeroplay.app.core.model.ContentSourceType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoMixDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun realSourceCandidatesCanFormMusicMixes() {
        assumeTrue("Explicit opt-in live source check", InstrumentationRegistry.getArguments().getString("liveMix") == "true")
        assertFalse(BuildConfig.YOUTUBE_ACCOUNT_SYNC_ENABLED)
        assertFalse(BuildConfig.YOUTUBE_API_ONLY_MODE)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = EntryPointAccessors.fromApplication(context, YouTubeTestEntryPoint::class.java)
        val result = runBlocking { graph.recommendations().recommendations(emptyList(), emptyList(), emptyList(),
            emptyList(), ContentSourceType.EXPERIMENTAL_EXTRACTOR) }
        val report = java.io.File(context.getExternalFilesDir(null), "auto-mix-live.txt")
        report.writeText("mixes=${result.autoMixes.size}\nmessage=${result.message}\n" +
            result.autoMixes.joinToString("\n") { mix -> "${mix.title}: ${mix.videos.size} tracks\n" + mix.videos.take(4).joinToString("\n") { "${it.durationSeconds}s ${it.title}" } })
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "cp ${report.absolutePath} /sdcard/Download/auto-mix-live.txt").use {
                java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() }
            }
        assertTrue("Expected real music candidates; ${result.message}", result.autoMixes.any { it.isMusic && it.videos.size >= 3 })
        assertTrue(result.autoMixes.flatMap { it.videos }.all { it.sourceType == ContentSourceType.EXPERIMENTAL_EXTRACTOR })
        if (InstrumentationRegistry.getArguments().getString("keepMusicPreview") == "true") {
            runBlocking { graph.settings().setActiveSourceType(ContentSourceType.EXPERIMENTAL_EXTRACTOR, warningAccepted = true) }
        }
    }

    @Test fun openingMixAndSelectingAlreadyLoadedVideoReplacesQueueAndResumes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        val graph = EntryPointAccessors.fromApplication(context, YouTubeTestEntryPoint::class.java)
        val before = runBlocking { graph.settings().getSettingsSnapshot() }
        val clip = runBlocking { graph.content().getHomeFeed(ContentSourceType.DEMO_LOCAL).getOrThrow().first { it.id == "demo_escapes" } }
        try {
            runBlocking { graph.settings().setActiveSourceType(ContentSourceType.DEMO_LOCAL) }
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                val playback = graph.playback()
                activity.onActivity { playback.dismissPlayer(); playback.loadAndPlayVideo(clip, openFullPlayer = false) }
                compose.waitUntil(20_000) { playback.playbackState.value.isPlaying }
                activity.onActivity { playback.playerFlow.value?.pause() }
                compose.waitUntil(5_000) { !playback.playbackState.value.isPlaying }
                val detail = playback.playbackState.value.currentDetail
                assertEquals(1, playback.playbackState.value.queue.items.size)
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("AutoMixCard").fetchSemanticsNodes().isNotEmpty() }
                compose.onAllNodesWithTag("AutoMixCard").onFirst().performClick()
                compose.onNodeWithText("Phát bản phối").assertIsDisplayed()
                compose.onAllNodesWithText(clip.title).onLast().performClick()
                compose.waitUntil(10_000) { playback.playbackState.value.let { it.queue.items.size >= 3 && it.isPlaying && it.isFullPlayerOpen } }
                val state = playback.playbackState.value
                assertEquals("Khám phá Chuẩn Mở", state.queue.queueTitle)
                assertEquals(clip.id, state.queue.currentItem?.id)
                assertSame("Reuse the current player detail instead of extracting again", detail, state.currentDetail)
                assertFalse(state.queue.radioMode)
                assertFalse(state.queue.autoRadioAfterEnd)
                activity.onActivity { playback.dismissPlayer() }
            }
        } finally {
            runBlocking { graph.settings().setActiveSourceType(before.activeSourceType) }
        }
    }
}
