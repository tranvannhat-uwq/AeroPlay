package com.aeroplay.app.data.repository

import com.aeroplay.app.core.database.AeroPlayDao
import com.aeroplay.app.core.database.SearchHistoryEntity
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.PlaybackQueue
import com.aeroplay.app.core.model.PlaylistItem
import com.aeroplay.app.core.model.RemotePlaylistDetail
import com.aeroplay.app.core.model.SearchResultBundle
import com.aeroplay.app.core.model.SearchContentType
import com.aeroplay.app.core.model.SourceError
import com.aeroplay.app.core.model.VideoDetail
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.core.network.SourceDiagnostics
import com.aeroplay.app.data.source.DemoLocalMediaSource
import com.aeroplay.app.data.source.ExperimentalExtractorSource
import com.aeroplay.app.data.source.SourceErrorException
import com.aeroplay.app.data.youtube.YouTubePolicyFeatureFlags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepositoryImpl @Inject constructor(
    private val youtubeBrowse: com.aeroplay.app.data.youtube.YouTubeBrowseRepository,
    private val settingsRepository: SettingsRepository,
    private val demoSource: DemoLocalMediaSource,
    private val extractorSource: ExperimentalExtractorSource
) : ContentRepository {

    override suspend fun getHomeFeed(sourceOverride: ContentSourceType?): Result<List<VideoItem>> {
        val activeSource = sourceOverride ?: settingsRepository.getSettingsSnapshot().activeSourceType
        SourceDiagnostics.event("home.repository", "source=$activeSource")
        if (activeSource == ContentSourceType.EXPERIMENTAL_EXTRACTOR && !YouTubePolicyFeatureFlags.allowNewPipeYouTube) {
            return youtubeBrowse.home()
        }
        return when (activeSource) {
            ContentSourceType.DEMO_LOCAL -> Result.success(demoSource.getRecommendedVideos())
            ContentSourceType.EXPERIMENTAL_EXTRACTOR -> extractorSource.fetchTrendingOrFeatured()
            ContentSourceType.DEVICE_LOCAL -> Result.success(emptyList())
        }
    }
}

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val youtubeBrowse: com.aeroplay.app.data.youtube.YouTubeBrowseRepository,
    private val settingsRepository: SettingsRepository,
    private val demoSource: DemoLocalMediaSource,
    private val extractorSource: ExperimentalExtractorSource,
    private val dao: AeroPlayDao
) : SearchRepository {

    override suspend fun search(
        query: String,
        pageToken: Any?,
        sourceOverride: ContentSourceType?,
        contentType: SearchContentType
    ): Result<SearchResultBundle> {
        val activeSource = sourceOverride ?: settingsRepository.getSettingsSnapshot().activeSourceType
        SourceDiagnostics.event("search.repository", "source=$activeSource next=${pageToken != null}")
        if (activeSource == ContentSourceType.EXPERIMENTAL_EXTRACTOR && !YouTubePolicyFeatureFlags.allowNewPipeYouTube) {
            if (contentType != SearchContentType.ALL && contentType != SearchContentType.VIDEO) {
                return Result.failure(IllegalArgumentException("Tìm kiếm YouTube hiện hỗ trợ tab Tất cả và Video. Playlist tài khoản ở Cài đặt → Tài khoản YouTube."))
            }
            return youtubeBrowse.search(query, pageToken as? String)
        }
        return when (activeSource) {
            ContentSourceType.DEMO_LOCAL -> Result.success(demoSource.search(query))
            ContentSourceType.EXPERIMENTAL_EXTRACTOR -> extractorSource.search(query, pageToken, contentType)
            ContentSourceType.DEVICE_LOCAL -> Result.success(SearchResultBundle(query = query, sourceType = activeSource))
        }
    }

    override fun observeSearchHistory(): Flow<List<String>> {
        return dao.observeSearchHistory().map { list -> list.map { it.query } }
    }

    override suspend fun saveSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            dao.upsertSearchQuery(SearchHistoryEntity(query = trimmed))
        }
    }

    override suspend fun deleteSearchQuery(query: String) {
        dao.deleteSearchQuery(query)
    }

    override suspend fun clearSearchHistory() {
        dao.clearSearchHistory()
    }
}

