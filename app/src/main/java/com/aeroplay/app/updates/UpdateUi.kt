package com.aeroplay.app.updates

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeroplay.app.BuildConfig
import java.util.Locale

@Composable
fun UpdateSettingsCard(manager: AppUpdateManager) {
    val state by manager.state.collectAsStateWithLifecycle()
    var editRepository by remember { mutableStateOf(false) }
    var repository by remember(state.repository) { mutableStateOf(state.repository) }
    var inputError by remember { mutableStateOf<String?>(null) }
    if (editRepository) {
        AlertDialog(
            onDismissRequest = { editRepository = false },
            title = { Text("Kho cập nhật GitHub") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ứng dụng lấy APK từ mục Releases của kho public. Bản cập nhật phải có cùng chữ ký với AeroPlay đang cài.")
                    OutlinedTextField(value = repository, onValueChange = { repository = it; inputError = null },
                        label = { Text("Link kho GitHub") }, singleLine = true, isError = inputError != null,
                        supportingText = { Text(inputError ?: "https://github.com/tai-khoan/ten-kho") })
                }
            },
            confirmButton = { TextButton(onClick = {
                manager.setRepository(repository).fold(onSuccess = { editRepository = false }, onFailure = { inputError = it.message })
            }) { Text("Lưu") } },
            dismissButton = { TextButton(onClick = { editRepository = false }) { Text("Hủy") } }
        )
    }
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Cập nhật ứng dụng", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text("AeroPlay ${BuildConfig.VERSION_NAME} · ${state.repository}", style = MaterialTheme.typography.bodySmall)
            if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall)
            if (state.stage == UpdateStage.DOWNLOADING) DownloadProgress(state)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (state.stage in setOf(UpdateStage.DOWNLOADING, UpdateStage.VERIFYING, UpdateStage.READY, UpdateStage.AVAILABLE)) manager.showPrompt()
                    else manager.check()
                }, enabled = state.stage != UpdateStage.CHECKING) {
                    Text(when (state.stage) {
                        UpdateStage.READY -> "Cài bản cập nhật"
                        UpdateStage.AVAILABLE -> "Xem bản mới"
                        UpdateStage.DOWNLOADING, UpdateStage.VERIFYING -> "Xem tiến trình"
                        UpdateStage.CHECKING -> "Đang kiểm tra…"
                        else -> "Kiểm tra cập nhật"
                    })
                }
                TextButton(onClick = { repository = state.repository; editRepository = true }, enabled = !state.busy && state.stage != UpdateStage.READY) { Text("Đổi kho") }
            }
            Text("Tự kiểm tra khi mở ứng dụng, tối đa mỗi ngày một lần. Android sẽ yêu cầu bạn xác nhận trước khi cài.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AppUpdateDialogHost(manager: AppUpdateManager) {
    val state by manager.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.activity()
    if (!state.promptVisible) return
    val release = state.release
    AlertDialog(
        onDismissRequest = { manager.dismissPrompt() },
        icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
        title = { Text(when (state.stage) {
            UpdateStage.AVAILABLE -> "AeroPlay ${release?.versionName} đã có"
            UpdateStage.DOWNLOADING -> "Đang tải bản cập nhật"
            UpdateStage.VERIFYING -> "Đang kiểm tra APK"
            UpdateStage.READY -> "Sẵn sàng cài cập nhật"
            UpdateStage.ERROR -> "Chưa thể cập nhật"
            else -> "Cập nhật AeroPlay"
        }) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.message)
                if (state.stage == UpdateStage.DOWNLOADING) DownloadProgress(state)
                if (state.stage in setOf(UpdateStage.CHECKING, UpdateStage.VERIFYING)) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (release != null && state.stage == UpdateStage.AVAILABLE) {
                    Text("Dung lượng: ${String.format(Locale.US, "%.1f", release.size / (1024.0 * 1024.0))} MB", style = MaterialTheme.typography.bodySmall)
                    if (release.notes.isNotBlank()) Text(release.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            when (state.stage) {
                UpdateStage.AVAILABLE -> TextButton(onClick = { manager.download() }) { Text("Tải cập nhật") }
                UpdateStage.READY -> TextButton(onClick = { if (activity != null) manager.install(activity) }) { Text("Cài đặt") }
                UpdateStage.ERROR -> TextButton(onClick = { if (release != null) manager.download() else manager.check() }) { Text("Thử lại") }
                else -> TextButton(onClick = { manager.dismissPrompt() }) { Text(if (state.stage == UpdateStage.DOWNLOADING) "Tải trong nền" else "Đóng") }
            }
        },
        dismissButton = {
            if (state.stage == UpdateStage.DOWNLOADING || state.stage == UpdateStage.READY) {
                TextButton(onClick = { manager.cancelDownload() }) { Text("Hủy bản tải") }
            } else if (state.stage == UpdateStage.AVAILABLE) {
                TextButton(onClick = { manager.dismissPrompt() }) { Text("Để sau") }
            }
        }
    )
}

@Composable
private fun DownloadProgress(state: UpdateUiState) {
    val progress = state.progress
    if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
    else {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
