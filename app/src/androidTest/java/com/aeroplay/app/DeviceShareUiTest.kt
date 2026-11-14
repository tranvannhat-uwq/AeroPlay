package com.aeroplay.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DeviceShareUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun sharingAVideoOpensDeviceLibraryAndImportsIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = EntryPointAccessors.fromApplication(context, YouTubeTestEntryPoint::class.java)
        val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates").apply { mkdirs() }
        val fixture = File(folder, "shared-device-video.mp4")
        context.resources.openRawResource(R.raw.open_media_escapes).use { input ->
            fixture.outputStream().use { input.copyTo(it) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", fixture)
        val videoId = "device_" + com.aeroplay.app.updates.UpdateHash.sha256(fixture)
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Video", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ActivityScenario.launch<MainActivity>(intent).use {
                compose.waitUntil(15_000) {
                    val state = graph.deviceMedia().importState.value
                    !state.busy && state.completed == 1
                }
                compose.onNodeWithText("Thêm video").assertIsDisplayed()
                compose.waitUntil(5_000) {
                    compose.onAllNodesWithText("Video của bạn, luôn sẵn sàng").fetchSemanticsNodes().isEmpty()
                }
                compose.onNodeWithText("Video của bạn, luôn sẵn sàng").assertDoesNotExist()
                check(runBlocking { graph.deviceMedia().videos.first() }.any { it.id == videoId })
            }
        } finally {
            runBlocking { graph.deviceMedia().remove(videoId) }
            fixture.delete()
        }
    }
}
