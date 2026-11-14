package com.aeroplay.app.core.designsystem

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aeroplay.app.core.model.ThemeMode

// 2026 AeroPlay Signature Palette (No Pure Tuber / YouTube assets used)
val ObsidianBackground = Color(0xFF0A0B10)
val ObsidianSurface = Color(0xFF131620)
val ObsidianSurfaceVariant = Color(0xFF1C2030)
val ObsidianCard = Color(0xFF171A26)

val CoralRedAccent = Color(0xFFFF4D6D)
val CoralRedSoft = Color(0xFFFF758F)
val ElectricViolet = Color(0xFF8B5CF6)
val ElectricVioletDeep = Color(0xFF6D28D9)

// Status-specific colors required in Section V
val StatusPlayColor = Color(0xFFFF4D6D)
val StatusFavoriteColor = Color(0xFFFF2E93)
val StatusAudioModeColor = Color(0xFF06B6D4)
val StatusVerifiedColor = Color(0xFF10B981)
val StatusWarningColor = Color(0xFFF59E0B)

// Neutral Light Palette
val NeutralLightBackground = Color(0xFFF4F5FF)
val NeutralLightSurface = Color(0xFFFFFFFF)
val NeutralLightSurfaceVariant = Color(0xFFEEEDFA)
val NeutralLightTextPrimary = Color(0xFF17182F)
val NeutralLightTextSecondary = Color(0xFF525571)

private val AeroPlayDarkColors = darkColorScheme(
    primary = CoralRedAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3F1624),
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = ElectricViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2B1F4A),
    onSecondaryContainer = Color(0xFFE7DEFF),
    tertiary = StatusAudioModeColor,
    onTertiary = Color(0xFF002026),
    background = ObsidianBackground,
    onBackground = Color(0xFFF3F4F8),
    surface = ObsidianSurface,
    onSurface = Color(0xFFF3F4F8),
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = Color(0xFFA0A8BD),
    error = Color(0xFFFF5C5C),
    onError = Color.White
)

private val AeroPlayLightColors = lightColorScheme(
    primary = Color(0xFF5940C4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAE3FF),
    onPrimaryContainer = Color(0xFF26115C),
    secondary = Color(0xFFAD3368),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF2E1065),
    tertiary = Color(0xFF0891B2),
    onTertiary = Color.White,
    background = NeutralLightBackground,
    onBackground = NeutralLightTextPrimary,
    surface = NeutralLightSurface,
    onSurface = NeutralLightTextPrimary,
    surfaceVariant = NeutralLightSurfaceVariant,
    onSurfaceVariant = NeutralLightTextSecondary,
    error = Color(0xFFDC2626),
    onError = Color.White
)

val AeroPlayTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

@Immutable
data class AeroPlayStatusColors(
    val playButton: Color = StatusPlayColor,
    val favoriteActive: Color = StatusFavoriteColor,
    val audioModeActive: Color = StatusAudioModeColor,
    val verifiedBadge: Color = StatusVerifiedColor,
    val experimentalWarning: Color = StatusWarningColor,
    val reduceMotion: Boolean = false
)

val LocalAeroPlayStatusColors = staticCompositionLocalOf { AeroPlayStatusColors() }

@Composable
fun AeroPlayTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val context = LocalContext.current
    val reduceMotion = remember(context) {
        runCatching {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            scale == 0f
        }.getOrDefault(false)
    }

    val colorScheme = if (useDark) AeroPlayDarkColors else AeroPlayLightColors
    val statusColors = remember(reduceMotion, useDark) {
        if (useDark) AeroPlayStatusColors(reduceMotion = reduceMotion)
        else AeroPlayStatusColors(
            playButton = Color(0xFF6744D8),
            favoriteActive = Color(0xFFC12968),
            audioModeActive = Color(0xFF087A8B),
            verifiedBadge = Color(0xFF08775A),
            experimentalWarning = Color(0xFF92550E),
            reduceMotion = reduceMotion
        )
    }

    CompositionLocalProvider(
        LocalAeroPlayStatusColors provides statusColors,
        LocalGlassLight provides !useDark
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AeroPlayTypography,
            content = content
        )
    }
}