@Singleton
class VideoMetadataRepositoryImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val demoSource: DemoLocalMediaSource,
    private val extractorSource: ExperimentalExtractorSource,
    private val deviceMedia: DeviceMediaRepository
) : VideoMetadataRepository {

    override suspend fun getVideoDetail(
        videoIdOrUrl: String,
        sourceHint: ContentSourceType?
    ): Result<VideoDetail> {
        if (sourceHint == ContentSourceType.DEVICE_LOCAL || videoIdOrUrl.startsWith("device_")) {
            return deviceMedia.detail(videoIdOrUrl)
        }
        val isDemoId = videoIdOrUrl.startsWith("demo_") ||
            videoIdOrUrl.contains("commondatastorage.googleapis.com")
        if (isDemoId) {
            return demoSource.getVideoDetail(videoIdOrUrl)
        }

        val isYoutubeUrl = videoIdOrUrl.contains("youtube.com") ||
            videoIdOrUrl.contains("youtu.be")
        if (!YouTubePolicyFeatureFlags.allowNewPipeYouTube &&
            (isYoutubeUrl || sourceHint == ContentSourceType.EXPERIMENTAL_EXTRACTOR)
        ) {
            return Result.failure(IllegalStateException("Chế độ YouTube API chỉ đọc không chuyển video YouTube vào Media3. Hãy mở bằng ứng dụng YouTube."))
        }
        val effectiveSource = when {
            isYoutubeUrl -> ContentSourceType.EXPERIMENTAL_EXTRACTOR
            sourceHint != null -> sourceHint
            else -> settingsRepository.getSettingsSnapshot().activeSourceType
        }

        SourceDiagnostics.event("metadata.repository", "source=$effectiveSource")

        return when (effectiveSource) {
            ContentSourceType.DEMO_LOCAL -> demoSource.getVideoDetail(videoIdOrUrl)
            ContentSourceType.EXPERIMENTAL_EXTRACTOR -> extractorSource.extractVideoDetail(videoIdOrUrl)
            ContentSourceType.DEVICE_LOCAL -> deviceMedia.detail(videoIdOrUrl)
        }
    }
}

@Singleton
class RemotePlaylistRepositoryImpl @Inject constructor(
    private val extractorSource: ExperimentalExtractorSource,
    private val demoSource: DemoLocalMediaSource
) : RemotePlaylistRepository {
    override suspend fun getPlaylist(playlist: PlaylistItem): Result<RemotePlaylistDetail> {
        if (playlist.sourceType == ContentSourceType.DEMO_LOCAL && playlist.id == "playlist_open_movies") {
            val videos = demoSource.getRecommendedVideos().take(playlist.streamCount.coerceAtLeast(0L).toInt())
            return Result.success(RemotePlaylistDetail(playlist, videos))
        }
        if (!YouTubePolicyFeatureFlags.allowNewPipeYouTube && playlist.sourceType.isExperimental) {
            return Result.failure(IllegalStateException("Chế độ API chính thức không đọc playlist bằng NewPipe."))
        }
        if (!playlist.sourceType.isExperimental) {
            return Result.failure(IllegalArgumentException("Nguồn playlist này chưa hỗ trợ phát nội dung."))
        }
        return extractorSource.getPlaylist(playlist)
    }
}

@Singleton
class StreamResolverImpl @Inject constructor() : StreamResolver {

