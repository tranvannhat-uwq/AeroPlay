package com.aeroplay.app

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OpenMediaAssetProbeTest {
    @Test fun inspectReplacementMediaAndExtractRealClipFrame() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("assetProbe") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sources = mapOf(
            "bbb_hd" to "https://media.w3.org/2010/05/bunny/movie.mp4",
            "bbb_low" to "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4",
            "sintel" to "https://storage.googleapis.com/shaka-demo-assets/sintel-mp4-only/v-0720p-2500k-libx264.mp4",
            "tos" to "https://storage.googleapis.com/wvmedia/clear/h264/tears/tears_h264_main_720p_8000.mp4",
            "ed" to "https://download.blender.org/ED/elephantsdream-1024-h264-st-aac.mov",
            "ed_low" to "https://download.blender.org/ED/elephantsdream-480-h264-st-aac.mov",
            "escapes" to "android.resource://com.aeroplay.app/raw/open_media_escapes"
        )
        for ((name, url) in sources) {
            if (InstrumentationRegistry.getArguments().getString("assetGroup") == "ed" && !name.startsWith("ed")) continue
            val retriever = MediaMetadataRetriever()
            try {
                if (url.startsWith("android.resource:")) retriever.setDataSource(context, Uri.parse(url))
                else retriever.setDataSource(url, emptyMap())
                val keys = listOf(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT,
                    MediaMetadataRetriever.METADATA_KEY_DURATION, MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                Log.i("AeroPlayTest", "ASSET $name width,height,durationMs,audio=${keys.map { retriever.extractMetadata(it) }}")
                if (name == "escapes") {
                    val frame = requireNotNull(retriever.getFrameAtTime(3_000_000))
                    File(context.getExternalFilesDir(null), "open_media_escapes.jpg").outputStream().use {
                        frame.compress(Bitmap.CompressFormat.JPEG, 85, it)
                    }
                    frame.recycle()
                }
            } finally { retriever.release() }
        }
    }
}
