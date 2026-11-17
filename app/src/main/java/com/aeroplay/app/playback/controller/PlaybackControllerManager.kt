package com.aeroplay.app.playback.controller

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.aeroplay.app.core.model.PlaybackQueue
import com.aeroplay.app.core.model.PlaybackState
import com.aeroplay.app.core.model.SourceError
import com.aeroplay.app.core.model.StreamVariant
import com.aeroplay.app.core.model.VideoDetail
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.core.network.SourceDiagnostics
import com.aeroplay.app.data.repository.HistoryRepository
import com.aeroplay.app.data.repository.MusicRecommendationRanker
import com.aeroplay.app.data.repository.MusicSuggestionRepository
import com.aeroplay.app.data.repository.PlaybackRepository
import com.aeroplay.app.data.repository.ResolvedStreamSelection
import com.aeroplay.app.data.repository.SettingsRepository
import com.aeroplay.app.data.youtube.YouTubePolicyFeatureFlags
import com.aeroplay.app.data.repository.StreamResolver
import com.aeroplay.app.data.repository.VideoMetadataRepository
import com.aeroplay.app.data.source.SourceErrorException
import com.aeroplay.app.playback.service.AeroPlayMediaService
import com.aeroplay.app.playback.car.CarMediaLibrary
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.Futures
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single coordinator between the UI and [AeroPlayMediaService] via [MediaController].
 * Prevents duplicate players or duplicate MediaSessions (Requirements V.3, V.4, VI.1).
 */
