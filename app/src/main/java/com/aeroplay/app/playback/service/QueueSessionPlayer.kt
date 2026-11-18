package com.aeroplay.app.playback.service

import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.aeroplay.app.core.model.PlaybackQueue
import com.aeroplay.app.playback.car.CarMediaLibrary
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
internal class QueueSessionPlayer(
    private val engine: Player,
    private val queue: () -> PlaybackQueue,
    private val skipNext: () -> Unit,
    private val skipPrevious: () -> Unit
) : ForwardingSimpleBasePlayer(engine) {
    private val handler = Handler(engine.applicationLooper)

    fun onQueueChanged() = invalidateState()

    override fun getState(): State {
        val state = super.getState()
        if (ownsNativeQueue()) return state

        val current = queue()
        val commands = state.availableCommands.buildUpon()
            .remove(Player.COMMAND_SEEK_TO_NEXT)
            .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
            .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        if (engine.currentMediaItem != null && current.hasNext) {
            commands.add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        }
        if (engine.currentMediaItem != null && current.hasPrevious) {
            commands.add(Player.COMMAND_SEEK_TO_PREVIOUS).add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        }
        return state.buildUpon().setAvailableCommands(commands.build()).build()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (ownsNativeQueue()) return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        val action = when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> skipNext
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> skipPrevious
            else -> return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }
        // A skip can issue commands back through the session. Run it after this seek dispatch.
        val item = engine.currentMediaItem
        val current = queue().currentItem
        handler.post {
            if (item != null && engine.currentMediaItem === item && queue().currentItem == current) action()
        }
        return Futures.immediateVoidFuture()
    }

    private fun ownsNativeQueue(): Boolean = CarMediaLibrary.summary(engine.currentMediaItem) != null
}
