package com.aeroplay.app.playback.service

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.LibraryResult
import androidx.media3.session.SessionError
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.aeroplay.app.MainActivity
import com.aeroplay.app.core.network.SourceDiagnostics
import com.aeroplay.app.playback.car.CarMediaLibrary
import com.aeroplay.app.playback.car.CarBrowseIds
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.data.repository.HistoryRepository
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.future
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * One MediaLibrarySession powers phone playback, Android Auto's audio library,
 * lock-screen controls and Bluetooth headset controls.
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class AeroPlayMediaService : MediaLibraryService() {

    companion object {
        const val EXTRA_SECONDARY_AUDIO_URL = "com.aeroplay.app.EXTRA_SECONDARY_AUDIO_URL"
        const val EXTRA_PRIMARY_MIME_TYPE = "com.aeroplay.app.EXTRA_PRIMARY_MIME_TYPE"
        const val EXTRA_SECONDARY_AUDIO_MIME_TYPE = "com.aeroplay.app.EXTRA_SECONDARY_AUDIO_MIME_TYPE"
        const val ACTION_SET_NOISY_HANDLING = "com.aeroplay.app.ACTION_SET_NOISY_HANDLING"
        const val EXTRA_NOISY_ENABLED = "com.aeroplay.app.EXTRA_NOISY_ENABLED"
    }

    @Inject
    lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var carLibrary: CarMediaLibrary
    @Inject lateinit var historyRepository: HistoryRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var carGeneration = 0L
    private val remainingCarTracks = ArrayDeque<VideoItem>()
    private var prefetchJob: Job? = null
    private var carProgressJob: Job? = null

    private var player: ExoPlayer? = null
    private var mediaSession: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()

        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
        val dataSourceFactory = DefaultDataSource.Factory(this, okHttpDataSourceFactory)
        val defaultMediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        // Custom MediaSource.Factory that supports MergingMediaSource when a separate audio URL is provided
        val adaptiveMergingMediaSourceFactory = object : MediaSource.Factory {
            override fun setDrmSessionManagerProvider(
                drmSessionManagerProvider: androidx.media3.exoplayer.drm.DrmSessionManagerProvider
            ): MediaSource.Factory {
                defaultMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
                return this
            }

            override fun setLoadErrorHandlingPolicy(
                loadErrorHandlingPolicy: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
            ): MediaSource.Factory {
                defaultMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                return this
            }

            override fun getSupportedTypes(): IntArray = defaultMediaSourceFactory.supportedTypes

            override fun createMediaSource(mediaItem: MediaItem): MediaSource {
                val secondaryAudioUrl = mediaItem.mediaMetadata.extras
                    ?.getString(EXTRA_SECONDARY_AUDIO_URL)
                    ?.takeIf { it.isNotBlank() }

                return if (secondaryAudioUrl != null) {
                    val videoSource = defaultMediaSourceFactory.createMediaSource(mediaItem)
                    val audioItem = MediaItem.Builder().setUri(secondaryAudioUrl)
                        .setMimeType(mediaItem.mediaMetadata.extras?.getString(EXTRA_SECONDARY_AUDIO_MIME_TYPE))
                        .build()
                    val audioSource = defaultMediaSourceFactory.createMediaSource(audioItem)
                    MergingMediaSource(true, videoSource, audioSource)
                } else {
                    defaultMediaSourceFactory.createMediaSource(mediaItem)
                }
            }
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(adaptiveMergingMediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val video = CarMediaLibrary.summary(exoPlayer.currentMediaItem) ?: return
                if (!isPlaying && exoPlayer.duration > 0) serviceScope.launch {
                    historyRepository.updatePositionOnly(video.id, exoPlayer.currentPosition, exoPlayer.duration)
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val carVideo = CarMediaLibrary.summary(mediaItem)
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, carVideo != null).build()
                carProgressJob?.cancel()
                if (carVideo != null) {
                    carProgressJob = serviceScope.launch {
                        var recorded = false
                        while (isActive) {
                            if (exoPlayer.isPlaying) {
                                val duration = exoPlayer.duration.coerceAtLeast(0L)
                                if (!recorded) {
                                    historyRepository.recordWatchAndPosition(carVideo, exoPlayer.currentPosition, duration)
                                    recorded = true
                                } else historyRepository.updatePositionOnly(carVideo.id, exoPlayer.currentPosition, duration)
                            }
                            delay(5_000)
                        }
                    }
                    prefetchNextCarTrack()
                } else {
                    carGeneration++
                    remainingCarTracks.clear()
                    prefetchJob?.cancel()
                }
            }

            override fun onRenderedFirstFrame() {
                SourceDiagnostics.event("player.rendered", "firstFrame=true")
            }

            override fun onPlayerError(error: PlaybackException) {
                SourceDiagnostics.failure("service.${error.errorCodeName}", error)
            }
        })

        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build())

        mediaSession = MediaLibrarySession.Builder(this, exoPlayer, object : MediaLibrarySession.Callback {
                override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?) =
                    Futures.immediateFuture(LibraryResult.ofItem(carLibrary.root(), params))

                override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> = libraryFuture {
                    LibraryResult.ofItem(carLibrary.item(mediaId), null)
                }

                override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = libraryFuture {
                    LibraryResult.ofItemList(CarBrowseIds.page(carLibrary.children(parentId), page, pageSize), params)
                }

                override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> = libraryFuture {
                    val items = carLibrary.searchItems(query)
                    session.notifySearchResultChanged(browser, query, items.size, params)
                    LibraryResult.ofVoid(params)
                }

                override fun onGetSearchResult(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = libraryFuture {
                    LibraryResult.ofItemList(CarBrowseIds.page(carLibrary.searchResults(query), page, pageSize), params)
                }
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val ownApp = controller.uid == applicationInfo.uid
                    val knownHost = controller.packageName in setOf("com.google.android.projection.gearhead", "com.google.android.googlequicksearchbox") &&
                        packageManager.getPackagesForUid(controller.uid)?.contains(controller.packageName) == true
                    if (!ownApp && !controller.isTrusted && !knownHost) return MediaSession.ConnectionResult.reject()
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                        .buildUpon()
                        .apply { if (ownApp) add(SessionCommand(ACTION_SET_NOISY_HANDLING, Bundle.EMPTY)) }
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (controller.uid == applicationInfo.uid && customCommand.customAction == ACTION_SET_NOISY_HANDLING) {
                        val enabled = args.getBoolean(EXTRA_NOISY_ENABLED, true)
                        player?.setHandleAudioBecomingNoisy(enabled)
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }

                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> {
                    val carRequest = mediaItems.firstOrNull()?.let { CarBrowseIds.decodeTrack(it.mediaId) != null ||
                        it.requestMetadata.searchQuery != null || (controller.uid != applicationInfo.uid && it.mediaId.isBlank()) } == true
                    if (carRequest) return serviceScope.future {
                        withTimeout(25_000) {
                            carGeneration++
                            val generation = carGeneration
                            prefetchJob?.cancel()
                            remainingCarTracks.clear()
                            val (video, tail) = carLibrary.selection(mediaItems.first())
                            val resolved = carLibrary.resolve(video)
                            check(generation == carGeneration) { "Playback selection changed" }
                            remainingCarTracks.addAll(tail)
                            mutableListOf(resolved)
                        }
                    }
                    if (controller.uid != applicationInfo.uid) return Futures.immediateFailedFuture(IllegalArgumentException("Unsupported media ID"))
                    val resolvedItems = mediaItems.map { item ->
                        val uri = item.requestMetadata.mediaUri
                        if (uri != null) {
                            // LocalConfiguration is not transported to a session. Preserve the
                            // resolved MIME type explicitly for URLs without a file extension.
                            item.buildUpon().setUri(uri)
                                .setMimeType(item.mediaMetadata.extras?.getString(EXTRA_PRIMARY_MIME_TYPE))
                                .build()
                        } else {
                            item
                        }
                    }.toMutableList()
                    return Futures.immediateFuture(resolvedItems)
                }

                override fun onSetMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val selected = mediaItems.getOrNull(startIndex.takeIf { it >= 0 } ?: 0)
                    if (selected != null && (CarBrowseIds.decodeTrack(selected.mediaId) != null ||
                        selected.requestMetadata.searchQuery != null || controller.uid != applicationInfo.uid)) {
                        // We resolve the selected item now and prepare its sibling queue lazily.
                        return Futures.transform(onAddMediaItems(session, controller, mutableListOf(selected)),
                            { resolved -> MediaSession.MediaItemsWithStartPosition(checkNotNull(resolved), 0, startPositionMs) },
                            com.google.common.util.concurrent.MoreExecutors.directExecutor())
                    }
                    return super.onSetMediaItems(session, controller, mediaItems, startIndex, startPositionMs)
                }

                override fun onPlaybackResumption(session: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future {
                    withTimeout(25_000) {
                        carGeneration++
                        prefetchJob?.cancel()
                        remainingCarTracks.clear()
                        val video = requireNotNull(carLibrary.resumption()) { "Thư viện trống. Phát hoặc lưu bài trên điện thoại trước." }
                        val item = carLibrary.resolve(video)
                        MediaSession.MediaItemsWithStartPosition(listOf(item), 0, historyRepository.getSavedPosition(video.id)?.positionMs ?: 0L)
                    }
                }
            })
            .setSessionActivity(sessionActivityPendingIntent)
            .setLibraryErrorReplicationMode(MediaLibrarySession.LIBRARY_ERROR_REPLICATION_MODE_NON_FATAL)
            .build()

        player = exoPlayer
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = player
        if (currentPlayer == null ||
            !currentPlayer.playWhenReady ||
            currentPlayer.mediaItemCount == 0 ||
            currentPlayer.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    private fun <T : Any> libraryFuture(block: suspend () -> LibraryResult<T>): ListenableFuture<LibraryResult<T>> = serviceScope.future<LibraryResult<T>> {
        try { return@future withTimeout<LibraryResult<T>>(25_000) { block() } }
        catch (error: TimeoutCancellationException) { return@future LibraryResult.ofError<T>(SessionError.ERROR_IO) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            SourceDiagnostics.failure("car.library", error)
            return@future LibraryResult.ofError<T>(SessionError.ERROR_BAD_VALUE)
        }
    }

    private fun prefetchNextCarTrack() {
        val currentPlayer = player ?: return
        if (prefetchJob?.isActive == true || remainingCarTracks.isEmpty() ||
            currentPlayer.currentMediaItemIndex < currentPlayer.mediaItemCount - 1) return
        val generation = carGeneration
        prefetchJob = serviceScope.launch {
            while (remainingCarTracks.isNotEmpty() && generation == carGeneration) {
                val next = remainingCarTracks.removeFirst()
                try {
                    val item = withTimeout(20_000) { carLibrary.resolve(next) }
                    if (generation == carGeneration && CarMediaLibrary.summary(currentPlayer.currentMediaItem) != null) currentPlayer.addMediaItem(item)
                    break
                } catch (timeout: TimeoutCancellationException) { SourceDiagnostics.failure("car.prefetch", timeout) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { SourceDiagnostics.failure("car.prefetch", error) }
            }
        }
    }
}
