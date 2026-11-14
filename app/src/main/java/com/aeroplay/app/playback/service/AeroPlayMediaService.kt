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
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.aeroplay.app.MainActivity
import com.aeroplay.app.core.network.SourceDiagnostics
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Unified MediaSessionService powering foreground video playback, background audio playback,
 * lock-screen controls, and Bluetooth headset controls (Section V.6 & VI).
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class AeroPlayMediaService : MediaSessionService() {

    companion object {
        const val EXTRA_SECONDARY_AUDIO_URL = "com.aeroplay.app.EXTRA_SECONDARY_AUDIO_URL"
        const val EXTRA_PRIMARY_MIME_TYPE = "com.aeroplay.app.EXTRA_PRIMARY_MIME_TYPE"
        const val EXTRA_SECONDARY_AUDIO_MIME_TYPE = "com.aeroplay.app.EXTRA_SECONDARY_AUDIO_MIME_TYPE"
        const val ACTION_SET_NOISY_HANDLING = "com.aeroplay.app.ACTION_SET_NOISY_HANDLING"
        const val EXTRA_NOISY_ENABLED = "com.aeroplay.app.EXTRA_NOISY_ENABLED"
    }

    @Inject
    lateinit var okHttpClient: OkHttpClient

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

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
            .build()

        exoPlayer.addListener(object : Player.Listener {
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

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                        .buildUpon()
                        .add(SessionCommand(ACTION_SET_NOISY_HANDLING, Bundle.EMPTY))
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
                    if (customCommand.customAction == ACTION_SET_NOISY_HANDLING) {
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
            })
            .build()

        player = exoPlayer
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
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
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }
}
