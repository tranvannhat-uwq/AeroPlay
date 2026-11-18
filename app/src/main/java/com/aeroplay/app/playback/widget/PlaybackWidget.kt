package com.aeroplay.app.playback.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.aeroplay.app.MainActivity
import com.aeroplay.app.R
import com.aeroplay.app.core.common.FormatUtils
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.data.repository.HistoryRepository
import com.aeroplay.app.playback.controller.PlaybackControllerManager
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

internal enum class WidgetSize {
    QUICK, WIDE, EXPANDED;

    companion object {
        fun fromDp(width: Int, height: Int): WidgetSize = when {
            width >= 240 && height >= 255 -> EXPANDED
            width >= 211 && height >= 122 -> WIDE
            else -> QUICK
        }
    }
}

internal data class WidgetLayoutPlan(val width: Int, val height: Int, val fontScale: Float) {
    val size = WidgetSize.fromDp(width, height)
    private val fontExtra = ((fontScale - 1f).coerceAtLeast(0f) * 40).toInt()
    val secondaryControls = size == WidgetSize.EXPANDED && width >= 280
    val heading = size != WidgetSize.WIDE || height >= 158 + fontExtra
    val artist = when (size) {
        WidgetSize.QUICK -> false
        WidgetSize.WIDE -> height >= 143 + fontExtra
        WidgetSize.EXPANDED -> height >= 248 + fontExtra / 2
    }
    val times = height >= (if (size == WidgetSize.EXPANDED) 267 else 165) + fontExtra
    val nextItem = height >= 245 + fontExtra
    val nextArtist = height >= 302 + fontExtra
    val coverDp = when (size) {
        WidgetSize.QUICK -> minOf(144, height - 38, width - 24).coerceAtLeast(55)
        WidgetSize.WIDE ->
            minOf(88, width / 3, height - (if (heading) 103 else 74) - fontExtra).coerceAtLeast(42)
        WidgetSize.EXPANDED -> {
            val chrome = 189 + (if (times) 19 else 0) - (if (nextItem) 0 else 34)
            minOf(126, width * 39 / 100, height - chrome - fontExtra).coerceAtLeast(58)
        }
    }
    val titleLines = if (size == WidgetSize.EXPANDED && coverDp >= 94 && height >= 310 + fontExtra) 2 else 1
    val quality = size == WidgetSize.EXPANDED && coverDp >= 88 && height >= 310 + fontExtra
    val expandedQueueLabel = width >= 266 + fontExtra
}

internal data class WidgetPlayback(
    val title: String? = null,
    val artist: String = "",
    val video: VideoItem? = null,
    val artwork: Bitmap? = null,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
    val pauseButton: Boolean = false,
    val status: Int? = null,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val next: VideoItem? = null,
    val nextArtwork: Bitmap? = null,
    val quality: String? = null,
    val repeat: Boolean = false,
    val canRepeat: Boolean = false,
    val favorite: Boolean = false
)

