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
import com.aeroplay.app.updates.AppRelease
import com.aeroplay.app.updates.UpdateHash
import com.aeroplay.app.updates.UpdateStage
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppUpdateDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun signedCandidateAndTamperedFileAreHandledBeforeInstaller() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates").apply { mkdirs() }
        val file = File(directory, "test-candidate.apk")
        assumeTrue("Provide a signed v1.1.1 candidate via adb", file.isFile)
        val graph = EntryPointAccessors.fromApplication(context, YouTubeTestEntryPoint::class.java)
        val release = AppRelease("tranvannhat-uwq/AeroPlay", "v1.1.1", "1.1.1", 4, 26, "AeroPlay-v1.1.1.apk",
            "https://github.com/tranvannhat-uwq/AeroPlay/releases/download/v1.1.1/AeroPlay-v1.1.1.apk", file.length(), UpdateHash.sha256(file), "test fixture")
        graph.updateVerifier().verify(file, release)
        assertThrows(IllegalArgumentException::class.java) { graph.updateVerifier().verify(file, release.copy(sha256 = "0".repeat(64))) }
        assertThrows(IllegalArgumentException::class.java) { graph.updateVerifier().verify(file, release.copy(versionCode = 3, versionName = "1.1.0")) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.contentResolver.openInputStream(uri).use { assertEquals('P'.code, requireNotNull(it).read()) }
        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(context, "${context.packageName}.updates", File(context.filesDir, "not-an-update.txt"))
        }
    }

    @Test fun settingsContainsUpdaterAndGitHubCheckReportsItsRealState() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("aeroplayLive") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = EntryPointAccessors.fromApplication(context, YouTubeTestEntryPoint::class.java)
        val activity = ActivityScenario.launch(MainActivity::class.java)
        try {
            compose.onNodeWithContentDescription("Cài đặt").performClick()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Cập nhật ứng dụng"))
            compose.onNodeWithText("Cập nhật ứng dụng").assertIsDisplayed()
            compose.onNodeWithText("Kiểm tra cập nhật").performClick()
            val deadline = System.currentTimeMillis() + 60_000
            while (System.currentTimeMillis() < deadline && graph.updates().state.value.stage == UpdateStage.CHECKING) delay(200)
            assertTrue(graph.updates().state.value.message, graph.updates().state.value.stage in setOf(UpdateStage.CURRENT, UpdateStage.NO_RELEASE, UpdateStage.AVAILABLE))
            assertEquals("tranvannhat-uwq/AeroPlay", graph.updates().state.value.repository)
        } finally { graph.updates().dismissPrompt(); activity.close() }
    }
}
