package com.aeroplay.app

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aeroplay.app.data.source.ExperimentalExtractorSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

/** Can run against the original APK; does not require the debug test entry point. */
@RunWith(AndroidJUnit4::class)
class YouTubeBaselineDeviceTest {
    @Before fun optIn() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("aeroplayLive") == "true")
    }
    private fun source() = ExperimentalExtractorSource(
        ApplicationProvider.getApplicationContext<AeroPlayApplication>().newPipeDownloader
    )

    @Test fun searchAndMetadata() = runBlocking<Unit> {
        val videos = source().search("Blender Big Buck Bunny").getOrThrow().videos
        assertTrue(videos.isNotEmpty())
        Log.i("AeroPlayTest", "BASELINE SEARCH videos=${videos.size}")
        val detail = source().extractVideoDetail("aqz-KE-bpKQ").getOrThrow()
        assertTrue(detail.streamVariants.isNotEmpty())
        Log.i("AeroPlayTest", "BASELINE METADATA video=${detail.streamVariants.size} audio=${detail.audioTracks.size}")
    }

    @Test fun home() = runBlocking<Unit> {
        assertTrue(source().fetchTrendingOrFeatured().getOrThrow().isNotEmpty())
    }
}