@AndroidEntryPoint
class PlaybackWidget : AppWidgetProvider() {
    @Inject lateinit var playback: Lazy<PlaybackControllerManager>
    @Inject lateinit var history: Lazy<HistoryRepository>

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        widgetIds.forEach { updateOne(context, manager, it, manager.getAppWidgetOptions(it)) }
        hostChanges.value++
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        updateOne(context, manager, id, options)
        hostChanges.value++
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        hostChanges.value++
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PREVIOUS -> if (snapshot.hasPrevious) playback.get().playPreviousTrack()
            ACTION_TOGGLE -> if (snapshot.title != null) playback.get().togglePlayPause()
            ACTION_NEXT -> if (snapshot.hasNext) playback.get().playNext()
            ACTION_REPEAT -> if (snapshot.canRepeat) playback.get().setRepeatOne(!snapshot.repeat)
            ACTION_FAVORITE -> {
                val video = snapshot.video ?: return
                val pending = goAsync()
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        history.get().toggleFavorite(video)
                    } catch (error: Exception) {
                        android.util.Log.w("PlaybackWidget", "Could not save favorite", error)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "com.aeroplay.app.widget.OPEN_PLAYER"
        const val ACTION_OPEN_QUEUE = "com.aeroplay.app.widget.OPEN_QUEUE"
        private const val ACTION_PREVIOUS = "com.aeroplay.app.widget.PREVIOUS"
        private const val ACTION_TOGGLE = "com.aeroplay.app.widget.TOGGLE"
        private const val ACTION_NEXT = "com.aeroplay.app.widget.NEXT"
        private const val ACTION_REPEAT = "com.aeroplay.app.widget.REPEAT"
        private const val ACTION_FAVORITE = "com.aeroplay.app.widget.FAVORITE"
        private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        private var snapshot = WidgetPlayback()
        internal val hostChanges = kotlinx.coroutines.flow.MutableStateFlow(0)

        internal fun hasWidgets(context: Context): Boolean =
            AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty()

        internal fun publish(context: Context, next: WidgetPlayback) {
            if (snapshot == next) return
            snapshot = next
            redraw(context)
        }

        internal fun redraw(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(component(context)).forEach {
                updateOne(context, manager, it, manager.getAppWidgetOptions(it))
            }
        }

        internal fun clear(context: Context) = publish(context, WidgetPlayback())

        private fun component(context: Context) = ComponentName(context, PlaybackWidget::class.java)

        @Suppress("DEPRECATION")
        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
            val appearance = WidgetAppearance.load(context)
            val colors = AdaptiveWidgetColorScheme.resolve(context, appearance)
            val fontScale = context.resources.configuration.fontScale
            if (Build.VERSION.SDK_INT >= 31) {
                val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                    .orEmpty().distinct().take(16)
                if (sizes.isNotEmpty()) {
                    manager.updateAppWidget(id, RemoteViews(sizes.associateWith {
                        render(context, WidgetLayoutPlan(it.width.toInt(), it.height.toInt(), fontScale), appearance, colors)
                    }))
                    return
                }
            }
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidth)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120)
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight)
            val portrait = render(context, WidgetLayoutPlan(minWidth, maxHeight, fontScale), appearance, colors)
            val landscape = render(context, WidgetLayoutPlan(maxWidth, minHeight, fontScale), appearance, colors)
            manager.updateAppWidget(id, RemoteViews(landscape, portrait))
        }

        private fun render(
            context: Context,
            plan: WidgetLayoutPlan,
            appearance: WidgetAppearance,
            colors: AdaptiveWidgetColorScheme
        ): RemoteViews {
            val state = snapshot
            val size = plan.size
            val dark = colors.dark
            val layout = when (size) {
                WidgetSize.QUICK -> R.layout.playback_widget_small
                WidgetSize.WIDE -> R.layout.playback_widget
                WidgetSize.EXPANDED -> R.layout.playback_widget_expanded
            }
            val open = activity(context, ACTION_OPEN_PLAYER)
            val ink = colors.ink
            val muted = colors.secondary
            return RemoteViews(context.packageName, layout).apply {
                setImageViewResource(R.id.widget_glass_fill,
                    if (dark) R.drawable.widget_pearl_dark else R.drawable.widget_pearl_background)
                setInt(R.id.widget_glass_fill, "setImageAlpha",
                    if (size == WidgetSize.QUICK && !appearance.highContrast) appearance.fillAlpha else colors.guardedAlpha)
                setImageViewResource(R.id.widget_glass_optics, R.drawable.widget_glass_optics)
                setInt(R.id.widget_glass_optics, "setImageAlpha", minOf(colors.opticsAlpha, appearance.fillAlpha))
                setImageViewResource(R.id.widget_glass_edge,
                    if (dark) R.drawable.widget_glass_rim_dark else R.drawable.widget_glass_rim)
                cover(R.id.widget_art, state.artwork)
                setContentDescription(R.id.widget_art, context.getString(R.string.playback_widget_open_player))
                setOnClickPendingIntent(R.id.widget_art, open)
                setOnClickPendingIntent(R.id.widget_heading, open)
                setImageViewResource(R.id.widget_toggle, if (state.pauseButton) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
                setContentDescription(R.id.widget_toggle, context.getString(
                    if (state.pauseButton) R.string.playback_widget_pause else R.string.playback_widget_play
                ))
                setInt(R.id.widget_toggle, "setColorFilter", ink)
                setInt(R.id.widget_toggle, "setBackgroundResource", if (dark) R.drawable.widget_orb_dark else R.drawable.widget_orb)
                setOnClickPendingIntent(R.id.widget_toggle, if (state.title == null) open else command(context, ACTION_TOGGLE))
                if (Build.VERSION.SDK_INT >= 31) {
                    setViewLayoutWidth(R.id.widget_art_frame, plan.coverDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                    setViewLayoutHeight(R.id.widget_art_frame, plan.coverDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                }
                if (size != WidgetSize.QUICK) {
                    val subtitle = state.status?.let(context::getString) ?: state.artist
                    setViewVisibility(R.id.widget_heading, if (plan.heading) View.VISIBLE else View.GONE)
                    setTextViewText(R.id.widget_track, state.title ?: context.getString(R.string.playback_widget_idle))
                    setInt(R.id.widget_track, "setMaxLines", plan.titleLines)
                    setTextViewText(R.id.widget_artist, subtitle)
                    val showArtist = subtitle.isNotBlank() && plan.artist
                    setViewVisibility(R.id.widget_artist, if (showArtist) View.VISIBLE else View.GONE)
                    setTextColor(R.id.widget_track, ink)
                    setTextColor(R.id.widget_brand, ink)
                    setTextColor(R.id.widget_artist, muted)
                    setTextColor(R.id.widget_elapsed, muted)
                    setTextColor(R.id.widget_duration, muted)
                    val duration = state.durationMs
                    val showProgress = duration != null
                    val showTimes = showProgress && plan.times
                    setViewVisibility(R.id.widget_progress, if (showProgress) View.VISIBLE else View.GONE)
                    setViewVisibility(R.id.widget_times, if (showTimes) View.VISIBLE else View.GONE)
                    if (duration != null) {
                        setInt(R.id.widget_progress_rail, "setColorFilter", colors.rail)
                        setInt(R.id.widget_progress_fill, "setColorFilter", colors.accent)
                        setInt(R.id.widget_progress_fill, "setImageLevel",
                            (10000.0 * state.positionMs / duration).toInt().coerceIn(0, 10000))
                        setContentDescription(R.id.widget_progress, context.getString(
                            R.string.playback_widget_progress,
                            FormatUtils.formatMillis(state.positionMs), FormatUtils.formatMillis(duration)
                        ))
                        setTextViewText(R.id.widget_elapsed, FormatUtils.formatMillis(state.positionMs))
                        setTextViewText(R.id.widget_duration, FormatUtils.formatMillis(duration))
                    }
                    setOnClickPendingIntent(R.id.widget_progress, open)
                    control(context, R.id.widget_previous, ACTION_PREVIOUS, state.hasPrevious, ink, dark)
                    control(context, R.id.widget_next, ACTION_NEXT, state.hasNext, ink, dark)
                }
                if (size == WidgetSize.EXPANDED) {
                    setTextViewText(R.id.widget_quality, state.quality.orEmpty())
                    setViewVisibility(R.id.widget_quality, if (state.quality == null || !plan.quality) View.GONE else View.VISIBLE)
                    setTextColor(R.id.widget_quality, ink)
                    val panel = if (dark) R.drawable.widget_button_dark else R.drawable.widget_button
                    setInt(R.id.widget_quality, "setBackgroundResource", panel)
                    setViewVisibility(R.id.widget_favorite, if (plan.secondaryControls) View.VISIBLE else View.GONE)
                    setViewVisibility(R.id.widget_repeat, if (plan.secondaryControls) View.VISIBLE else View.GONE)
                    control(context, R.id.widget_favorite, ACTION_FAVORITE, state.video != null, if (state.favorite) colors.accent else ink, dark, state.favorite)
                    control(context, R.id.widget_repeat, ACTION_REPEAT, state.canRepeat, if (state.repeat) colors.accent else ink, dark, state.repeat)
                    setContentDescription(R.id.widget_favorite, context.getString(
                        if (state.favorite) R.string.playback_widget_unfavorite else R.string.playback_widget_favorite
                    ))
                    setContentDescription(R.id.widget_repeat, context.getString(
                        if (state.repeat) R.string.playback_widget_repeat_off else R.string.playback_widget_repeat
                    ))
                    setTextColor(R.id.widget_next_header, ink)
                    setTextColor(R.id.widget_next_title, ink)
                    setTextColor(R.id.widget_next_artist, muted)
                    setInt(R.id.widget_up_next, "setBackgroundResource",
                        if (dark) R.drawable.widget_queue_surface_dark else R.drawable.widget_queue_surface)
                    setTextColor(R.id.widget_open_queue, muted)
                    setTextViewText(R.id.widget_open_queue, context.getString(
                        if (plan.expandedQueueLabel) R.string.playback_widget_open_queue else R.string.playback_widget_queue_short
                    ))
                    setContentDescription(R.id.widget_open_queue, context.getString(R.string.playback_widget_open_queue))
                    setViewVisibility(R.id.widget_next_artist, if (plan.nextArtist) View.VISIBLE else View.GONE)
                    setOnClickPendingIntent(R.id.widget_up_next, activity(context, ACTION_OPEN_QUEUE))
                    setViewVisibility(R.id.widget_next_item, if (state.next == null || !plan.nextItem) View.GONE else View.VISIBLE)
                    setTextViewText(R.id.widget_next_header, context.getString(
                        if (state.title != null && state.next == null) R.string.playback_widget_no_next else R.string.playback_widget_up_next
                    ))
                    setTextViewText(R.id.widget_next_title, state.next?.title.orEmpty())
                    setTextViewText(R.id.widget_next_artist, state.next?.channelName.orEmpty())
                    cover(R.id.widget_next_art, state.nextArtwork)
                }
            }
        }

        private fun RemoteViews.cover(id: Int, bitmap: Bitmap?) {
            if (bitmap == null) setImageViewResource(id, R.drawable.widget_art_placeholder)
            else setImageViewBitmap(id, bitmap)
        }

        private fun RemoteViews.control(context: Context, id: Int, action: String, enabled: Boolean,
            ink: Int, dark: Boolean, active: Boolean = false) {
            setOnClickPendingIntent(id, if (enabled) command(context, action) else null)
            setInt(id, "setColorFilter", ink)
            setInt(id, "setImageAlpha", if (enabled) 255 else 106)
            setInt(id, "setBackgroundResource", when {
                dark && active -> R.drawable.widget_orb_dark
                active -> R.drawable.widget_orb
                dark -> R.drawable.widget_button_dark
                else -> R.drawable.widget_button
            })
        }

        private fun activity(context: Context, action: String): PendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), FLAGS
        )

        private fun command(context: Context, action: String): PendingIntent =
            PendingIntent.getBroadcast(context, 0, Intent(context, PlaybackWidget::class.java).setAction(action), FLAGS)
    }
}
