package com.aeroplay.app.data.source

import com.aeroplay.app.core.model.AudioTrack
import com.aeroplay.app.core.model.ChannelItem
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.DeliveryType
import com.aeroplay.app.core.model.PlaylistItem
import com.aeroplay.app.core.model.RemotePlaylistDetail
import com.aeroplay.app.core.model.SearchResultBundle
import com.aeroplay.app.core.model.SourceError
import com.aeroplay.app.core.model.StreamVariant
import com.aeroplay.app.core.model.SubtitleTrack
import com.aeroplay.app.core.model.VideoDetail
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.core.network.NewPipeOkHttpDownloader
import com.aeroplay.app.core.network.SourceDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import com.aeroplay.app.core.model.SearchContentType
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Experimental adapter integrating NewPipe Extractor (GPLv3).
 *
 * Strictly complies with Section II & IV.2:
 * - Isolated from official APIs.
 * - Does not bypass DRM, CAPTCHA, age gates, login walls, or geo-restrictions.
 * - Cancels network work immediately when the user leaves the screen via [runInterruptible].
 * - Retries transiently with a strict limit (max 2 retries on IOException).
 * - Never fabricates fake stream URLs or unavailable resolutions.
 */
@Singleton
class ExperimentalExtractorSource @Inject constructor(
    @Suppress("unused") private val downloader: NewPipeOkHttpDownloader
) {

    data class PaginationToken(
        val queryHandler: SearchQueryHandler,
        val page: Page
    )

    private fun youtubeService(): StreamingService = ServiceList.YouTube

    suspend fun fetchTrendingOrFeatured(): Result<List<VideoItem>> = executeWithRetry("home") {
        val service = youtubeService()
        val kioskList = service.kioskList
        val defaultKioskId = kioskList.defaultKioskId
        val kioskExtractor = kioskList.getExtractorById(defaultKioskId, null)
        // This overload of KioskInfo.getInfo expects an already fetched extractor.
        kioskExtractor.fetchPage()
        val kioskInfo = KioskInfo.getInfo(kioskExtractor)
        val items = kioskInfo.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map { it.toVideoItem() }
        if (items.isEmpty() && kioskInfo.errors.isNotEmpty()) {
            throw ExtractionException("Home items could not be extracted", kioskInfo.errors.first())
        }
        SourceDiagnostics.event("home.domain", "videos=${items.size} kiosk=$defaultKioskId")

        if (items.isNotEmpty()) {
            items
        } else {
            // Fallback to a public search query if Trending kiosk is empty in region
            val queryHandler = service.searchQHFactory.fromQuery("open source creative commons music")
            val searchInfo = SearchInfo.getInfo(service, queryHandler)
            searchInfo.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() }
        }
    }

    suspend fun search(
        query: String,
        pageToken: Any? = null,
        contentType: SearchContentType = SearchContentType.ALL
    ): Result<SearchResultBundle> = executeWithRetry("search") {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return@executeWithRetry SearchResultBundle(
                query = query,
                sourceType = ContentSourceType.EXPERIMENTAL_EXTRACTOR
            )
        }

        val service = youtubeService()
        if (pageToken is PaginationToken) {
            val morePage = SearchInfo.getMoreItems(service, pageToken.queryHandler, pageToken.page)
            val nextToken = morePage.nextPage?.takeIf { Page.isValid(it) }?.let { PaginationToken(pageToken.queryHandler, it) }
            if (morePage.items.isEmpty() && morePage.errors.isNotEmpty()) {
                throw ExtractionException("Search page could not be extracted", morePage.errors.first())
            }
            mapInfoItemsToBundle(
                query = trimmed,
                items = morePage.items,
                nextPageToken = nextToken,
                correctedQuery = null
            )
        } else {
            val queryHandler = service.searchQHFactory.fromQuery(
                trimmed,
                listOf(contentType.extractorFilter),
                ""
            )
            val searchInfo = SearchInfo.getInfo(service, queryHandler)
            if (searchInfo.relatedItems.isEmpty() && searchInfo.errors.isNotEmpty()) {
                throw ExtractionException("Search items could not be extracted", searchInfo.errors.first())
            }
            val nextToken = searchInfo.nextPage?.takeIf { Page.isValid(it) }?.let { PaginationToken(queryHandler, it) }
            mapInfoItemsToBundle(
                query = trimmed,
                items = searchInfo.relatedItems,
                nextPageToken = nextToken,
                correctedQuery = searchInfo.searchSuggestion?.takeIf { it.isNotBlank() }
            )
        }
    }

    suspend fun getPlaylist(playlist: PlaylistItem): Result<RemotePlaylistDetail> =
        executeWithRetry("playlist") {
            val url = playlist.url.ifBlank { playlist.id }
            val service = NewPipe.getServiceByUrl(url)
            val info = PlaylistInfo.getInfo(service, url)
            val videos = info.relatedItems.map { it.toVideoItem() }.toMutableList()
            var nextPage = info.nextPage
            var pagesLoaded = 1
            var pageFailed = false
            while (nextPage != null && videos.size < MAX_REMOTE_PLAYLIST_ITEMS && pagesLoaded < MAX_REMOTE_PLAYLIST_PAGES) {
                if (Thread.currentThread().isInterrupted) {
                    throw CancellationException("Playlist loading was cancelled.")
                }
                val page = try {
                    PlaylistInfo.getMoreItems(service, url, nextPage)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (videos.isEmpty()) throw error
                    pageFailed = true
                    SourceDiagnostics.failure("playlist.page", error)
                    break
                }
                videos += page.items.map { it.toVideoItem() }
                nextPage = page.nextPage
                pagesLoaded++
            }
            if (videos.isEmpty() && info.errors.isNotEmpty()) {
                throw ExtractionException("Playlist items could not be extracted", info.errors.first())
            }
            val mappedPlaylist = playlist.copy(
                id = info.url.ifBlank { playlist.id },
                url = info.url.ifBlank { url },
                title = info.name.ifBlank { playlist.title },
                uploaderName = info.uploaderName.ifBlank { playlist.uploaderName },
                thumbnailUrl = info.thumbnails.bestImageUrl().ifBlank { playlist.thumbnailUrl },
                streamCount = info.streamCount.takeIf { it > 0L } ?: playlist.streamCount,
                description = info.description?.content.orEmpty().ifBlank { playlist.description }
            )
            RemotePlaylistDetail(
                playlist = mappedPlaylist,
                videos = videos.distinctBy { "${it.sourceType.name}:${it.id}" }.take(MAX_REMOTE_PLAYLIST_ITEMS),
                hasMore = nextPage != null || pageFailed
            )
        }

    suspend fun extractVideoDetail(urlOrId: String): Result<VideoDetail> = executeWithRetry("metadata") {
        val resolvedUrl = normalizeVideoUrl(urlOrId)
        val service = runCatching { NewPipe.getServiceByUrl(resolvedUrl) }
            .getOrElse { youtubeService() }

        val streamInfo = StreamInfo.getInfo(service, resolvedUrl)

        val progressiveVariants = streamInfo.videoStreams
            .orEmpty()
            .filter { it.isUrl && it.content.isNotBlank() && isSupportedVideoFormat(it.format) }
            .map { it.toStreamVariant(isVideoOnly = false) }

        val adaptiveVideoOnlyVariants = streamInfo.videoOnlyStreams
            .orEmpty()
            .filter { it.isUrl && it.content.isNotBlank() && isSupportedVideoFormat(it.format) }
            .map { it.toStreamVariant(isVideoOnly = true) }

        // Combine progressive first (guaranteed audio+video in one stream), plus higher-res adaptive variants
        val allVariants = buildList {
            addAll(progressiveVariants.sortedByDescending { it.height })
            val existingHeights = progressiveVariants.map { it.height }.toSet()
            adaptiveVideoOnlyVariants
                .filter { it.height > 0 && it.height !in existingHeights }
                .distinctBy { "${it.height}_${it.fps}" }
                .sortedByDescending { it.height }
                .forEach { add(it) }
        }.toMutableList()

        val hlsUrl = streamInfo.hlsUrl?.takeIf { it.isNotBlank() }
        val dashUrl = streamInfo.dashMpdUrl?.takeIf { it.isNotBlank() }

        if (hlsUrl != null) {
            allVariants.add(
                0,
                StreamVariant(
                    id = "hls_auto",
                    resolutionLabel = "HLS Tự động (Adaptive)",
                    formatMimeType = "application/x-mpegURL",
                    url = hlsUrl,
                    isVideoOnly = false,
                    deliveryType = DeliveryType.HLS
                )
            )
        }

        val audioTracks = streamInfo.audioStreams
            .orEmpty()
            .filter { it.isUrl && it.content.isNotBlank() && isSupportedAudioFormat(it.format) }
            .sortedByDescending { it.averageBitrate }
            .distinctBy { "${it.averageBitrate}_${it.format?.mimeType}" }
            .map { it.toAudioTrack() }

        if (allVariants.isEmpty() && audioTracks.isEmpty() && hlsUrl == null && dashUrl == null) {
            throw SourceErrorException(
                SourceError.NoStreamAvailable(
                    message = "Bộ trích xuất không tìm thấy luồng phát hợp lệ cho video này.",
                    technicalDetail = "videoStreams=0, audioStreams=0, hls=null, dash=null"
                )
            )
        }

        val subtitleTracks = streamInfo.subtitles
            .orEmpty()
            .filter { it.isUrl && it.content.isNotBlank() }
            .map { sub ->
                SubtitleTrack(
                    id = sub.id ?: sub.languageTag,
                    languageCode = sub.languageTag.orEmpty(),
                    displayName = sub.name ?: sub.displayLanguageName.orEmpty().ifBlank { sub.languageTag },
                    mimeType = sub.format?.mimeType ?: "text/vtt",
                    isAutoGenerated = sub.isAutoGenerated,
                    url = sub.content
                )
            }

        val relatedVideos = streamInfo.relatedItems
            .orEmpty()
            .filterIsInstance<StreamInfoItem>()
            .take(20)
            .map { it.toVideoItem() }

        val isLive = streamInfo.streamType == StreamType.LIVE_STREAM ||
            streamInfo.streamType == StreamType.AUDIO_LIVE_STREAM
        SourceDiagnostics.event("metadata.domain", "video=${allVariants.size} audio=${audioTracks.size} hls=${hlsUrl != null} dash=${dashUrl != null}")

        VideoDetail(
            id = streamInfo.id.ifBlank { resolvedUrl },
            url = streamInfo.url.ifBlank { resolvedUrl },
            title = streamInfo.name.orEmpty().ifBlank { "Video không có tiêu đề" },
            description = streamInfo.description?.content.orEmpty(),
            channelName = streamInfo.uploaderName.orEmpty().ifBlank { "Kênh ẩn danh" },
            channelUrl = streamInfo.uploaderUrl.orEmpty(),
            channelAvatarUrl = streamInfo.uploaderAvatars.bestImageUrl(),
            subscriberCount = streamInfo.uploaderSubscriberCount,
            isChannelVerified = streamInfo.isUploaderVerified,
            thumbnailUrl = streamInfo.thumbnails.bestImageUrl(),
            durationSeconds = streamInfo.duration.coerceAtLeast(0L),
            viewCount = streamInfo.viewCount,
            likeCount = streamInfo.likeCount,
            uploadDateText = streamInfo.textualUploadDate.orEmpty(),
            isLive = isLive,
            streamVariants = allVariants,
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
            hlsManifestUrl = hlsUrl,
            dashManifestUrl = dashUrl,
            relatedVideos = relatedVideos,
            sourceType = ContentSourceType.EXPERIMENTAL_EXTRACTOR
        )
    }

    private fun mapInfoItemsToBundle(
        query: String,
        items: List<InfoItem>,
        nextPageToken: Any?,
        correctedQuery: String?
    ): SearchResultBundle {
        val videos = mutableListOf<VideoItem>()
        val channels = mutableListOf<ChannelItem>()
        val playlists = mutableListOf<PlaylistItem>()

        for (item in items) {
            when (item) {
                is StreamInfoItem -> videos.add(item.toVideoItem())
                is ChannelInfoItem -> channels.add(
                    ChannelItem(
                        id = item.url,
                        url = item.url,
                        name = item.name.orEmpty(),
                        avatarUrl = item.thumbnails.bestImageUrl(),
                        description = item.description.orEmpty(),
                        subscriberCount = item.subscriberCount,
                        streamCount = item.streamCount,
                        isVerified = item.isVerified,
                        sourceType = ContentSourceType.EXPERIMENTAL_EXTRACTOR
                    )
                )
                is PlaylistInfoItem -> playlists.add(
                    PlaylistItem(
                        id = item.url,
                        url = item.url,
                        title = item.name.orEmpty(),
                        uploaderName = item.uploaderName.orEmpty(),
                        thumbnailUrl = item.thumbnails.bestImageUrl(),
                        streamCount = item.streamCount,
                        sourceType = ContentSourceType.EXPERIMENTAL_EXTRACTOR,
                        description = item.description?.content.orEmpty()
                    )
                )
            }
        }

        SourceDiagnostics.event("search.domain", "videos=${videos.size} channels=${channels.size} playlists=${playlists.size} next=${nextPageToken != null}")

        return SearchResultBundle(
            query = query,
            videos = videos,
            channels = channels,
            playlists = playlists,
            nextPageToken = nextPageToken,
            correctedQuery = correctedQuery,
            sourceType = ContentSourceType.EXPERIMENTAL_EXTRACTOR
        )
    }

    private fun StreamInfoItem.toVideoItem(): VideoItem {
        val videoId = extractYoutubeVideoId(url) ?: url
        val live = streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM
        return VideoItem(
            id = videoId,
            url = url,
            title = name.orEmpty(),
            channelName = uploaderName.orEmpty().ifBlank { "YouTube Channel" },
            channelUrl = uploaderUrl.orEmpty(),
            channelAvatarUrl = uploaderAvatars.bestImageUrl(),
            thumbnailUrl = thumbnails.bestImageUrl(),
            durationSeconds = duration.coerceAtLeast(0L),
            viewCount = viewCount,
            uploadedText = textualUploadDate.orEmpty(),
            isLive = live,
            sourceType = ContentSourceType.EXPERIMENTAL_EXTRACTOR,
            publishedAtEpochMs = uploadDate?.instant?.toEpochMilli()
        )
    }

    private fun VideoStream.toStreamVariant(isVideoOnly: Boolean): StreamVariant {
        val res = getResolution().ifBlank {
            if (height > 0) "${height}p" else "Auto"
        }
        val formatName = format?.name ?: "MP4"
        val label = if (isVideoOnly) {
            "$res ($formatName • Ghép Audio)"
        } else {
            "$res ($formatName)"
        }
        val delivery = when (deliveryMethod) {
            DeliveryMethod.HLS -> DeliveryType.HLS
            DeliveryMethod.DASH -> DeliveryType.DASH
            else -> if (isVideoOnly) DeliveryType.VIDEO_ONLY else DeliveryType.PROGRESSIVE
        }
        return StreamVariant(
            id = "${id}_${res}_${isVideoOnly}",
            resolutionLabel = label,
            width = width,
            height = height.takeIf { it > 0 } ?: parseResolutionHeight(res),
            fps = fps.takeIf { it > 0 } ?: 30,
            formatMimeType = if (deliveryMethod == DeliveryMethod.HLS) "application/x-mpegURL" else format?.mimeType ?: "video/mp4",
            codec = codec.orEmpty(),
            bitrate = bitrate,
            url = content,
            isVideoOnly = isVideoOnly,
            deliveryType = delivery
        )
    }

    private fun AudioStream.toAudioTrack(): AudioTrack {
        val bitrateKbps = averageBitrate.takeIf { it > 0 } ?: 128
        val formatSuffix = format?.name ?: "M4A"
        return AudioTrack(
            id = "${id}_${bitrateKbps}",
            label = "$bitrateKbps kbps $formatSuffix",
            averageBitrateKbps = bitrateKbps,
            formatMimeType = if (deliveryMethod == DeliveryMethod.HLS) "application/x-mpegURL" else format?.mimeType ?: "audio/mp4",
            codec = codec.orEmpty(),
            languageCode = audioLocale?.language.orEmpty(),
            url = content
        )
    }

    private fun isSupportedVideoFormat(format: MediaFormat?): Boolean {
        if (format == null) return true
        return format == MediaFormat.MPEG_4 ||
            format == MediaFormat.WEBM ||
            format == MediaFormat.v3GPP
    }

    private fun isSupportedAudioFormat(format: MediaFormat?): Boolean {
        if (format == null) return true
        return format == MediaFormat.M4A ||
            format == MediaFormat.WEBMA ||
            format == MediaFormat.WEBMA_OPUS ||
            format == MediaFormat.OPUS ||
            format == MediaFormat.MP3 ||
            format == MediaFormat.OGG
    }

    private fun parseResolutionHeight(resolution: String): Int {
        val digits = resolution.takeWhile { it.isDigit() }
        return digits.toIntOrNull() ?: 360
    }

    private companion object {
        const val MAX_REMOTE_PLAYLIST_ITEMS = 80
        const val MAX_REMOTE_PLAYLIST_PAGES = 4
    }

    private fun List<Image>?.bestImageUrl(): String {
        if (this.isNullOrEmpty()) return ""
        return this.maxByOrNull { it.height }?.url ?: this.last().url
    }

    private fun normalizeVideoUrl(urlOrId: String): String {
        val trimmed = urlOrId.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        return "https://www.youtube.com/watch?v=$trimmed"
    }

    private fun extractYoutubeVideoId(url: String): String? {
        val regex = Regex("(?:v=|youtu\\.be/|shorts/)([a-zA-Z0-9_-]{11})")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }

    /**
     * Executes an extraction block on [Dispatchers.IO] using [runInterruptible] so that
     * cancelling the calling coroutine immediately aborts blocking network calls.
     * Retries up to [maxRetries] times only on transient [IOException].
     */
    private suspend fun <T> executeWithRetry(
        operation: String,
        maxRetries: Int = 2,
        block: () -> T
    ): Result<T> {
        var attempt = 0
        var lastError: Throwable? = null

        while (attempt <= maxRetries) {
            coroutineContext.ensureActive()
            SourceDiagnostics.event("$operation.extractor", "attempt=${attempt + 1}")
            try {
                val value = withContext(Dispatchers.IO) {
                    runInterruptible {
                        block()
                    }
                }
                return Result.success(value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (sourceErr: SourceErrorException) {
                SourceDiagnostics.failure(operation, sourceErr)
                return Result.failure(sourceErr)
            } catch (ioEx: IOException) {
                // SocketTimeoutException also extends InterruptedIOException. A timeout is a
                // network failure, not navigation cancellation: otherwise UI loading never ends.
                coroutineContext.ensureActive()
                SourceDiagnostics.failure(operation, ioEx)
                lastError = ioEx
                if (attempt < maxRetries) {
                    delay(450L * (attempt + 1))
                    attempt++
                    continue
                }
                return Result.failure(SourceErrorException(mapThrowableToSourceError(ioEx), ioEx))
            } catch (ex: Throwable) {
                SourceDiagnostics.failure(operation, ex)
                return Result.failure(SourceErrorException(mapThrowableToSourceError(ex), ex))
            }
        }

        val err = lastError ?: UnknownError("Unknown extraction error")
        return Result.failure(SourceErrorException(mapThrowableToSourceError(err), err))
    }

    fun mapThrowableToSourceError(throwable: Throwable): SourceError {
        if (throwable is SourceErrorException) return throwable.sourceError
        return when (throwable) {
            is AgeRestrictedContentException -> SourceError.AgeRestricted(
                technicalDetail = throwable.message
            )
            is GeographicRestrictionException -> SourceError.GeoRestricted(
                technicalDetail = throwable.message
            )
            is PrivateContentException, is PaidContentException -> SourceError.PrivateOrPaidContent(
                technicalDetail = throwable.message
            )
            is ReCaptchaException -> SourceError.RateLimitedOrCaptcha(
                technicalDetail = throwable.message
            )
            is ContentNotSupportedException -> SourceError.NoStreamAvailable(
                message = "Định dạng nội dung này không được bộ trích xuất hỗ trợ.",
                technicalDetail = throwable.message
            )
            is ContentNotAvailableException -> SourceError.NoStreamAvailable(
                message = "Nội dung không khả dụng hoặc đã bị giới hạn bởi nguồn phát.",
                technicalDetail = throwable.message
            )
            is ParsingException, is ExtractionException -> SourceError.ExtractionBroken(
                technicalDetail = "${throwable.javaClass.simpleName}: ${throwable.message}"
            )
            is IOException -> SourceError.NetworkError(
                technicalDetail = "${throwable.javaClass.simpleName}: ${throwable.message}"
            )
            else -> SourceError.UnknownError(
                technicalDetail = "${throwable.javaClass.simpleName}: ${throwable.message}"
            )
        }
    }
}
