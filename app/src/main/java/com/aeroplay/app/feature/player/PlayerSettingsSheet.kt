package com.aeroplay.app.feature.player

import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Vrpano
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aeroplay.app.core.model.StreamVariant
import com.aeroplay.app.core.model.SubtitleTrack
import kotlinx.coroutines.delay

private enum class PlayerSettingsPage { MAIN, MORE, QUALITY, SPEED, SUBTITLES, SLEEP, HELP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSettingsSheet(
    currentQuality: String,
    selectedVariantId: String?,
    variants: List<StreamVariant>,
    isAudioOnly: Boolean,
    playbackSpeed: Float,
    subtitles: List<SubtitleTrack>,
    selectedSubtitle: SubtitleTrack?,
    isLive: Boolean,
    repeatEnabled: Boolean,
    ambientEnabled: Boolean,
    stableVolumeEnabled: Boolean,
    stableVolumeError: String?,
    isOnTheGo: Boolean,
    sleepDeadlineRealtimeMs: Long?,
    sleepAtEndOfVideo: Boolean,
    canOpenYouTubeMusic: Boolean,
    feedbackUrl: String,
    appVersion: String,
    deviceSummary: String,
    onDismiss: () -> Unit,
    onQuality: (StreamVariant) -> Unit,
    onDefaultQuality: () -> Unit,
    onAudioOnly: (Boolean) -> Unit,
    onSpeed: (Float) -> Unit,
    onSubtitle: (SubtitleTrack?) -> Unit,
    onLockScreen: () -> Unit,
    onTheGo: (Boolean) -> Unit,
    onRepeat: (Boolean) -> Unit,
    onAmbient: (Boolean) -> Unit,
    onStableVolume: (Boolean) -> Unit,
    onSleepMinutes: (Int) -> Unit,
    onSleepAtEnd: (Boolean) -> Unit,
    onOpenYouTubeMusic: () -> Unit,
    onOpenFeedback: (String) -> Unit
) {
    var page by remember { mutableStateOf(PlayerSettingsPage.MAIN) }
    var timerTick by remember { mutableIntStateOf(0) }
    val supportedSubtitles = remember(subtitles) { subtitles.filter { it.isMedia3SubtitleSupported() } }
    val remainingMs = sleepDeadlineRealtimeMs?.let { (it - SystemClock.elapsedRealtime()).coerceAtLeast(0L) }
    LaunchedEffect(sleepDeadlineRealtimeMs) {
        while (sleepDeadlineRealtimeMs != null) {
            delay(1_000L)
            timerTick++
        }
    }
    @Suppress("UNUSED_VARIABLE") val refreshTimer = timerTick

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 690.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (page != PlayerSettingsPage.MAIN) {
                    IconButton(onClick = { page = page.previous() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại mục cài đặt trước")
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
                Text(
                    text = page.title(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Đóng cài đặt phát")
                }
            }
            HorizontalDivider()
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (page) {
                    PlayerSettingsPage.MAIN -> {
                        item {
                            SettingsRow(Icons.Filled.HighQuality, "Chất lượng", currentQuality.ifBlank { "Theo cài đặt" }, showChevron = true) { page = PlayerSettingsPage.QUALITY }
                        }
                        item { SettingsRow(Icons.Filled.Speed, "Tốc độ phát", "${playbackSpeed}×", showChevron = true) { page = PlayerSettingsPage.SPEED } }
                        item {
                            val value = when {
                                selectedSubtitle != null -> selectedSubtitle.displayName.ifBlank { selectedSubtitle.languageCode }
                                supportedSubtitles.isEmpty() -> "Video này chưa có phụ đề"
                                else -> "Tắt"
                            }
                            SettingsRow(Icons.Filled.ClosedCaption, "Phụ đề", value, enabled = supportedSubtitles.isNotEmpty(), showChevron = supportedSubtitles.isNotEmpty()) {
                                if (supportedSubtitles.isEmpty()) page = PlayerSettingsPage.SUBTITLES else page = PlayerSettingsPage.SUBTITLES
                            }
                        }
                        item { SettingsRow(Icons.Filled.Lock, "Khóa màn hình", "Khóa thao tác chạm") { onDismiss(); onLockScreen() } }
                        item {
                            SettingsRow(Icons.Filled.DirectionsCar, "Trên đường đi", if (isOnTheGo) "Đang bật" else "Giao diện nghe nhạc lớn") {
                                onTheGo(!isOnTheGo)
                                onDismiss()
                            }
                        }
                        item { SettingsRow(Icons.Filled.Tune, "Tùy chọn khác", "Lặp lại, hẹn giờ và hơn nữa", showChevron = true) { page = PlayerSettingsPage.MORE } }
                    }

                    PlayerSettingsPage.MORE -> {
                        item {
                            ToggleSettingsRow(Icons.Filled.Repeat, "Cho video lặp lại", "Lặp video hiện tại; nút Tiếp vẫn chuyển bài.", repeatEnabled,
                                enabled = !isLive, disabledReason = if (isLive) "Không áp dụng cho phát trực tiếp." else null,
                                onCheckedChange = onRepeat)
                        }
                        item {
                            ToggleSettingsRow(Icons.Filled.Palette, "Chế độ môi trường xung quanh", "Lấy màu từ ảnh bìa; chỉ hiện khi video ở trước.", ambientEnabled,
                                onCheckedChange = onAmbient)
                        }
                        item {
                            ToggleSettingsRow(Icons.Filled.VolumeUp, "Âm lượng ổn định", "Nén dải động và giới hạn đỉnh trên phiên phát.", stableVolumeEnabled,
                                enabled = Build.VERSION.SDK_INT >= 28, disabledReason = if (Build.VERSION.SDK_INT < 28) "Cần Android 9 trở lên." else stableVolumeError,
                                onCheckedChange = onStableVolume)
                        }
                        item {
                            val timerText = when {
                                sleepAtEndOfVideo -> "Hết video này"
                                remainingMs != null -> "Còn ${formatRemaining(remainingMs)}"
                                else -> "Đang tắt"
                            }
                            SettingsRow(Icons.Filled.Bedtime, "Hẹn giờ ngủ", timerText, showChevron = true) { page = PlayerSettingsPage.SLEEP }
                        }
                        if (canOpenYouTubeMusic) {
                            item { SettingsRow(Icons.Filled.MusicNote, "Nghe nhạc trên YouTube Music", "Mở video hiện tại", onClick = onOpenYouTubeMusic) }
                        }
                        item {
                            SettingsRow(Icons.Filled.Vrpano, "Xem ở chế độ thực tế ảo", "Nguồn chưa cung cấp metadata 360° đã xác minh", enabled = false) {}
                        }
                        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                        item { SettingsRow(Icons.Filled.HelpOutline, "Trợ giúp và phản hồi", "Phiên bản $appVersion • xem thông tin trước khi mở GitHub", showChevron = true) { page = PlayerSettingsPage.HELP } }
                    }

                    PlayerSettingsPage.QUALITY -> {
                        item {
                            SettingsRow(Icons.Filled.HighQuality, "Theo chất lượng mặc định", "Ứng dụng chọn mức gần với cài đặt chất lượng", onClick = onDefaultQuality)
                        }
                        items(variants, key = { "quality_${it.id}" }) { variant ->
                            SettingsRow(Icons.Filled.HighQuality, variant.resolutionLabel,
                                if (variant.isVideoOnly) "Video riêng • ghép với âm thanh" else "Luồng có hình và tiếng",
                                selected = variant.id == selectedVariantId, onClick = { onQuality(variant) })
                        }
                        item { SettingsRow(Icons.Filled.Headphones, "Chỉ âm thanh", "Giảm dữ liệu nếu nguồn có luồng âm thanh riêng", selected = isAudioOnly, onClick = { onAudioOnly(!isAudioOnly) }) }
                        if (variants.isEmpty()) item { InfoText("Nguồn hiện tại không cung cấp danh sách chất lượng riêng.") }
                    }

                    PlayerSettingsPage.SPEED -> {
                        items(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)) { speed ->
                            SettingsRow(Icons.Filled.Speed, if (speed == 1f) "1× • Chuẩn" else "${speed}×", null,
                                selected = kotlin.math.abs(playbackSpeed - speed) < 0.01f, onClick = { onSpeed(speed) })
                        }
                    }

                    PlayerSettingsPage.SUBTITLES -> {
                        item { SettingsRow(Icons.Filled.ClosedCaption, "Tắt", null, selected = selectedSubtitle == null, onClick = { onSubtitle(null) }) }
                        items(supportedSubtitles, key = { "subtitle_${it.id}_${it.languageCode}" }) { track ->
                            SettingsRow(Icons.Filled.ClosedCaption, track.displayName.ifBlank { track.languageCode.ifBlank { "Phụ đề" } },
                                if (track.isAutoGenerated) "Tự động tạo" else track.languageCode,
                                selected = selectedSubtitle?.id == track.id, onClick = { onSubtitle(track) })
                        }
                        if (supportedSubtitles.isEmpty()) item { InfoText("Video này chưa có phụ đề ở định dạng Media3 hỗ trợ.") }
                    }

                    PlayerSettingsPage.SLEEP -> {
                        listOf(5, 15, 30, 45, 60).forEach { minutes ->
                            item { SettingsRow(Icons.Filled.Bedtime, "$minutes phút", null,
                                selected = remainingMs?.let { it / 60_000L in (minutes - 1).toLong()..minutes.toLong() } == true,
                                onClick = { onSleepMinutes(minutes) }) }
                        }
                        item { SettingsRow(Icons.Filled.Bedtime, "Hết video này",
                            if (isLive) "Không áp dụng cho phát trực tiếp." else "Hủy nếu chuyển sang video khác",
                            selected = sleepAtEndOfVideo, enabled = !isLive, showChevron = !isLive,
                            onClick = { onSleepAtEnd(!sleepAtEndOfVideo) }) }
                        item { SettingsRow(Icons.Filled.Close, "Hủy hẹn giờ", null, enabled = sleepDeadlineRealtimeMs != null || sleepAtEndOfVideo, onClick = { onSleepMinutes(0) }) }
                    }

                    PlayerSettingsPage.HELP -> {
                        item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("AeroPlay $appVersion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Thông tin đính kèm: $appVersion • $deviceSummary. Không gửi kèm video đang xem, tài khoản hoặc token.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Nguồn phản hồi: GitHub Issues của AeroPlay. Trang gửi sẽ mở để bạn xem lại và tự gửi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { onOpenFeedback(feedbackUrl) }) {
                                    Icon(Icons.Filled.OpenInNew, null)
                                    Spacer(Modifier.size(8.dp))
                                    Text("Mở GitHub để tự gửi phản hồi")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    BackHandler(enabled = true) {
        if (page == PlayerSettingsPage.MAIN) onDismiss() else page = page.previous()
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String?,
    selected: Boolean = false,
    enabled: Boolean = true,
    showChevron: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surface
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                if (!value.isNullOrBlank()) Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (selected) Icon(Icons.Filled.Check, "Đang chọn", tint = MaterialTheme.colorScheme.primary)
            else if ((value.isNullOrBlank() || showChevron) && enabled) Icon(Icons.Filled.ArrowForwardIos, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun ToggleSettingsRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    disabledReason: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { contentDescription = title },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(disabledReason ?: description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, enabled = enabled, onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

@Composable
private fun InfoText(text: String) {
    Text(text, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun SubtitleTrack.isMedia3SubtitleSupported(): Boolean = when (mimeType.substringBefore(';').trim().lowercase()) {
    "text/vtt", "application/x-subrip", "application/ttml+xml" -> url.startsWith("https://") || url.startsWith("http://") || url.startsWith("file://")
    else -> false
}

private fun PlayerSettingsPage.previous(): PlayerSettingsPage = when (this) {
    PlayerSettingsPage.MAIN -> PlayerSettingsPage.MAIN
    PlayerSettingsPage.MORE -> PlayerSettingsPage.MAIN
    PlayerSettingsPage.QUALITY, PlayerSettingsPage.SPEED, PlayerSettingsPage.SUBTITLES -> PlayerSettingsPage.MAIN
    PlayerSettingsPage.SLEEP, PlayerSettingsPage.HELP -> PlayerSettingsPage.MORE
}

private fun PlayerSettingsPage.title(): String = when (this) {
    PlayerSettingsPage.MAIN -> "Cài đặt phát"
    PlayerSettingsPage.MORE -> "Tùy chọn khác"
    PlayerSettingsPage.QUALITY -> "Chất lượng"
    PlayerSettingsPage.SPEED -> "Tốc độ phát"
    PlayerSettingsPage.SUBTITLES -> "Phụ đề"
    PlayerSettingsPage.SLEEP -> "Hẹn giờ ngủ"
    PlayerSettingsPage.HELP -> "Trợ giúp và phản hồi"
}

private fun formatRemaining(ms: Long): String {
    val seconds = (ms / 1_000L).coerceAtLeast(0L)
    return "%02d:%02d".format(seconds / 60L, seconds % 60L)
}
