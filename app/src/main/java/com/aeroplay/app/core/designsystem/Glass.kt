package com.aeroplay.app.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalGlassLight = staticCompositionLocalOf { true }
val LocalFloatingPlayerInset = staticCompositionLocalOf { 0.dp }

@Composable
fun GlassBackground(modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.background
    val fill = if (LocalGlassLight.current) {
        Brush.linearGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.background))
    } else {
        Brush.linearGradient(listOf(base, MaterialTheme.colorScheme.surfaceContainerLow, base))
    }
    Box(modifier.fillMaxSize().background(fill))
}

@Composable
fun GlassSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    elevation: Dp = 0.dp,
    translucent: Boolean = true,
    content: @Composable () -> Unit
) {
    val light = LocalGlassLight.current
    val surface = MaterialTheme.colorScheme.surface
    val scheme = MaterialTheme.colorScheme
    val edge = scheme.outlineVariant.copy(alpha = if (light) 0.8f else 0.7f)
    val fill = if (light) {
        listOf(scheme.surface, scheme.surfaceContainerLow, scheme.surface)
    } else {
        listOf(scheme.surfaceContainerHigh.copy(alpha = 0.97f), surface.copy(alpha = 0.97f))
    }
    val colors = if (translucent) fill else fill.map { it.copy(alpha = 1f) }
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(edge, edge.copy(alpha = 0.3f), edge))),
        shadowElevation = elevation
    ) {
        Box(Modifier.background(Brush.linearGradient(colors))) { content() }
    }
}
