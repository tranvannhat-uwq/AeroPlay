package com.aeroplay.app

import com.aeroplay.app.core.common.FormatUtils
import com.aeroplay.app.core.model.AudioTrack
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.DeliveryType
import com.aeroplay.app.core.model.PlaybackPosition
import com.aeroplay.app.core.model.PlaybackQueue
import com.aeroplay.app.core.model.SourceError
import com.aeroplay.app.core.model.StreamVariant
import com.aeroplay.app.core.model.VideoDetail
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.core.network.NewPipeOkHttpDownloader
import com.aeroplay.app.data.repository.PlaybackRepositoryImpl
import com.aeroplay.app.data.repository.StreamResolverImpl
import com.aeroplay.app.data.source.DemoLocalMediaSource
import com.aeroplay.app.data.source.ExperimentalExtractorSource
import com.aeroplay.app.data.source.SourceErrorException
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

class AeroPlayUnitTests {

    private val demoSource = DemoLocalMediaSource()
    private val streamResolver = StreamResolverImpl()

    @Test fun demoSource_preservesMovieIdentityForOldSavedUrls() {
        val detail = demoSource.getVideoDetail("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4").getOrThrow()
        assertEquals("demo_sintel", detail.id)
        assertFalse(detail.url.contains("gtv-videos-bucket"))
    }

    @Test fun demoSource_unknownUrlDoesNotPlayAnotherMovie() {
        assertTrue(demoSource.getVideoDetail("https://example.org/unknown.mp4").isFailure)
    }

    @Test
    fun formatUtils_formatsDurationAndViewsCorrectly() {
        assertEquals("09:56", FormatUtils.formatDuration(596L))
        assertEquals("1:02:05", FormatUtils.formatDuration(3725L))
        assertEquals("LIVE", FormatUtils.formatDuration(0L))
        assertEquals("12.8M lượt xem", FormatUtils.formatViewCount(12_840_000L))
        assertEquals("1.5K người đăng ký", FormatUtils.formatSubscribers(1_500L))
    }

    @Test
    fun demoSource_returnsValidStreamsAndHandlesUnsupportedVideoCleanly() {
        val recommended = demoSource.getRecommendedVideos()
        assertTrue(recommended.isNotEmpty())

        val detailResult = demoSource.getVideoDetail("demo_bbb")
        assertTrue(detailResult.isSuccess)
        val detail = detailResult.getOrThrow()
        assertTrue(detail.streamVariants.isNotEmpty())
        assertTrue("A progressive MP4 must not be mislabeled as a dedicated audio track", detail.audioTracks.isEmpty())

        // Test Case #19 & #20: Unsupported video returns SourceError.NoStreamAvailable without crashing
        val unsupportedResult = demoSource.getVideoDetail("unsupported_demo_video")
        assertTrue(unsupportedResult.isFailure)
        val ex = unsupportedResult.exceptionOrNull() as? SourceErrorException
        assertNotNull(ex)
        assertTrue(ex?.sourceError is SourceError.NoStreamAvailable)
    }

    @Test
    fun streamResolver_audioOnlyModeSelectsDedicatedAudioStream() {
        val detail = demoSource.getVideoDetail("demo_sintel").getOrThrow()
        val resolved = streamResolver.resolveStream(
            detail = detail,
            preferredQualityLabel = "720p",
            isAudioOnly = true,
            dataSaver = false
        ).getOrThrow()

        assertTrue(resolved.isAudioOnly)
        assertNotNull(resolved.selectedAudioTrack)
        assertEquals(detail.audioTracks.first().url, resolved.primaryUrl)
    }

