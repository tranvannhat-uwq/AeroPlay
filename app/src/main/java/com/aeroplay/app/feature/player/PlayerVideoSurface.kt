package com.aeroplay.app.feature.player

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.aeroplay.app.R

/** Transfers the existing player to the visible surface without reloading the media. */
@OptIn(UnstableApi::class)
class PlayerVideoOutput {
    private var target: PlayerView? = null

    fun attach(player: Player?, view: PlayerView) {
        if (target === view && view.player === player) return
        val previous = target
        if (previous?.player === player && player != null) {
            PlayerView.switchTargetView(player, previous, view)
        } else {
            view.player = player
            if (previous !== view) previous?.player = null
        }
        target = view
    }

    fun detach(view: PlayerView) {
        if (target === view) target = null
        view.player = null
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerVideoSurface(
    player: Player?,
    output: PlayerVideoOutput,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { LayoutInflater.from(it).inflate(R.layout.player_texture_view, null) as PlayerView },
        modifier = modifier,
        update = { if (active) output.attach(player, it) },
        onRelease = output::detach
    )
}
