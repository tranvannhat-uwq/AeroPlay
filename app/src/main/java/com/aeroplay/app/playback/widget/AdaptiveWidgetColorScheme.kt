package com.aeroplay.app.playback.widget

import android.content.Context
import android.os.Build
import kotlin.math.pow
import kotlin.math.roundToInt

internal data class AdaptiveWidgetColorScheme(
    val dark: Boolean,
    val ink: Int,
    val secondary: Int,
    val accent: Int,
    val rail: Int,
    val guardedAlpha: Int,
    val opticsAlpha: Int
) {
    companion object {
        private val lightMinimum by lazy { minimumAlpha(false, 0xFF293C58.toInt()) }
        private val darkMinimum by lazy { minimumAlpha(true, 0xFFE2EBFF.toInt()) }

        fun resolve(context: Context, appearance: WidgetAppearance): AdaptiveWidgetColorScheme {
            val dark = appearance.isDark(context)
            val dynamic = if (appearance.accent == WidgetAccent.SYSTEM && Build.VERSION.SDK_INT >= 31) {
                context.getColor(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_800)
            } else null
            return create(dark, appearance, dynamic)
        }

        fun create(dark: Boolean, appearance: WidgetAppearance, dynamic: Int? = null): AdaptiveWidgetColorScheme {
            val ink = if (dark) 0xFFF6F9FF.toInt() else 0xFF142653.toInt()
            val secondary = if (dark) 0xFFE2EBFF.toInt() else 0xFF293C58.toInt()
            val rail = if (dark) 0xFF546981.toInt() else 0xFF8293AE.toInt()
            val cyan = if (dark) 0xFFA7EFFF.toInt() else 0xFF004A66.toInt()
            val violet = if (dark) 0xFFE4C9FF.toInt() else 0xFF4C246D.toInt()
            val accent = when (appearance.accent) {
                WidgetAccent.CYAN -> cyan
                WidgetAccent.VIOLET -> violet
                WidgetAccent.SYSTEM -> dynamic?.takeIf { contrast(it, rail) >= 3.2 } ?: cyan
            }
            val minimum = if (dark) darkMinimum else lightMinimum
            val base = when {
                appearance.highContrast -> maxOf(appearance.fillAlpha, minimum, 222)
                appearance.customOpacity -> appearance.fillAlpha
                else -> maxOf(appearance.fillAlpha, minimum)
            }
            return AdaptiveWidgetColorScheme(dark, ink, secondary, accent, rail, base,
                if (appearance.highContrast) 0 else 27)
        }

        // Bound the worst case using white/black wallpaper and up to 8% additional optical tint.
        private fun minimumAlpha(dark: Boolean, text: Int): Int =
            (0..255).firstOrNull { contrast(text, worstSurface(dark, it)) >= 4.6 } ?: 255

        internal fun worstSurface(dark: Boolean, alpha: Int): Int {
            val wallpaper = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            // Per-channel adverse bounds of the light and dark gradients.
            val base = if (dark) 0xFF232543.toInt() else 0xFFEAE4F9.toInt()
            return blend(blend(wallpaper, base, alpha), wallpaper, 20)
        }

        private fun blend(bottom: Int, top: Int, alpha: Int): Int {
            fun channel(shift: Int): Int {
                val from = bottom ushr shift and 255
                val to = top ushr shift and 255
                return (from + (to - from) * alpha / 255.0).roundToInt()
            }
            return 0xFF000000.toInt() or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }

        internal fun contrast(first: Int, second: Int): Double {
            fun luminance(color: Int): Double {
                fun linear(shift: Int): Double {
                    val srgb = (color ushr shift and 255) / 255.0
                    return if (srgb <= 0.04045) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
                }
                return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
            }
            val a = luminance(first) + 0.05
            val b = luminance(second) + 0.05
            return maxOf(a, b) / minOf(a, b)
        }
    }
}
