package com.aeroplay.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aeroplay.app.core.designsystem.AeroPlayBrand
import com.aeroplay.app.core.model.AutoMix

@Composable
internal fun AutoMixCarousel(mixes: List<AutoMix>, onOpen: (AutoMix) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(if (mixes.any { it.isMusic }) "Bản phối dành cho bạn" else "Danh sách kết hợp",
                style = MaterialTheme.typography.titleMedium)
            Text("AeroPlay tự kết hợp • Cập nhật theo nội dung bạn nghe",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(mixes, key = { it.id }) { mix ->
                Card(Modifier.width(196.dp).testTag("AutoMixCard").clickable { onOpen(mix) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))
                        .background(AeroPlayBrand.Navy)) {
                        Column(Modifier.fillMaxSize()) {
                            repeat(2) { row ->
                                Row(Modifier.weight(1f)) {
                                    repeat(2) { column ->
                                        val video = mix.videos.getOrNull(row * 2 + column)
                                        Box(Modifier.weight(1f).fillMaxHeight()) {
                                            if (video != null) AsyncImage(video.thumbnailUrl, contentDescription = null,
                                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        }
                                    }
                                }
                            }
                        }
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                            AeroPlayBrand.PlayerScrim.copy(alpha = 0f), AeroPlayBrand.PlayerScrim.copy(alpha = 0.85f)))))
                        Text("AEROPLAY MIX", style = MaterialTheme.typography.labelLarge,
                            color = AeroPlayBrand.Cyan, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
                    }
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(mix.title, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall)
                        Text("${mix.videos.size} ${if (mix.isMusic) "bài" else "video"} • Tự động kết hợp",
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(mix.subtitle, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
