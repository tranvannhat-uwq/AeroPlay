package com.aeroplay.app.core.designsystem

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle

val LocalGlassLight = staticCompositionLocalOf { true }

/** Pearl-to-midnight ambient page background; content remains the backdrop source. */
@Composable
fun GlassBackground(modifier: Modifier = Modifier) {
    val light = LocalGlassLight.current
    val base = if (light) Color(0xFFF1F6FC) else Color(0xFF07152B)
    val secondary = if (light) Color(0xFFE7F2FD) else Color(0xFF0B1C38)
    val cyanGlow = AeroPlayBrand.Cyan.copy(alpha = if (light) 0.13f else 0.10f)
    val violetGlow = AeroPlayBrand.Violet.copy(alpha = if (light) 0.095f else 0.085f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(base)
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        colors = if (light) listOf(Color.White, secondary, Color(0xFFF4F7FF))
                        else listOf(Color(0xFF08172F), Color(0xFF0D1E3A), Color(0xFF101A39)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(cyanGlow, Color.Transparent),
                        center = Offset(size.width * 0.94f, size.height * 0.03f),
                        radius = size.width * 0.78f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(violetGlow, Color.Transparent),
                        center = Offset(size.width * 0.04f, size.height * 0.84f),
                        radius = size.width * 0.74f
                    )
                )
            }
    )
}

/**
 * Opaque-enough content surface for cards and rows. It deliberately does not sample a backdrop;
 * use [AeroGlassSurface] only for floating chrome such as the bottom dock and mini player.
 */
@Composable
fun AeroPlaySurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    elevation: Dp = 0.dp,
    translucent: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val light = LocalGlassLight.current
    val scheme = MaterialTheme.colorScheme
    val borderBrush = Brush.linearGradient(
        if (light) listOf(Color.White.copy(alpha = 0.96f), scheme.outlineVariant.copy(alpha = 0.62f), Color.White.copy(alpha = 0.72f))
        else listOf(Color.White.copy(alpha = 0.42f), scheme.outlineVariant.copy(alpha = 0.50f), AeroPlayBrand.Cyan.copy(alpha = 0.23f))
    )
    val fill = if (light) {
        Brush.linearGradient(
            listOf(
                scheme.surface.copy(alpha = if (translucent) 0.96f else 1f),
                scheme.surfaceContainerLow.copy(alpha = if (translucent) 0.94f else 1f),
                scheme.surface.copy(alpha = if (translucent) 0.96f else 1f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                scheme.surfaceContainerHigh.copy(alpha = if (translucent) 0.97f else 1f),
                scheme.surface.copy(alpha = if (translucent) 0.96f else 1f),
                scheme.surfaceContainerLow.copy(alpha = if (translucent) 0.97f else 1f)
            )
        )
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(0.8.dp, borderBrush),
        shadowElevation = elevation.coerceAtMost(2.dp)
    ) {
        Box(Modifier.background(fill), content = content)
    }
}

/**
 * Real-time floating Liquid Glass surface. The backdrop source must be a sibling of this surface.
 * Older Android versions and the user opt-out use a calm, legible material fallback.
 */
@Composable
fun AeroGlassSurface(
    backdrop: LayerBackdrop?,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val enabled = LocalAeroGlassEffectsEnabled.current
    val light = LocalGlassLight.current
    val scheme = MaterialTheme.colorScheme
    val sdk = Build.VERSION.SDK_INT
    val supportsBackdropBlur = enabled && backdrop != null && sdk >= Build.VERSION_CODES.S

    if (!supportsBackdropBlur) {
        AeroPlaySurface(shape = shape, modifier = modifier, elevation = 1.dp, content = content)
        return
    }

    val edge = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (light) 0.86f else 0.48f),
            scheme.primary.copy(alpha = if (light) 0.28f else 0.32f),
            AeroPlayBrand.Violet.copy(alpha = if (light) 0.20f else 0.25f),
            Color.White.copy(alpha = if (light) 0.44f else 0.36f)
        )
    )
    val wash = Brush.linearGradient(
        if (light) listOf(Color.White.copy(alpha = 0.21f), scheme.primary.copy(alpha = 0.045f), Color.White.copy(alpha = 0.13f))
        else listOf(scheme.surface.copy(alpha = 0.28f), AeroPlayBrand.Cyan.copy(alpha = 0.045f), AeroPlayBrand.Violet.copy(alpha = 0.055f))
    )
    val glassBackdrop = requireNotNull(backdrop)

    Box(
        modifier = modifier
            .clip(shape)
            .drawBackdrop(
                backdrop = glassBackdrop,
                shape = { shape },
                effects = {
                    blur(18f)
                    if (sdk >= Build.VERSION_CODES.TIRAMISU) {
                        lens(refractionHeight = 23f, refractionAmount = 20f)
                    }
                },
                highlight = {
                    Highlight(
                        width = 1.dp,
                        blurRadius = 1.dp,
                        alpha = 0.88f,
                        style = HighlightStyle.Plain(color = Color.White.copy(alpha = 0.23f))
                    )
                },
                shadow = null,
                onDrawSurface = { drawRect(wash) }
            )
            .border(BorderStroke(0.85.dp, edge), shape),
        content = content
    )
}
