package com.aeroplay.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeroplay.app.data.repository.LibraryPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RestrictionsViewModel @Inject constructor(private val preferences: LibraryPreferencesRepository) : ViewModel() {
    val restrictions = preferences.restrictions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var message by mutableStateOf("")
        private set
    fun addStyle(style: String) = viewModelScope.launch {
        runCatching { preferences.restrictStyle(style) }
            .onSuccess { message = "Đã hạn chế phong cách này" }
            .onFailure { message = "Chưa lưu được lựa chọn. Hãy thử lại." }
    }
    fun remove(id: String) = viewModelScope.launch {
        runCatching { preferences.removeRestriction(id) }
            .onSuccess { message = "Đã gỡ hạn chế" }
            .onFailure { message = "Chưa gỡ được lựa chọn. Hãy thử lại." }
    }
}

@Composable
fun RestrictionsDialog(onDismiss: () -> Unit, viewModel: RestrictionsViewModel = hiltViewModel()) {
    val restrictions by viewModel.restrictions.collectAsStateWithLifecycle()
    var styles by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.86f), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Danh sách hạn chế", style = MaterialTheme.typography.titleLarge)
                Text("Giảm gợi ý nội dung tương tự trong Trang chủ, bản phối tự động và phát tiếp.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !styles, onClick = { styles = false }, label = { Text("Video") })
                    FilterChip(selected = styles, onClick = { styles = true }, label = { Text("Phong cách") })
                }
                if (styles) {
                    OutlinedTextField(query, onValueChange = { query = it.take(80) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), label = { Text("Phong cách hoặc chủ đề") },
                        placeholder = { Text("Ví dụ bolero, Minecraft…") })
                    TextButton(onClick = { viewModel.addStyle(query); query = "" }, enabled = query.isNotBlank()) { Text("Thêm hạn chế") }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("ballad", "acoustic", "bolero", "lofi", "edm", "remix", "rap", "rock", "pop", "classical", "instrumental")) { style ->
                            SuggestionChip(onClick = { viewModel.addStyle(style) }, label = { Text(style) })
                        }
                    }
                }
                if (viewModel.message.isNotBlank()) Text(viewModel.message, style = MaterialTheme.typography.bodySmall)
                val selected = restrictions.filter { (it.kind == "style") == styles }
                LazyColumn(Modifier.weight(1f)) {
                    if (selected.isEmpty()) item { Text(if (styles) "Chưa có phong cách bị hạn chế" else "Chọn Hạn chế gợi ý tương tự trong menu của video để thêm vào đây.") }
                    items(selected, key = { it.restrictionId }) { entry ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(entry.label, Modifier.weight(1f), maxLines = 3, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { viewModel.remove(entry.restrictionId) }) { Text("Gỡ") }
                        }
                    }
                }
                TextButton(onClick = onDismiss) { Text("Đóng") }
            }
        }
    }
}
