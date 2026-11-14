package com.aeroplay.app

import com.aeroplay.app.core.model.StreamVariant
import com.aeroplay.app.data.repository.StreamResolverImpl
import com.aeroplay.app.data.source.DemoLocalMediaSource
import org.junit.Assert.*
import org.junit.Test

class StreamResolverRegressionTest {
    private val resolver = StreamResolverImpl()
    private val base = DemoLocalMediaSource().getVideoDetail("demo_bbb").getOrThrow().copy(
        streamVariants = emptyList(), audioTracks = emptyList(), hlsManifestUrl = null, dashManifestUrl = null
    )
    private val videoOnly = StreamVariant(id = "videoOnly", resolutionLabel = "720p", height = 720,
        url = "https://example.org/video", isVideoOnly = true)

    @Test fun videoOnlyWithoutAudioReturnsFailure() {
        assertTrue(resolver.resolveStream(base.copy(streamVariants = listOf(videoOnly))).isFailure)
    }

    @Test fun audioOnlyNeverSelectsSilentVideo() {
        assertTrue(resolver.resolveStream(base.copy(streamVariants = listOf(videoOnly)), isAudioOnly = true).isFailure)
    }

    @Test fun missingAudioCanUseHlsManifestWithoutVariantOrCrash() {
        val result = resolver.resolveStream(base.copy(streamVariants = listOf(videoOnly),
            hlsManifestUrl = "https://example.org/master"), preferredVariantId = videoOnly.id).getOrThrow()
        assertEquals("https://example.org/master", result.primaryUrl)
        assertEquals("application/x-mpegURL", result.mimeType)
        assertFalse(result.isMergedAdaptive)
    }

    @Test fun dashManifestIsPlayableWhenItIsTheOnlyVideoSource() {
        val result = resolver.resolveStream(base.copy(dashManifestUrl = "https://example.org/dash")).getOrThrow()
        assertEquals("application/dash+xml", result.mimeType)
    }
}
