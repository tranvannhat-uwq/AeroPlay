package com.aeroplay.app.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aeroplay.app.core.model.AutoMix
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.data.repository.RecommendationRanking

@Composable
internal fun AutoMixDialog(mix: AutoMix, onDismiss: () -> Unit, onPlay: (AutoMix, VideoItem) -> Unit,
    viewModel: MixEditorViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember(mix.id) { mutableStateOf(false) }
    var adding by remember(mix.id) { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    LaunchedEffect(state.saved) { if (state.saved) onDismiss() }
    val draft = state.mix ?: mix
    Dialog(onDismissRequest = { if (!state.saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.86f), shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(draft.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${draft.videos.size} mục • ${if (editing) "Chỉnh sửa bản phối" else "AeroPlay"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall)
                if (editing) {
                    TextButton(onClick = { adding = !adding; focus.clearFocus() }, enabled = !state.saving) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(if (adding) "Xem danh sách đang sửa" else "Thêm bài hoặc video")
                    }
                }
                if (adding && editing) {
                    OutlinedTextField(state.query, onValueChange = viewModel::updateQuery, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), label = { Text("Tìm bài hoặc video để thêm") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); viewModel.search() }))
                    TextButton(onClick = { focus.clearFocus(); viewModel.search() }, enabled = !state.searching && state.query.isNotBlank()) { Text("Tìm kiếm") }
                }
                if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                val videos = if (adding && editing) state.candidates else draft.videos
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (videos.isEmpty()) item { Text(if (adding) "Tìm kiếm để thêm nội dung vào bản phối" else "Bản phối đang trống. Chọn Sửa bản phối để thêm nội dung.") }
                    items(videos, key = RecommendationRanking::stableKey) { video ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !editing) { onPlay(draft, video) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(video.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp, 42.dp).clip(RoundedCornerShape(6.dp)))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Text(video.channelName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(enabled = !state.saving, onClick = {
                                if (editing) { if (adding) viewModel.add(video) else viewModel.remove(video) }
                                else viewModel.restrict(video)
                            }) {
                                Icon(if (!editing) Icons.Filled.Block else if (adding) Icons.Filled.Add else Icons.Filled.DeleteOutline,
                                    contentDescription = "${if (!editing) "Hạn chế gợi ý" else if (adding) "Thêm vào bản phối" else "Xóa khỏi bản phối"}: ${video.title}")
                            }
                        }
                    }
                }
                if (editing) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(enabled = !state.saving, onClick = { editing = false; adding = false; viewModel.open(mix); focus.clearFocus() }) { Text("Hủy sửa") }
                        Button(enabled = !state.saving, onClick = viewModel::save) { Text(if (state.saving) "Đang lưu…" else "Lưu thay đổi") }
                    }
                } else {
                    TextButton(onClick = { editing = true }) { Text("Sửa bản phối") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onDismiss) { Text("Đóng") }
                        Button(onClick = { draft.videos.firstOrNull()?.let { onPlay(draft, it) } }, enabled = draft.videos.isNotEmpty()) { Text("Phát bản phối") }
                    }
                }
            }
        }
    }
}
