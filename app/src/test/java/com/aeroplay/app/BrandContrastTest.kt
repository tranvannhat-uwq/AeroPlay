package com.aeroplay.app

import androidx.compose.ui.graphics.Color
import com.aeroplay.app.core.designsystem.AeroPlayDarkColors
import com.aeroplay.app.core.designsystem.AeroPlayLightColors
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class BrandContrastTest {
    @Test fun bothPalettesKeepTextReadableOnAllMaterialContainers() {
        for ((name, scheme) in listOf("light" to AeroPlayLightColors, "dark" to AeroPlayDarkColors)) {
            val pairs = listOf("primary" to (scheme.onPrimary to scheme.primary),
                "primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
                "secondary" to (scheme.onSecondary to scheme.secondary),
                "secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
                "tertiary" to (scheme.onTertiary to scheme.tertiary),
                "tertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
                "background" to (scheme.onBackground to scheme.background),
                "surface" to (scheme.onSurface to scheme.surface),
                "surfaceVariant" to (scheme.onSurfaceVariant to scheme.surfaceVariant),
                "error" to (scheme.onError to scheme.error),
                "errorContainer" to (scheme.onErrorContainer to scheme.errorContainer))
            for ((role, colors) in pairs) {
                val a = luminance(colors.first); val b = luminance(colors.second)
                val ratio = (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
                assertTrue("$name/$role contrast $ratio is below 4.5:1", ratio >= 4.5)
            }
        }
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double = value.toDouble().let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}
