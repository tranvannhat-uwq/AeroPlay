package com.aeroplay.app.playback.widget

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import kotlin.math.pow
import kotlin.math.roundToInt

internal data class AdaptiveWidgetColorScheme(
    val dark: Boolean,
    val lightInk: Boolean,
    val ink: Int,
    val secondary: Int,
    val accent: Int,
    val rail: Int,
    val guardedAlpha: Int,
    val opticsAlpha: Int
) {
    companion object {
        private const val DARK_INK = 0xFF0B172A.toInt()
        private const val LIGHT_INK = 0xFFF8FAFF.toInt()
        private const val DARK_SECONDARY = 0xFF253751.toInt()
        private const val LIGHT_SECONDARY = 0xFFE4EDFC.toInt()

        fun resolve(context: Context, appearance: WidgetAppearance): AdaptiveWidgetColorScheme {
            val wallpaper = if (Build.VERSION.SDK_INT >= 27) runCatching {
                WallpaperManager.getInstance(context)
                    .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
            }.getOrNull() else null
            val dark = appearance.isDark(context, wallpaper)
            val dynamic = if (appearance.accent == WidgetAccent.SYSTEM && Build.VERSION.SDK_INT >= 31) {
                context.getColor(android.R.color.system_accent1_500)
            } else null
            return create(dark, appearance, dynamic, wallpaper)
        }

        internal fun prefersDarkGlass(wallpaper: Int): Boolean = luminance(wallpaper) < 0.36

        fun create(dark: Boolean, appearance: WidgetAppearance, dynamic: Int? = null,
                   wallpaper: Int? = null): AdaptiveWidgetColorScheme {
            val custom = appearance.customOpacity && !appearance.highContrast
            val surface = if (dark) 0xFF14203B.toInt() else 0xFFE9F1FC.toInt()
            val sample = wallpaper?.let { blend(it, surface, appearance.fillAlpha) }
            val lightInk = if (custom && sample != null) contrast(LIGHT_INK, sample) > contrast(DARK_INK, sample) else dark
            val ink = if (lightInk) LIGHT_INK else DARK_INK
            val secondary = when {
                custom -> ink
                lightInk -> LIGHT_SECONDARY
                else -> DARK_SECONDARY
            }
            val rail = if (lightInk) 0xFF425571.toInt() else 0xFFB6C6DC.toInt()
            val cyan = if (lightInk) 0xFF9FEFFF.toInt() else 0xFF004A66.toInt()
            val violet = if (lightInk) 0xFFE2C5FF.toInt() else 0xFF51297D.toInt()
            val accent = when (appearance.accent) {
                WidgetAccent.CYAN -> cyan
                WidgetAccent.VIOLET -> violet
                WidgetAccent.SYSTEM -> dynamic
                    ?.let { blend(it, if (lightInk) -1 else 0xFF060E25.toInt(), if (lightInk) 116 else 134) }
                    ?.takeIf { contrast(it, rail) >= 3.2 } ?: cyan
            }
            val base = when {
                appearance.highContrast -> maxOf(appearance.fillAlpha, minimumAlpha(dark, secondary, null), 222)
                custom -> appearance.fillAlpha
                else -> maxOf(appearance.fillAlpha, minimumAlpha(dark, secondary, wallpaper))
            }
            return AdaptiveWidgetColorScheme(dark, lightInk, ink, secondary, accent, rail, base,
                if (appearance.highContrast) 0 else 52)
        }

        private fun minimumAlpha(dark: Boolean, text: Int, wallpaper: Int?): Int =
            (0..255).firstOrNull { contrast(text, worstSurface(dark, it, wallpaper)) >= 4.6 } ?: 255

        internal fun worstSurface(dark: Boolean, alpha: Int, wallpaper: Int? = null): Int {
            val extreme = if (dark) -1 else 0xFF000000.toInt()
            // The dominant wallpaper color isn't specific to the widget's position.
            val backdrop = wallpaper?.let { blend(it, extreme, 158) } ?: extreme
            // Per-channel adverse bounds of the light and dark gradients.
            val base = if (dark) 0xFF272840.toInt() else 0xFFE3E2F6.toInt()
            return blend(blend(backdrop, base, alpha), extreme, 26)
        }

        private fun blend(bottom: Int, top: Int, alpha: Int): Int {
            fun channel(shift: Int): Int {
                val from = bottom ushr shift and 255
                val to = top ushr shift and 255
                return (from + (to - from) * alpha / 255.0).roundToInt()
            }
            return 0xFF000000.toInt() or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }

        private fun luminance(color: Int): Double {
            fun linear(shift: Int): Double {
                val srgb = (color ushr shift and 255) / 255.0
                return if (srgb <= 0.04045) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
        }

        internal fun contrast(first: Int, second: Int): Double {
            val a = luminance(first) + 0.05
            val b = luminance(second) + 0.05
            return maxOf(a, b) / minOf(a, b)
        }
    }
}