    override fun resolveStream(
        detail: VideoDetail,
        preferredVariantId: String?,
        preferredQualityLabel: String,
        isAudioOnly: Boolean,
        dataSaver: Boolean
    ): Result<ResolvedStreamSelection> {
        val progressiveFallbackUrl = detail.streamVariants
            .firstOrNull { !it.isVideoOnly && it.url.isNotBlank() }
            ?.url
            ?: detail.hlsManifestUrl

        // 1. Audio-Only mode: genuinely switch to audio stream to save mobile data (Section VII)
        if (isAudioOnly) {
            val bestAudio = if (dataSaver) {
                detail.audioTracks.minByOrNull { it.averageBitrateKbps }
            } else {
                detail.audioTracks.maxByOrNull { it.averageBitrateKbps }
            }
            if (bestAudio != null && bestAudio.url.isNotBlank()) {
                return Result.success(
                    ResolvedStreamSelection(
                        primaryUrl = bestAudio.url,
                        mimeType = bestAudio.formatMimeType,
                        selectedAudioTrack = bestAudio,
                        isAudioOnly = true,
                        isMergedAdaptive = false,
                        fallbackProgressiveUrl = progressiveFallbackUrl
                    )
                )
            }
            // Fallback to lowest-bitrate progressive video stream if source has no dedicated audio track
            val lowBitrateVariant = detail.streamVariants
                .filter { !it.isVideoOnly && it.url.isNotBlank() }
                .minByOrNull { it.height.takeIf { h -> h > 0 } ?: 360 }

            if (lowBitrateVariant != null) {
                return Result.success(
                    ResolvedStreamSelection(
                        primaryUrl = lowBitrateVariant.url,
                        mimeType = lowBitrateVariant.formatMimeType,
                        selectedVariant = lowBitrateVariant,
                        isAudioOnly = true,
                        isMergedAdaptive = false,
                        fallbackProgressiveUrl = progressiveFallbackUrl
                    )
                )
            }
        }

        // 2. Video mode: select requested variant or match preferred quality
        val targetHeight = if (dataSaver && preferredVariantId == null) {
            360
        } else {
            preferredQualityLabel.takeWhile { it.isDigit() }.toIntOrNull() ?: 720
        }

        val chosenVariant = when {
            preferredVariantId != null -> detail.streamVariants.find { it.id == preferredVariantId }
            else -> {
                // Prefer progressive streams (embedded audio+video) first for rock-solid reliability
                val progressiveCandidates = detail.streamVariants.filter { !it.isVideoOnly }
                progressiveCandidates.minByOrNull { kotlin.math.abs(it.height - targetHeight) }
                    ?: detail.streamVariants.minByOrNull { kotlin.math.abs(it.height - targetHeight) }
            }
        } ?: detail.streamVariants.firstOrNull()

        if (chosenVariant != null) {
            if (chosenVariant.isVideoOnly) {
                val pairedAudio = detail.audioTracks.maxByOrNull { it.averageBitrateKbps }
                if (pairedAudio != null) {
                    return Result.success(
                        ResolvedStreamSelection(
                            primaryUrl = chosenVariant.url,
                            secondaryAudioUrl = pairedAudio.url,
                            mimeType = chosenVariant.formatMimeType,
                            selectedVariant = chosenVariant,
                            selectedAudioTrack = pairedAudio,
                            isAudioOnly = false,
                            isMergedAdaptive = true,
                            fallbackProgressiveUrl = progressiveFallbackUrl
                        )
                    )
                } else {
                    // Requirement VI.11: If video-only has no audio to merge, fall back safely to progressive
                    resolveProgressiveFallback(detail)?.let { return Result.success(it) }
                    return Result.failure(SourceErrorException(SourceError.NoStreamAvailable(
                        "Luồng video này không có âm thanh tương thích để ghép."
                    )))
                }
            }

            return Result.success(
                ResolvedStreamSelection(
                    primaryUrl = chosenVariant.url,
                    mimeType = chosenVariant.formatMimeType,
                    selectedVariant = chosenVariant,
                    selectedAudioTrack = detail.audioTracks.firstOrNull(),
                    isAudioOnly = false,
                    isMergedAdaptive = false,
                    fallbackProgressiveUrl = progressiveFallbackUrl
                )
            )
        }

        if (!detail.hlsManifestUrl.isNullOrBlank()) {
            return Result.success(
                ResolvedStreamSelection(
                    primaryUrl = detail.hlsManifestUrl,
                    mimeType = "application/x-mpegURL",
                    isAudioOnly = isAudioOnly,
                    isMergedAdaptive = false
                )
            )
        }

        if (!isAudioOnly && !detail.dashManifestUrl.isNullOrBlank()) {
            return Result.success(ResolvedStreamSelection(
                primaryUrl = detail.dashManifestUrl,
                mimeType = "application/dash+xml"
            ))
        }

        return Result.failure(
            SourceErrorException(
                SourceError.NoStreamAvailable("Không có luồng video hoặc âm thanh tương thích với Media3.")
            )
        )
    }

    override fun resolveProgressiveFallback(detail: VideoDetail): ResolvedStreamSelection? {
        val progressive = detail.streamVariants.firstOrNull { !it.isVideoOnly && it.url.isNotBlank() }
        if (progressive == null) {
            return detail.hlsManifestUrl?.takeIf { it.isNotBlank() }?.let {
                ResolvedStreamSelection(primaryUrl = it, mimeType = "application/x-mpegURL")
            }
        }
        return ResolvedStreamSelection(
            primaryUrl = progressive.url,
            mimeType = progressive.formatMimeType,
            selectedVariant = progressive,
            isAudioOnly = false,
            isMergedAdaptive = false
        )
    }
}

@Singleton
class PlaybackRepositoryImpl @Inject constructor() : PlaybackRepository {
    private val _activeQueue = MutableStateFlow(PlaybackQueue())
    override val activeQueue: StateFlow<PlaybackQueue> = _activeQueue.asStateFlow()

    override fun updateQueue(queue: PlaybackQueue) {
        _activeQueue.value = queue
    }

    override fun selectQueueIndex(index: Int): VideoItem? {
        val current = _activeQueue.value
        if (index !in current.items.indices) return null
        _activeQueue.value = current.copy(currentIndex = index)
        return current.items[index]
    }

    override fun nextInQueue(): VideoItem? {
        val current = _activeQueue.value
        if (!current.hasNext) return null
        val nextIdx = current.currentIndex + 1
        _activeQueue.value = current.copy(currentIndex = nextIdx)
        return current.items[nextIdx]
    }

    override fun previousInQueue(): VideoItem? {
        val current = _activeQueue.value
        if (!current.hasPrevious) return null
        val prevIdx = current.currentIndex - 1
        _activeQueue.value = current.copy(currentIndex = prevIdx)
        return current.items[prevIdx]
    }
}
