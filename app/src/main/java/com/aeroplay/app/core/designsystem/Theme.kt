package com.aeroplay.app.core.designsystem

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

/** Brand colors shared by Compose, video overlays and the optional native embedded player. */
object AeroPlayBrand {
    val Midnight = Color(0xFF07152B)
    val Navy = Color(0xFF10213D)
    val NavyRaised = Color(0xFF193253)
    val Cyan = Color(0xFF5DE0F5)
    val Blue = Color(0xFF3D7CFA)
    val Violet = Color(0xFFAD96FF)
    val Ink = Color(0xFF122746)
    val Cloud = Color(0xFFF1F6FC)
    val PlayerScrim = Color(0xFF020718)
    val PlayerForeground = Color(0xFFF6FCFF)
}

internal val AeroPlayDarkColors = darkColorScheme(
    primary = AeroPlayBrand.Cyan,
    onPrimary = Color(0xFF002530),
    primaryContainer = Color(0xFF064B62),
    onPrimaryContainer = Color(0xFFBBF4FF),
    inversePrimary = Color(0xFF00657E),
    secondary = Color(0xFFA7C6FF),
    onSecondary = Color(0xFF002B66),
    secondaryContainer = Color(0xFF153F85),
    onSecondaryContainer = Color(0xFFD9E6FF),
    tertiary = AeroPlayBrand.Violet,
    onTertiary = Color(0xFF2C1455),
    tertiaryContainer = Color(0xFF47306F),
    onTertiaryContainer = Color(0xFFECDDFF),
    background = AeroPlayBrand.Midnight,
    onBackground = Color(0xFFEAF3FF),
    surface = AeroPlayBrand.Navy,
    onSurface = Color(0xFFEAF4FF),
    surfaceVariant = AeroPlayBrand.NavyRaised,
    onSurfaceVariant = Color(0xFFB5C7DE),
    surfaceTint = AeroPlayBrand.Cyan,
    surfaceContainerLowest = Color(0xFF09162D),
    surfaceContainerLow = Color(0xFF0C1B35),
    surfaceContainer = AeroPlayBrand.Navy,
    surfaceContainerHigh = Color(0xFF122143),
    surfaceContainerHighest = Color(0xFF1C2E50),
    surfaceBright = Color(0xFF2A3B5B),
    surfaceDim = AeroPlayBrand.Midnight,
    outline = Color(0xFF8096B2),
    outlineVariant = Color(0xFF304766),
    inverseSurface = Color(0xFFEAF4FF),
    inverseOnSurface = AeroPlayBrand.Ink,
    scrim = AeroPlayBrand.PlayerScrim,
    error = Color(0xFFFFB3BA),
    onError = Color(0xFF5B1125),
    errorContainer = Color(0xFF71273A),
    onErrorContainer = Color(0xFFFFDDE1)
)

internal val AeroPlayLightColors = lightColorScheme(
    primary = Color(0xFF176FD5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF153866),
    inversePrimary = AeroPlayBrand.Cyan,
    secondary = Color(0xFF536A8C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE7FF),
    onSecondaryContainer = Color(0xFF123570),
    tertiary = Color(0xFF7956C7),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDDDFF),
    onTertiaryContainer = Color(0xFF38205C),
    background = AeroPlayBrand.Cloud,
    onBackground = AeroPlayBrand.Ink,
    surface = Color(0xFFFCFDFF),
    onSurface = AeroPlayBrand.Ink,
    surfaceVariant = Color(0xFFE5EEF9),
    onSurfaceVariant = Color(0xFF536985),
    surfaceTint = Color(0xFF176FD5),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F8FE),
    surfaceContainer = Color(0xFFEDF4FC),
    surfaceContainerHigh = Color(0xFFE7F0FA),
    surfaceContainerHighest = Color(0xFFDDE9F7),
    surfaceBright = Color(0xFFFBFDFF),
    surfaceDim = Color(0xFFD7E3EF),
    outline = Color(0xFF627C96),
    outlineVariant = Color(0xFFB9CFDF),
    inverseSurface = AeroPlayBrand.Navy,
    inverseOnSurface = Color(0xFFEAF4FF),
    scrim = AeroPlayBrand.PlayerScrim,
    error = Color(0xFFAC2845),
    onError = Color.White,
    errorContainer = Color(0xFFFFE0E6),
    onErrorContainer = Color(0xFF65162D)
)

val AeroPlayTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.35).sp
    ),
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
    val playButton: Color = AeroPlayBrand.Cyan,
    val onPlayButton: Color = Color(0xFF002530),
    val favoriteActive: Color = AeroPlayBrand.Violet,
    val audioModeActive: Color = AeroPlayBrand.Cyan,
    val verifiedBadge: Color = Color(0xFF74E5C8),
    val experimentalWarning: Color = Color(0xFFFFCA80),
    val reduceMotion: Boolean = false
)

val LocalAeroPlayStatusColors = staticCompositionLocalOf { AeroPlayStatusColors() }
val LocalAeroGlassEffectsEnabled = staticCompositionLocalOf { true }

@Composable
fun AeroPlayTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    glassEffectsEnabled: Boolean = true,
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
            playButton = Color(0xFF00657E),
            onPlayButton = Color.White,
            favoriteActive = Color(0xFF7044A7),
            audioModeActive = Color(0xFF00657E),
            verifiedBadge = Color(0xFF06765D),
            experimentalWarning = Color(0xFF865000),
            reduceMotion = reduceMotion
        )
    }

    CompositionLocalProvider(
        LocalAeroPlayStatusColors provides statusColors,
        LocalGlassLight provides !useDark,
        LocalAeroGlassEffectsEnabled provides glassEffectsEnabled
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AeroPlayTypography,
            content = content
        )
    }
}