@Singleton
class PlaybackControllerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataRepository: VideoMetadataRepository,
    private val streamResolver: StreamResolver,
    private val playbackRepository: PlaybackRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val musicSuggestionRepository: MusicSuggestionRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _playerFlow = MutableStateFlow<Player?>(null)
    private var carAudioOverride = false
    private var audioOnlyBeforeCar = false
    val playerFlow: StateFlow<Player?> = _playerFlow.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var extractionJob: Job? = null
    private var radioRecommendationJob: Job? = null
    private var progressPollingJob: Job? = null
    private var currentStreamSelection: ResolvedStreamSelection? = null
    private var hasAttemptedProgressiveFallback = false
    private var lastPersistedSecond = -1L
    private var historyRecordedForSelection = false
    private var completionRecordedForSelection = false
    private var lastSamplePositionMs = -1L
    private var lastSampleRealtimeMs = -1L
    private var sessionWatchedMs = 0L
    private var pendingWatchedMs = 0L
    private var autoPlaySimilarMusic = true

    init {
        connectSession()
        observeSettings()
    }

    private fun connectSession() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, AeroPlayMediaService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching {
                    val controller = future.get()
                    bindController(controller)
                }.onFailure {
                    SourceDiagnostics.failure("session.connect", it)
                    _playbackState.update { state ->
                        state.copy(isLoadingMetadata = false, isBuffering = false,
                            error = SourceError.PlaybackFailed("Không thể kết nối dịch vụ phát media."))
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun bindController(controller: MediaController) {
        if (mediaController === controller) return
        mediaController = controller
        _playerFlow.value = controller
        attachPlayerListener(controller)
        synchronizeCarPlayback(controller)
        startProgressTicker()
        SourceDiagnostics.event("session.connected", "ready=true")
    }

    private fun observeSettings() {
        scope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                autoPlaySimilarMusic = settings.autoPlaySimilarMusic
                val controller = mediaController ?: return@collectLatest
                val args = Bundle().apply {
                    putBoolean(AeroPlayMediaService.EXTRA_NOISY_ENABLED, settings.pauseOnHeadphoneDisconnect)
                }
                runCatching {
                    controller.sendCustomCommand(
                        SessionCommand(AeroPlayMediaService.ACTION_SET_NOISY_HANDLING, Bundle.EMPTY),
                        args
                    )
                }
            }
        }
    }

    private fun attachPlayerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                synchronizeCarPlayback(controller)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.update {
                    it.copy(
                        isPlaying = isPlaying,
                        currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
                        durationMs = controller.duration.coerceAtLeast(0L)
                    )
                }
                if (CarMediaLibrary.summary(controller.currentMediaItem) != null) return
                if (!isPlaying) {
                    _playbackState.value.currentVideo?.let { video ->
                        flushWatchedDuration(video.id, controller.duration.coerceAtLeast(0L), controller.currentPosition.coerceAtLeast(0L))
                    }
                    lastSamplePositionMs = -1L
                    lastSampleRealtimeMs = -1L
                    persistCurrentPosition()
                } else {
                    val state = _playbackState.value
                    val video = state.currentVideo
                    if (!historyRecordedForSelection && video != null && controller.currentMediaItem?.mediaId == video.id) {
                        historyRecordedForSelection = true
                        val position = controller.currentPosition.coerceAtLeast(0L)
                        val duration = controller.duration.coerceAtLeast(0L)
                        scope.launch { historyRepository.recordWatchAndPosition(video, position, duration) }
                    }
                    lastSamplePositionMs = controller.currentPosition.coerceAtLeast(0L)
                    lastSampleRealtimeMs = SystemClock.elapsedRealtime()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                SourceDiagnostics.event("player.state", "state=$playbackState")
                val buffering = playbackState == Player.STATE_BUFFERING
                val duration = controller.duration.takeIf { it > 0L }
                    ?: ((_playbackState.value.currentDetail?.durationSeconds ?: 0L) * 1000L)
                _playbackState.update {
                    it.copy(
                        isBuffering = buffering,
                        durationMs = duration.coerceAtLeast(0L),
                        currentPositionMs = controller.currentPosition.coerceAtLeast(0L)
                    )
                }

                if (playbackState == Player.STATE_ENDED) {
                    if (CarMediaLibrary.summary(controller.currentMediaItem) != null) return
                    persistCurrentPosition()
                    val video = _playbackState.value.currentVideo
                    val durationMs = controller.duration.takeIf { it > 0L }
                        ?: ((_playbackState.value.currentDetail?.durationSeconds ?: 0L) * 1000L)
                    if (video != null) flushWatchedDuration(video.id, durationMs, controller.currentPosition.coerceAtLeast(0L))
                    if (!completionRecordedForSelection && video != null && durationMs > 0L &&
                        sessionWatchedMs >= durationMs * COMPLETION_THRESHOLD
                    ) {
                        completionRecordedForSelection = true
                        scope.launch { historyRepository.recordCompletion(video.id) }
                    }
                    val activeQueue = playbackRepository.activeQueue.value
                    if (activeQueue.hasNext && (!activeQueue.radioMode || autoPlaySimilarMusic)) {
                        playNext()
                    } else if (!activeQueue.hasNext && activeQueue.autoRadioAfterEnd && autoPlaySimilarMusic) {
                        continuePlaylistWithRadio(activeQueue)
                    } else if (!activeQueue.hasNext && activeQueue.radioMode && autoPlaySimilarMusic) {
                        requestRadioRecommendations(activeQueue.currentItem, _playbackState.value.currentDetail)
                    }
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _playbackState.update { it.copy(playbackSpeed = playbackParameters.speed) }
            }

            override fun onPlayerError(error: PlaybackException) {
                SourceDiagnostics.failure("player.${error.errorCodeName}", error)
                // Requirement VI.11: If adaptive video+audio merge failed, fall back to progressive stream
                val detail = _playbackState.value.currentDetail
                val selection = currentStreamSelection
                if (detail != null && selection?.isMergedAdaptive == true && !hasAttemptedProgressiveFallback) {
                    val fallback = streamResolver.resolveProgressiveFallback(detail)
                    if (fallback != null) {
                        hasAttemptedProgressiveFallback = true
                        val resumePos = controller.currentPosition.coerceAtLeast(0L)
                        applyResolvedStream(
                            video = _playbackState.value.currentVideo ?: return,
                            detail = detail,
                            selection = fallback,
                            startPositionMs = resumePos,
                            autoPlay = true
                        )
                        return
                    }
                }

                _playbackState.update {
                    it.copy(
                        isBuffering = false,
                        isPlaying = false,
                        error = SourceError.PlaybackFailed(
                            message = "Không thể phát luồng media này (${error.errorCodeName}).",
                            technicalDetail = error.message
                        )
                    )
                }
            }
        })
    }

    private fun startProgressTicker() {
        progressPollingJob?.cancel()
        progressPollingJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && (controller.isPlaying || controller.playbackState == Player.STATE_BUFFERING)) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.takeIf { it > 0L }
                        ?: ((_playbackState.value.currentDetail?.durationSeconds ?: 0L) * 1000L)
                    val buf = controller.bufferedPosition.coerceAtLeast(0L)
                    if (controller.isPlaying) {
                        val video = _playbackState.value.currentVideo
                        val now = SystemClock.elapsedRealtime()
                        if (video != null && controller.currentMediaItem?.mediaId == video.id &&
                            lastSamplePositionMs >= 0L && lastSampleRealtimeMs >= 0L
                        ) {
                            val wallDelta = (now - lastSampleRealtimeMs).coerceAtLeast(0L)
                            val playbackDelta = pos - lastSamplePositionMs
                            val plausibleMaximum = wallDelta * MAXIMUM_TRACKING_SPEED + SEEK_TOLERANCE_MS
                            if (playbackDelta in 1L..plausibleMaximum) {
                                sessionWatchedMs += playbackDelta
                                pendingWatchedMs += playbackDelta
                                if (pendingWatchedMs >= MIN_PERSISTED_WATCH_DELTA_MS) {
                                    flushWatchedDuration(video.id, dur, pos)
                                }
                            }
                        }
                        lastSamplePositionMs = pos
                        lastSampleRealtimeMs = now
                    } else {
                        lastSamplePositionMs = -1L
                        lastSampleRealtimeMs = -1L
                    }
                    _playbackState.update {
                        it.copy(
                            currentPositionMs = pos,
                            durationMs = dur,
                            bufferedPositionMs = buf
                        )
                    }
                    val currentSec = pos / 1000L
                    if (currentSec > 0L && currentSec % 5L == 0L && currentSec != lastPersistedSecond) {
                        lastPersistedSecond = currentSec
                        persistCurrentPosition()
                    }
                }
                delay(500L)
            }
        }
    }

    /** Route Assistant's public voice-search intent through the existing audio library session. */
    fun playFromMediaSearch(query: String) {
        scope.launch {
            try {
                val controller = mediaController ?: withTimeout(10_000) { checkNotNull(controllerFuture).await() }
                val request = MediaItem.Builder().setRequestMetadata(
                    MediaItem.RequestMetadata.Builder().setSearchQuery(query.trim().take(200)).build()
                ).build()
                controller.setMediaItem(request)
                controller.prepare()
                controller.play()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                SourceDiagnostics.failure("media.search.intent", error)
                _playbackState.update { it.copy(error = SourceError.PlaybackFailed("Không thể phát kết quả tìm nhạc. Hãy thử lại.")) }
            }
        }
    }

    fun loadAndPlayVideo(
        video: VideoItem,
        queueItems: List<VideoItem> = listOf(video),
        queueTitle: String = "Danh sách đang phát",
        openFullPlayer: Boolean = true,
        radioMode: Boolean = false,
        autoRadioAfterEnd: Boolean = false,
        replaceQueue: Boolean = false
    ) {
        if (carAudioOverride) {
            // A phone selection takes over the same player; don't let old car events replace its UI state.
            val restoreAudioOnly = audioOnlyBeforeCar
            mediaController?.pause()
            mediaController?.clearMediaItems()
            carAudioOverride = false
            _playbackState.update { it.copy(isAudioOnlyMode = restoreAudioOnly) }
        }
        if (YouTubePolicyFeatureFlags.apiOnlyMode && YouTubePolicyFeatureFlags.isYouTubeVideo(video)) {
            mediaController?.pause()
            if (com.aeroplay.app.feature.player.YouTubePlayerActivity.open(context, video.url)) return
            _playbackState.update {
                it.copy(
                    isLoadingMetadata = false,
                    isBuffering = false,
                    error = SourceError.PlaybackFailed(
                        "Chế độ YouTube API chỉ đọc không phát luồng YouTube trong AeroPlay. Dùng nút bên dưới để mở video bằng YouTube."
                    )
                )
            }
            SourceDiagnostics.event("player.policy", "blocked=youtube_api_only")
            return
        }
        SourceDiagnostics.event("player.selection", "source=${video.sourceType}")
        val currentLoadedId = _playbackState.value.currentVideo?.id
        if (currentLoadedId == video.id && _playbackState.value.currentVideo?.sourceType == video.sourceType &&
            _playbackState.value.currentDetail != null && _playbackState.value.error == null) {
            if (replaceQueue) {
                radioRecommendationJob?.cancel()
                val items = if (queueItems.any { it.id == video.id && it.sourceType == video.sourceType }) queueItems else listOf(video) + queueItems
                val queue = PlaybackQueue(items, items.indexOfFirst { it.id == video.id }.coerceAtLeast(0),
                    queueTitle, radioMode, autoRadioAfterEnd)
                playbackRepository.updateQueue(queue)
                _playbackState.update { it.copy(queue = queue) }
                mediaController?.play()
            }
            // Requirement V.3: Do not open duplicate players for the same active content
            _playbackState.update {
                it.copy(
                    isFullPlayerOpen = openFullPlayer,
                    isMiniPlayerVisible = !openFullPlayer
                )
            }
            return
        }

        _playbackState.value.currentVideo?.let { previous ->
            val previousDuration = mediaController?.duration?.coerceAtLeast(0L)
                ?: ((_playbackState.value.currentDetail?.durationSeconds ?: 0L) * 1000L)
            flushWatchedDuration(
                previous.id,
                previousDuration,
                mediaController?.currentPosition?.coerceAtLeast(0L) ?: _playbackState.value.currentPositionMs
            )
        }
        persistCurrentPosition()
        extractionJob?.cancel()
        radioRecommendationJob?.cancel()
        historyRecordedForSelection = false
        completionRecordedForSelection = false
        lastSamplePositionMs = -1L
        lastSampleRealtimeMs = -1L
        sessionWatchedMs = 0L
        pendingWatchedMs = 0L
        lastPersistedSecond = -1L
        hasAttemptedProgressiveFallback = false

        val normalizedQueue = if (queueItems.any { it.id == video.id }) {
            queueItems
        } else {
            listOf(video) + queueItems
        }
        val idx = normalizedQueue.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
        val queue = PlaybackQueue(
            items = normalizedQueue,
            currentIndex = idx,
            queueTitle = queueTitle,
            radioMode = radioMode,
            autoRadioAfterEnd = autoRadioAfterEnd
        )
        playbackRepository.updateQueue(queue)

        _playbackState.update {
            it.copy(
                currentVideo = video,
                currentDetail = null,
                isLoadingMetadata = true,
                isBuffering = true,
                error = null,
                queue = queue,
                isFullPlayerOpen = openFullPlayer,
                isMiniPlayerVisible = !openFullPlayer
            )
        }

        extractionJob = scope.launch {
            val settings = settingsRepository.getSettingsSnapshot()
            val savedPosition = historyRepository.getSavedPosition(video.id)?.positionMs ?: 0L

            val detailResult = metadataRepository.getVideoDetail(
                videoIdOrUrl = video.url.ifBlank { video.id },
                sourceHint = video.sourceType
            )

            detailResult.fold(
                onSuccess = { detail ->
                    val resolvedResult = streamResolver.resolveStream(
                        detail = detail,
                        preferredVariantId = null,
                        preferredQualityLabel = settings.defaultQualityLabel,
                        isAudioOnly = _playbackState.value.isAudioOnlyMode,
                        dataSaver = settings.dataSaverEnabled
                    )

                    resolvedResult.fold(
                        onSuccess = { selection ->
                            // Extraction can finish before the asynchronous MediaSession connection.
                            // Wait here instead of silently dropping the selected media item.
                            try {
                                val controller = withTimeout(20_000) {
                                    Futures.nonCancellationPropagating(checkNotNull(controllerFuture) {
                                        "MediaSession connection was not started"
                                    }).await()
                                }
                                bindController(controller)
                            } catch (cancelled: CancellationException) {
                                if (!kotlin.coroutines.coroutineContext.isActive) throw cancelled
                                SourceDiagnostics.failure("session.await", cancelled)
                                _playbackState.update { it.copy(isLoadingMetadata = false, isBuffering = false,
                                    error = SourceError.PlaybackFailed("Kết nối dịch vụ phát media quá thời gian chờ.")) }
                                return@launch
                            } catch (error: Exception) {
                                SourceDiagnostics.failure("session.await", error)
                                _playbackState.update { it.copy(isLoadingMetadata = false, isBuffering = false,
                                    error = SourceError.PlaybackFailed("Không thể kết nối dịch vụ phát media.")) }
                                return@launch
                            }
                            SourceDiagnostics.event("stream.resolved", "merged=${selection.isMergedAdaptive} audioOnly=${selection.isAudioOnly} mime=${selection.mimeType}")
                            val updatedVideo = video.copy(
                                title = detail.title.ifBlank { video.title },
                                channelName = detail.channelName.ifBlank { video.channelName },
                                thumbnailUrl = detail.thumbnailUrl.ifBlank { video.thumbnailUrl },
                                durationSeconds = detail.durationSeconds.takeIf { it > 0 } ?: video.durationSeconds
                            )
                            val queueBeforeRecommendations = playbackRepository.activeQueue.value
                            val readyQueue = if (queueBeforeRecommendations.radioMode &&
                                queueBeforeRecommendations.currentItem?.id == updatedVideo.id
                            ) {
                                val recentAndPending = queueBeforeRecommendations.items
                                val fresh = MusicRecommendationRanker.rank(
                                    detail.relatedVideos,
                                    updatedVideo,
                                    currentDescription = detail.description,
                                    forceMusic = true
                                )
                                    .filterNot { suggestion ->
                                        recentAndPending.any { existing ->
                                            com.aeroplay.app.data.repository.SongIdentityResolver.likelySameSong(
                                                existing.title, suggestion.title
                                            )
                                        }
                                    }
                                val played = recentAndPending.take(queueBeforeRecommendations.currentIndex + 1).takeLast(12)
                                val pending = recentAndPending.drop(queueBeforeRecommendations.currentIndex + 1)
                                val compacted = (played + pending).distinctBy { "${it.sourceType.name}:${it.id}" }
                                val updated = queueBeforeRecommendations.copy(
                                    items = (compacted + fresh).take(80),
                                    currentIndex = (played.size - 1).coerceAtLeast(0)
                                )
                                playbackRepository.updateQueue(updated)
                                updated
                            } else queueBeforeRecommendations
                            _playbackState.update { state ->
                                state.copy(
                                    currentVideo = updatedVideo,
                                    currentDetail = detail,
                                    queue = readyQueue,
                                    isLoadingMetadata = false,
                                    availableVariants = detail.streamVariants,
                                    availableAudioTracks = detail.audioTracks,
                                    selectedStreamVariant = selection.selectedVariant,
                                    selectedAudioTrack = selection.selectedAudioTrack,
                                    isAudioOnlyMode = selection.isAudioOnly,
                                    playbackSpeed = settings.defaultPlaybackSpeed,
                                    error = null
                                )
                            }

                            applyResolvedStream(
                                video = updatedVideo,
                                detail = detail,
                                selection = selection,
                                startPositionMs = savedPosition,
                                autoPlay = true,
                                defaultSpeed = settings.defaultPlaybackSpeed
                            )
                            if (readyQueue.radioMode) {
                                requestRadioRecommendations(updatedVideo, detail)
                            }

                        },
                        onFailure = { err ->
                            val sourceErr = (err as? SourceErrorException)?.sourceError
                                ?: SourceError.NoStreamAvailable(err.message ?: "Không có stream khả dụng")
                            _playbackState.update {
                                it.copy(
                                    currentDetail = detail,
                                    isLoadingMetadata = false,
                                    isBuffering = false,
                                    isPlaying = false,
                                    error = sourceErr
                                )
                            }
                        }
                    )
                },
                onFailure = { err ->
                    val sourceErr = (err as? SourceErrorException)?.sourceError
                        ?: SourceError.UnknownError(err.message ?: "Không thể tải thông tin video")
                    _playbackState.update {
                        it.copy(
                            isLoadingMetadata = false,
                            isBuffering = false,
                            isPlaying = false,
                            error = sourceErr
                        )
                    }
                }
            )
        }
    }

    private companion object {
        const val COMPLETION_THRESHOLD = 0.8
        const val MAXIMUM_TRACKING_SPEED = 3L
        const val SEEK_TOLERANCE_MS = 750L
        const val MIN_PERSISTED_WATCH_DELTA_MS = 5_000L
    }

    private fun flushWatchedDuration(videoId: String, durationMs: Long, positionMs: Long) {
        val delta = pendingWatchedMs.coerceAtMost(10_000L)
        if (delta <= 0L) return
        pendingWatchedMs = 0L
        scope.launch { historyRepository.recordWatchedDuration(videoId, delta, durationMs, positionMs) }
    }

    private fun applyResolvedStream(
        video: VideoItem,
        detail: VideoDetail,
        selection: ResolvedStreamSelection,
        startPositionMs: Long,
        autoPlay: Boolean,
        defaultSpeed: Float? = null
    ) {
        currentStreamSelection = selection
        val controller = mediaController ?: return

        val extras = Bundle().apply {
            putString(AeroPlayMediaService.EXTRA_PRIMARY_MIME_TYPE, selection.mimeType)
            if (selection.isMergedAdaptive && !selection.secondaryAudioUrl.isNullOrBlank()) {
                putString(AeroPlayMediaService.EXTRA_SECONDARY_AUDIO_URL, selection.secondaryAudioUrl)
                putString(AeroPlayMediaService.EXTRA_SECONDARY_AUDIO_MIME_TYPE, selection.selectedAudioTrack?.formatMimeType)
            }
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(detail.title.ifBlank { video.title })
            .setArtist(detail.channelName.ifBlank { video.channelName })
            .setArtworkUri(
                detail.thumbnailUrl.ifBlank { video.thumbnailUrl }
                    .takeIf { it.isNotBlank() }
                    ?.let { Uri.parse(it) }
            )
            .setExtras(extras)
            .build()

        val requestMetadata = MediaItem.RequestMetadata.Builder()
            .setMediaUri(Uri.parse(selection.primaryUrl))
            .build()

        val mediaItemBuilder = MediaItem.Builder()
            .setMediaId(video.id)
            .setUri(Uri.parse(selection.primaryUrl))
            .setRequestMetadata(requestMetadata)
            .setMediaMetadata(metadata)

        if (!selection.mimeType.isNullOrBlank()) {
            mediaItemBuilder.setMimeType(selection.mimeType)
        }

        controller.setMediaItem(mediaItemBuilder.build(), startPositionMs.coerceAtLeast(0L))
        SourceDiagnostics.event("player.prepare", "mime=${selection.mimeType} merged=${selection.isMergedAdaptive}")
        defaultSpeed?.let { controller.setPlaybackSpeed(it) }
        controller.prepare()
        controller.playWhenReady = autoPlay
    }

    fun retryCurrentVideo() {
        val current = _playbackState.value.currentVideo ?: return
        val queue = _playbackState.value.queue
        val fullOpen = _playbackState.value.isFullPlayerOpen
        _playbackState.update { it.copy(currentDetail = null, error = null) }
        loadAndPlayVideo(
            video = current,
            queueItems = queue.items.ifEmpty { listOf(current) },
            queueTitle = queue.queueTitle,
            openFullPlayer = fullOpen,
            radioMode = queue.radioMode,
            autoRadioAfterEnd = queue.autoRadioAfterEnd
        )
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_ENDED) {
                controller.seekTo(0L)
            }
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        val target = positionMs.coerceAtLeast(0L)
        controller.seekTo(target)
        _playbackState.update { it.copy(currentPositionMs = target) }
        persistCurrentPosition()
    }

    fun skipDelta(deltaMs: Long) {
        val controller = mediaController ?: return
        val duration = controller.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = (controller.currentPosition + deltaMs).coerceIn(0L, duration)
        seekTo(target)
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        mediaController?.setPlaybackSpeed(clamped)
        _playbackState.update { it.copy(playbackSpeed = clamped) }
    }

    fun selectQualityVariant(variant: StreamVariant) {
        val state = _playbackState.value
        val detail = state.currentDetail ?: return
        val video = state.currentVideo ?: return
        val controller = mediaController ?: return
        val currentPos = controller.currentPosition.coerceAtLeast(0L)
        val wasPlaying = controller.isPlaying

        scope.launch {
            val settings = settingsRepository.getSettingsSnapshot()
            val resolved = streamResolver.resolveStream(
                detail = detail,
                preferredVariantId = variant.id,
                preferredQualityLabel = variant.resolutionLabel,
                isAudioOnly = false,
                dataSaver = settings.dataSaverEnabled
            ).getOrNull() ?: return@launch

            _playbackState.update {
                it.copy(
                    selectedStreamVariant = resolved.selectedVariant,
                    selectedAudioTrack = resolved.selectedAudioTrack,
                    isAudioOnlyMode = false,
                    error = null
                )
            }

            applyResolvedStream(
                video = video,
                detail = detail,
                selection = resolved,
                startPositionMs = currentPos,
                autoPlay = wasPlaying
            )
        }
    }

    fun setAudioOnlyMode(enabled: Boolean) {
        val state = _playbackState.value
        if (state.isAudioOnlyMode == enabled) return
        val detail = state.currentDetail ?: run {
            _playbackState.update { it.copy(isAudioOnlyMode = enabled) }
            return
        }
        val video = state.currentVideo ?: return
        val controller = mediaController ?: return
        val currentPos = controller.currentPosition.coerceAtLeast(0L)
        val wasPlaying = controller.isPlaying

        scope.launch {
            val settings = settingsRepository.getSettingsSnapshot()
            val resolved = streamResolver.resolveStream(
                detail = detail,
                preferredVariantId = state.selectedStreamVariant?.id,
                preferredQualityLabel = settings.defaultQualityLabel,
                isAudioOnly = enabled,
                dataSaver = settings.dataSaverEnabled
            ).getOrNull() ?: return@launch

            _playbackState.update {
                it.copy(
                    isAudioOnlyMode = enabled,
                    selectedStreamVariant = resolved.selectedVariant ?: it.selectedStreamVariant,
                    selectedAudioTrack = resolved.selectedAudioTrack ?: it.selectedAudioTrack,
                    error = null
                )
            }

            applyResolvedStream(
                video = video,
                detail = detail,
                selection = resolved,
                startPositionMs = currentPos,
                autoPlay = wasPlaying
            )
        }
    }

    fun playNext() {
        mediaController?.takeIf { CarMediaLibrary.summary(it.currentMediaItem) != null }?.let {
            it.seekToNextMediaItem()
            return
        }
        val nextVideo = playbackRepository.nextInQueue() ?: return
        val updatedQueue = playbackRepository.activeQueue.value
        loadAndPlayVideo(
            video = nextVideo,
            queueItems = updatedQueue.items,
            queueTitle = updatedQueue.queueTitle,
            openFullPlayer = _playbackState.value.isFullPlayerOpen,
            radioMode = updatedQueue.radioMode,
            autoRadioAfterEnd = updatedQueue.autoRadioAfterEnd
        )
    }

    fun playPrevious() {
        val controller = mediaController
        if (controller != null && CarMediaLibrary.summary(controller.currentMediaItem) != null) {
            controller.seekToPrevious()
            return
        }
        if (controller != null && controller.currentPosition > 5_000L) {
            seekTo(0L)
            return
        }
        val prevVideo = playbackRepository.previousInQueue() ?: return
        val updatedQueue = playbackRepository.activeQueue.value
        loadAndPlayVideo(
            video = prevVideo,
            queueItems = updatedQueue.items,
            queueTitle = updatedQueue.queueTitle,
            openFullPlayer = _playbackState.value.isFullPlayerOpen,
            radioMode = updatedQueue.radioMode,
            autoRadioAfterEnd = updatedQueue.autoRadioAfterEnd
        )
    }

    private fun continuePlaylistWithRadio(queue: PlaybackQueue) {
        if (queue.currentIndex != playbackRepository.activeQueue.value.currentIndex) return
        val current = queue.currentItem ?: return
        val detail = _playbackState.value.currentDetail ?: return
        if (radioRecommendationJob?.isActive == true) return
        radioRecommendationJob = scope.launch {
            val recommendations = musicSuggestionRepository.suggestions(current, detail.description, detail.relatedVideos)
            val active = playbackRepository.activeQueue.value
            if (!active.autoRadioAfterEnd || active.currentItem?.id != current.id) return@launch
            val existing = active.items
            val fresh = recommendations.filterNot { suggestion ->
                existing.any { item ->
                    item.id == suggestion.id || com.aeroplay.app.data.repository.SongIdentityResolver.likelySameSong(
                        item.title, suggestion.title
                    )
                }
            }.take((80 - existing.size).coerceAtLeast(0))
            if (fresh.isEmpty()) return@launch
            val continued = active.copy(
                items = existing + fresh,
                radioMode = true,
                autoRadioAfterEnd = false
            )
            playbackRepository.updateQueue(continued)
            _playbackState.update { it.copy(queue = continued) }
            if (mediaController?.playbackState == Player.STATE_ENDED && autoPlaySimilarMusic) playNext()
        }
    }

    private fun requestRadioRecommendations(video: VideoItem?, detail: VideoDetail?) {
        if (video == null || detail == null || !video.sourceType.isExperimental) return
        if (radioRecommendationJob?.isActive == true) return
        radioRecommendationJob = scope.launch {
            val suggestions = musicSuggestionRepository.suggestions(video, detail.description, detail.relatedVideos)
            val active = playbackRepository.activeQueue.value
            if (!active.radioMode || active.currentItem?.id != video.id) return@launch
            val existing = active.items
            val fresh = suggestions.filterNot { suggestion ->
                existing.any { item ->
                    item.id == suggestion.id || com.aeroplay.app.data.repository.SongIdentityResolver.likelySameSong(
                        item.title, suggestion.title
                    )
                }
            }.take((80 - existing.size).coerceAtLeast(0))
            if (fresh.isEmpty()) return@launch
            val updated = active.copy(items = existing + fresh)
            playbackRepository.updateQueue(updated)
            _playbackState.update { state ->
                if (state.currentVideo?.id == video.id) state.copy(queue = updated) else state
            }
            if (mediaController?.playbackState == Player.STATE_ENDED && autoPlaySimilarMusic) playNext()
        }
    }

    fun minimizeToMiniPlayer() {
        if (_playbackState.value.currentVideo != null) {
            _playbackState.update {
                it.copy(
                    isFullPlayerOpen = false,
                    isMiniPlayerVisible = true
                )
            }
        }
    }

    fun expandToFullPlayer() {
        if (_playbackState.value.currentVideo != null) {
            _playbackState.update {
                it.copy(
                    isFullPlayerOpen = true,
                    isMiniPlayerVisible = false
                )
            }
        }
    }

    fun onPipModeChanged(isInPip: Boolean) {
        _playbackState.update {
            it.copy(
                isInPipMode = isInPip,
                isFullPlayerOpen = if (isInPip) true else it.isFullPlayerOpen,
                isMiniPlayerVisible = if (isInPip) false else it.isMiniPlayerVisible
            )
        }
    }

    fun onAppBackgrounded() {
        scope.launch {
            val settings = settingsRepository.getSettingsSnapshot()
            persistCurrentPosition()
            if (!settings.backgroundPlaybackEnabled && !_playbackState.value.isInPipMode) {
                mediaController?.pause()
            } else if (settings.autoAudioOnlyWhenBackground &&
                _playbackState.value.isPlaying &&
                !_playbackState.value.isInPipMode &&
                !_playbackState.value.isAudioOnlyMode
            ) {
                setAudioOnlyMode(true)
            }
        }
    }

    fun dismissPlayer() {
        persistCurrentPosition()
        extractionJob?.cancel()
        mediaController?.stop()
        mediaController?.clearMediaItems()
        _playbackState.update {
            PlaybackState()
        }
    }

    private fun persistCurrentPosition() {
        val state = _playbackState.value
        val video = state.currentVideo ?: return
        val pos = mediaController?.currentPosition?.coerceAtLeast(0L) ?: state.currentPositionMs
        val dur = mediaController?.duration?.takeIf { it > 0L }
            ?: (state.currentDetail?.durationSeconds?.times(1000L) ?: state.durationMs)
        if (dur > 0L) {
            scope.launch {
                historyRepository.updatePositionOnly(video.id, pos, dur)
            }
        }
    }

    /** Android Auto and the phone share this session; don't start a second playback or a phone radio queue. */
    private fun synchronizeCarPlayback(controller: MediaController) {
        val video = CarMediaLibrary.summary(controller.currentMediaItem) ?: return
        val old = _playbackState.value
        if (!carAudioOverride) {
            audioOnlyBeforeCar = old.isAudioOnlyMode
            carAudioOverride = true
        }
        val changed = old.currentVideo?.id != video.id || old.currentVideo?.sourceType != video.sourceType
        if (changed) {
            old.currentVideo?.let { flushWatchedDuration(it.id, old.durationMs, old.currentPositionMs) }
            extractionJob?.cancel()
            radioRecommendationJob?.cancel()
            currentStreamSelection = null
            pendingWatchedMs = 0
            sessionWatchedMs = 0
        }
        val items = (0 until controller.mediaItemCount).mapNotNull { CarMediaLibrary.summary(controller.getMediaItemAt(it)) }
        val queue = PlaybackQueue(items, controller.currentMediaItemIndex, "Android Auto")
        playbackRepository.updateQueue(queue)
        _playbackState.update {
            it.copy(currentVideo = video, currentDetail = null, queue = queue, isAudioOnlyMode = true,
                isLoadingMetadata = false, isPlaying = controller.isPlaying,
                isBuffering = controller.playbackState == Player.STATE_BUFFERING,
                isFullPlayerOpen = if (changed) false else it.isFullPlayerOpen,
                isMiniPlayerVisible = if (changed) true else it.isMiniPlayerVisible,
                error = if (changed) null else it.error)
        }
    }
}
