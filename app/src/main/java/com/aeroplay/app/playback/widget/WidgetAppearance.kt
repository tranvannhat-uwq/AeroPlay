package com.aeroplay.app.playback.widget

import android.content.Context
import android.content.res.Configuration
import kotlin.math.roundToInt

internal enum class WidgetGlassMode(val label: String) {
    PEARL("Pearl Glass Light"),
    NEON("Neon Glass Dark"),
    ADAPTIVE("Theo hệ thống")
}

internal data class WidgetAppearance(
    val mode: WidgetGlassMode = WidgetGlassMode.PEARL,
    val opacity: Int = DEFAULT_OPACITY
) {
    val fillAlpha: Int get() = (opacity.coerceIn(0, 100) * 255 / 100.0).roundToInt()

    fun isDark(context: Context): Boolean = when (mode) {
        WidgetGlassMode.PEARL -> false
        WidgetGlassMode.NEON -> true
        WidgetGlassMode.ADAPTIVE -> (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun save(context: Context) {
        preferences(context).edit().putString("glassMode", mode.name)
            .putInt("opacity", opacity.coerceIn(0, 100)).apply()
        PlaybackWidget.redraw(context)
    }

    companion object {
        const val DEFAULT_OPACITY = 28

        fun load(context: Context): WidgetAppearance {
            val prefs = preferences(context)
            val name = prefs.getString("glassMode", null)
            return WidgetAppearance(
                mode = WidgetGlassMode.entries.firstOrNull { it.name == name } ?: WidgetGlassMode.PEARL,
                opacity = prefs.getInt("opacity", DEFAULT_OPACITY).coerceIn(0, 100)
            )
        }

        private fun preferences(context: Context) = context.getSharedPreferences("playback_widget", Context.MODE_PRIVATE)
    }
}
