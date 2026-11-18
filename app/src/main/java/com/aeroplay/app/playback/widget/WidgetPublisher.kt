package com.aeroplay.app.playback.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil.imageLoader
import coil.request.ImageRequest
import com.aeroplay.app.R
import com.aeroplay.app.core.model.PlaybackQueue
import com.aeroplay.app.core.model.PlaybackState
import com.aeroplay.app.data.repository.HistoryRepository
import com.aeroplay.app.playback.car.CarMediaLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
internal class WidgetPublisher(
    private val context: Context,
    private val player: Player,
    private val scope: CoroutineScope,
    private val history: HistoryRepository,
    private val queue: () -> PlaybackQueue,
    private val playback: () -> PlaybackState?
) {
    private inner class Cover(private val pixels: Int) {
        var bitmap: Bitmap? = null
            private set
        private var url: String? = null
        private var job: Job? = null

        fun select(next: String?) {
            if (next == url) return
            url = next
            job?.cancel()
            bitmap = null
            if (next == null) return
            job = scope.launch {
                val drawable = context.imageLoader.execute(ImageRequest.Builder(context)
                    .data(next)
                    .size(pixels)
                    .allowHardware(false)
                    .build()).drawable
                if (url != next) return@launch
                bitmap = drawable?.let { image ->
                    val ratio = image.intrinsicWidth.toFloat() / image.intrinsicHeight.coerceAtLeast(1)
                    val width = if (ratio >= 1) pixels else (pixels * ratio).toInt().coerceAtLeast(1)
                    val height = if (ratio <= 1) pixels else (pixels / ratio).toInt().coerceAtLeast(1)
                    image.toBitmap(width, height, Bitmap.Config.ARGB_8888)
                }
                refresh()
            }
        }
    }

    private val artwork = Cover(256)
    private val nextArtwork = Cover(84)
    private var favoriteId: String? = null
    private var favorite = false
    private var favoriteJob: Job? = null
    private var progressJob: Job? = null

    fun refresh() {
        val installed = PlaybackWidget.hasWidgets(context)
        if (installed && player.isPlaying) {
            if (progressJob?.isActive != true) {
                progressJob = scope.launch {
                    while (isActive) {
                        delay(30_000)
                        refresh()
                    }
                }
            }
        } else {
            progressJob?.cancel()
            progressJob = null
        }
        val media = player.currentMediaItem
        val car = CarMediaLibrary.summary(media)
        val state = if (media != null && car == null) playback()?.takeIf { it.currentVideo?.id == media.mediaId } else null
        val video = if (media != null) car ?: state?.currentVideo else null
        val activeQueue = queue()
        val next = if (car == null && activeQueue.currentItem?.id == media?.mediaId && activeQueue.hasNext) {
            activeQueue.items.getOrNull(activeQueue.currentIndex + 1)
        } else if (car != null && player.hasNextMediaItem()) {
            CarMediaLibrary.summary(player.getMediaItemAt(player.nextMediaItemIndex))
        } else null
        val metadata = player.mediaMetadata
        artwork.select(metadata.artworkUri?.toString()?.takeIf { installed && media != null })
        nextArtwork.select(next?.thumbnailUrl?.takeIf { installed && it.isNotBlank() })
        val nextFavorite = video?.id?.takeIf { installed }
        if (favoriteId != nextFavorite) {
            favoriteId = nextFavorite
            favoriteJob?.cancel()
            favorite = false
            favoriteJob = nextFavorite?.let { id ->
                scope.launch {
                    history.observeIsFavorite(id).collect {
                        if (favorite != it) {
                            favorite = it
                            refresh()
                        }
                    }
                }
            }
        }
        if (media == null) {
            PlaybackWidget.publish(context, WidgetPlayback())
            return
        }
        val duration = player.duration.takeIf { it > 0 && !player.isCurrentMediaItemLive }
        val quality = if (state?.isAudioOnlyMode == true) {
            state.selectedAudioTrack?.averageBitrateKbps?.takeIf { it > 0 }?.let { "$it kbps" }
        } else state?.selectedStreamVariant?.height?.takeIf { it > 0 }?.let { "${it}p" }
        PlaybackWidget.publish(context, WidgetPlayback(
            title = metadata.title?.toString()?.takeIf(String::isNotBlank) ?: video?.title ?: context.getString(R.string.app_name),
            artist = metadata.artist?.toString().orEmpty().ifBlank { video?.channelName.orEmpty() },
            video = video,
            artwork = artwork.bitmap,
            positionMs = player.currentPosition.coerceAtLeast(0) / 1000 * 1000,
            durationMs = duration,
            pauseButton = player.playWhenReady && player.playbackState in listOf(Player.STATE_READY, Player.STATE_BUFFERING),
            status = when {
                player.playerError != null -> R.string.playback_widget_unavailable
                player.playbackState == Player.STATE_BUFFERING -> R.string.playback_widget_loading
                else -> null
            },
            hasPrevious = player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM),
            hasNext = player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM),
            next = next,
            nextArtwork = nextArtwork.bitmap,
            quality = quality,
            repeat = player.repeatMode == Player.REPEAT_MODE_ONE,
            canRepeat = !player.isCurrentMediaItemLive && player.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE),
            favorite = favorite
        ))
    }
}
