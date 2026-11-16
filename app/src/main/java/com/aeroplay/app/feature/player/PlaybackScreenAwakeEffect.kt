package com.aeroplay.app.feature.player

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player

/** Keep the visible video awake without changing the device's screen timeout or acquiring a wake lock. */
@Composable
internal fun PlaybackScreenAwakeEffect(activity: Activity?, player: Player?, enabled: Boolean) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(activity, player, lifecycleOwner, enabled) {
        val window = activity?.window
        val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val previouslySet = window != null && window.attributes.flags and flag != 0

        fun update() {
            if (window == null || !enabled) return
            val playingOrBuffering = player?.playWhenReady == true &&
                (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
            if (playingOrBuffering && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                window.addFlags(flag)
            } else if (!previouslySet) {
                window.clearFlags(flag)
            }
        }

        val playerListener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = update()
        }
        val lifecycleObserver = LifecycleEventObserver { _, _ -> update() }
        if (enabled) {
            player?.addListener(playerListener)
            lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
            update()
        }
        onDispose {
            player?.removeListener(playerListener)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            if (enabled && !previouslySet) window?.clearFlags(flag)
        }
    }
}
