package com.aeroplay.app.playback.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.media3.common.Player
import com.aeroplay.app.MainActivity
import com.aeroplay.app.R
import com.aeroplay.app.playback.controller.PlaybackControllerManager
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackWidget : AppWidgetProvider() {
    @Inject lateinit var playback: Lazy<PlaybackControllerManager>

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        manager.updateAppWidget(widgetIds, render(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PREVIOUS -> if (snapshot.hasPrevious) playback.get().playPreviousTrack()
            ACTION_TOGGLE -> if (snapshot.title != null) playback.get().togglePlayPause()
            ACTION_NEXT -> if (snapshot.hasNext) playback.get().playNext()
        }
    }

    private data class Snapshot(
        val title: String? = null,
        val playing: Boolean = false,
        val hasPrevious: Boolean = false,
        val hasNext: Boolean = false
    )

    companion object {
        private const val ACTION_PREVIOUS = "com.aeroplay.app.widget.PREVIOUS"
        private const val ACTION_TOGGLE = "com.aeroplay.app.widget.TOGGLE"
        private const val ACTION_NEXT = "com.aeroplay.app.widget.NEXT"
        private val intentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        private var snapshot = Snapshot()

        fun updateFromPlayer(context: Context, player: Player) {
            val title = player.currentMediaItem?.let {
                player.mediaMetadata.title?.toString()?.takeIf(String::isNotBlank) ?: context.getString(R.string.app_name)
            }
            publish(context, Snapshot(
                title = title,
                playing = title != null && player.playWhenReady &&
                    player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE,
                hasPrevious = title != null && player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM),
                hasNext = title != null && player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            ))
        }

        fun clear(context: Context) = publish(context, Snapshot())

        private fun publish(context: Context, next: Snapshot) {
            if (snapshot == next) return
            snapshot = next
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlaybackWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, render(context))
        }

        private fun render(context: Context): RemoteViews {
            val state = snapshot
            val openApp = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                intentFlags
            )
            return RemoteViews(context.packageName, R.layout.playback_widget).apply {
                setTextViewText(R.id.widget_track, state.title ?: context.getString(R.string.playback_widget_idle))
                setImageViewResource(R.id.widget_toggle, if (state.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
                setContentDescription(R.id.widget_toggle, context.getString(
                    if (state.playing) R.string.playback_widget_pause else R.string.playback_widget_play
                ))
                setOnClickPendingIntent(R.id.widget_heading, openApp)
                setOnClickPendingIntent(R.id.widget_toggle, if (state.title == null) openApp else command(context, ACTION_TOGGLE))
                setOnClickPendingIntent(R.id.widget_previous, if (state.hasPrevious) command(context, ACTION_PREVIOUS) else null)
                setOnClickPendingIntent(R.id.widget_next, if (state.hasNext) command(context, ACTION_NEXT) else null)
                setInt(R.id.widget_previous, "setImageAlpha", if (state.hasPrevious) 255 else 88)
                setInt(R.id.widget_next, "setImageAlpha", if (state.hasNext) 255 else 88)
            }
        }

        private fun command(context: Context, action: String): PendingIntent =
            PendingIntent.getBroadcast(context, 0, Intent(context, PlaybackWidget::class.java).setAction(action), intentFlags)
    }
}