    @Test
    fun streamResolver_dataSaverSelectsLowerResolutionAndHandlesAdaptiveFallback() {
        val base = demoSource.getVideoDetail("demo_bbb").getOrThrow()
        val detail = base.copy(streamVariants = listOf(
            StreamVariant("test_720p", "720p", width = 1280, height = 720, url = "https://example.org/720p.mp4"),
            StreamVariant("test_360p", "360p", width = 640, height = 360, url = "https://example.org/360p.mp4")
        ))
        val dataSaverResolved = streamResolver.resolveStream(
            detail = detail,
            preferredVariantId = null,
            preferredQualityLabel = "720p",
            isAudioOnly = false,
            dataSaver = true
        ).getOrThrow()

        assertEquals(360, dataSaverResolved.selectedVariant?.height)

        // Adaptive video-only with paired audio uses MergingMediaSource
        val adaptiveDetail = detail.copy(
            audioTracks = listOf(AudioTrack("paired", "AAC", 128, "audio/mp4", url = "https://example.org/audio.m4a")),
            streamVariants = listOf(
                StreamVariant(
                    id = "1080p_vo",
                    resolutionLabel = "1080p",
                    width = 1920,
                    height = 1080,
                    url = "https://example.org/video1080.mp4",
                    isVideoOnly = true,
                    deliveryType = DeliveryType.VIDEO_ONLY
                ),
                detail.streamVariants.first()
            )
        )
        val mergedSelection = streamResolver.resolveStream(
            detail = adaptiveDetail,
            preferredVariantId = "1080p_vo",
            isAudioOnly = false
        ).getOrThrow()

        assertTrue(mergedSelection.isMergedAdaptive)
        assertNotNull(mergedSelection.secondaryAudioUrl)
        assertNotNull(mergedSelection.fallbackProgressiveUrl)
    }

    @Test
    fun streamResolver_returnsFailureWhenNoStreamsExist() {
        val emptyDetail = VideoDetail(
            id = "empty_1",
            url = "https://example.org/empty",
            title = "No Stream Video",
            description = "",
            channelName = "Test",
            channelUrl = "",
            channelAvatarUrl = "",
            thumbnailUrl = "",
            durationSeconds = 100L,
            streamVariants = emptyList(),
            audioTracks = emptyList(),
            hlsManifestUrl = null,
            dashManifestUrl = null,
            sourceType = ContentSourceType.EXPERIMENTAL_EXTRACTOR
        )

        val result = streamResolver.resolveStream(emptyDetail)
        assertTrue(result.isFailure)
        val err = (result.exceptionOrNull() as? SourceErrorException)?.sourceError
        assertTrue(err is SourceError.NoStreamAvailable)
    }

    @Test
    fun playbackRepository_managesQueueTransitionsCorrectly() {
        val repo = PlaybackRepositoryImpl()
        val videos = demoSource.getRecommendedVideos()
        repo.updateQueue(PlaybackQueue(items = videos, currentIndex = 0, queueTitle = "Test Queue"))

        assertTrue(repo.activeQueue.value.hasNext)
        assertFalse(repo.activeQueue.value.hasPrevious)

        val second = repo.nextInQueue()
        assertEquals(videos[1].id, second?.id)
        assertTrue(repo.activeQueue.value.hasPrevious)

        val firstAgain = repo.previousInQueue()
        assertEquals(videos[0].id, firstAgain?.id)
    }

    @Test
    fun playbackPosition_computesProgressFractionAccurately() {
        val pos = PlaybackPosition(videoId = "v1", positionMs = 150_000L, durationMs = 300_000L)
        assertEquals(0.5f, pos.progressFraction, 0.001f)
    }

    @Test
    fun experimentalExtractor_mapsExceptionsToExplicitSourceErrors() {
        val downloader = NewPipeOkHttpDownloader(OkHttpClient())
        val extractor = ExperimentalExtractorSource(downloader)

        assertTrue(
            extractor.mapThrowableToSourceError(AgeRestrictedContentException("Age gate"))
                is SourceError.AgeRestricted
        )
        assertTrue(
            extractor.mapThrowableToSourceError(GeographicRestrictionException("Geo blocked"))
                is SourceError.GeoRestricted
        )
        assertTrue(
            extractor.mapThrowableToSourceError(ReCaptchaException("Captcha", "https://youtube.com"))
                is SourceError.RateLimitedOrCaptcha
        )
        assertTrue(
            extractor.mapThrowableToSourceError(ParsingException("Changed layout"))
                is SourceError.ExtractionBroken
        )
        assertTrue(
            extractor.mapThrowableToSourceError(IOException("Timeout"))
                is SourceError.NetworkError
        )
    }

}
