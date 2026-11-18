package com.aeroplay.app.playback.widget

import android.content.Context
import android.content.res.Configuration
import kotlin.math.roundToInt

internal enum class WidgetGlassMode(val label: String) {
    PEARL("Pearl Glass Light"),
    NEON("Neon Glass Dark"),
    ADAPTIVE("Tự động theo hệ thống")
}

internal enum class WidgetAccent(val label: String) {
    SYSTEM("Hệ thống"), CYAN("Cyan"), VIOLET("Violet")
}

internal data class WidgetAppearance(
    val mode: WidgetGlassMode = WidgetGlassMode.ADAPTIVE,
    val opacity: Int = DEFAULT_OPACITY,
    val highContrast: Boolean = false,
    val accent: WidgetAccent = WidgetAccent.SYSTEM
) {
    val fillAlpha: Int get() = (opacity.coerceIn(0, 100) * 255 / 100.0).roundToInt()

    fun isDark(context: Context): Boolean = when (mode) {
        WidgetGlassMode.PEARL -> false
        WidgetGlassMode.NEON -> true
        WidgetGlassMode.ADAPTIVE -> (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun save(context: Context) {
        preferences(context).edit().putString("adaptiveGlassMode", mode.name)
            .putInt("opacity", opacity.coerceIn(0, 100))
            .putBoolean("highContrast", highContrast)
            .putString("accent", accent.name)
            .apply()
        PlaybackWidget.redraw(context)
    }

    companion object {
        const val DEFAULT_OPACITY = 28

        fun load(context: Context): WidgetAppearance {
            val prefs = preferences(context)
            val name = prefs.getString("adaptiveGlassMode", null)
            val accent = prefs.getString("accent", null)
            return WidgetAppearance(
                mode = WidgetGlassMode.entries.firstOrNull { it.name == name }
                    ?: if (prefs.getString("glassMode", null) == WidgetGlassMode.NEON.name) WidgetGlassMode.NEON
                    else WidgetGlassMode.ADAPTIVE,
                opacity = prefs.getInt("opacity", DEFAULT_OPACITY).coerceIn(0, 100),
                highContrast = prefs.getBoolean("highContrast", false),
                accent = WidgetAccent.entries.firstOrNull { it.name == accent } ?: WidgetAccent.SYSTEM
            )
        }

        private fun preferences(context: Context) = context.getSharedPreferences("playback_widget", Context.MODE_PRIVATE)
    }
}
