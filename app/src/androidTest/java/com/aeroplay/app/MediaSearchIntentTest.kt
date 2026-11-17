package com.aeroplay.app

import android.content.Context
import android.content.Intent
import android.app.SearchManager
import android.provider.MediaStore
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
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaSearchIntentTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun publicMediaSearchIntentFindsAndPlaysBundledClipThroughAudioSession() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (android.os.Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        val graph = EntryPointAccessors.fromApplication(context, YouTubeTestEntryPoint::class.java)
        val before = runBlocking { graph.settings().getSettingsSnapshot() }
        try {
            runBlocking { graph.settings().setActiveSourceType(ContentSourceType.DEMO_LOCAL) }
            val voice = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).setPackage(context.packageName)
                .putExtra(SearchManager.QUERY, "escapes").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val target = context.packageManager.resolveActivity(voice, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            assertEquals(MainActivity::class.java.name, target?.activityInfo?.name)
            ActivityScenario.launch<MainActivity>(voice).use { activity ->
                val playback = graph.playback()
                compose.waitUntil(20_000) { playback.playbackState.value.let {
                    it.currentVideo?.id == "demo_escapes" && it.isPlaying && it.isAudioOnlyMode
                } }
                assertEquals("Android Auto", playback.playbackState.value.queue.queueTitle)
                activity.onActivity { playback.dismissPlayer() }
            }
        } finally {
            runBlocking { graph.settings().setActiveSourceType(before.activeSourceType) }
        }
    }
}
