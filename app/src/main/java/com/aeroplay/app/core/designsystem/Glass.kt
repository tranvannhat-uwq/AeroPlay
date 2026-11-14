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
        Brush.linearGradient(listOf(Color(0xFFF9FAFF), Color(0xFFEDF0FF), Color(0xFFF8EEFA), Color(0xFFF2F8FF)))
    } else {
        Brush.linearGradient(listOf(base, Color(0xFF151529), base))
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
    val edge = if (light) Color.White.copy(alpha = 0.96f) else Color.White.copy(alpha = 0.15f)
    val fill = if (light) {
        listOf(Color.White.copy(alpha = 0.96f), Color(0xE8F8F6FF), Color(0xDCF0F7FF))
    } else {
        listOf(Color(0xED25283C), surface.copy(alpha = 0.92f))
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
