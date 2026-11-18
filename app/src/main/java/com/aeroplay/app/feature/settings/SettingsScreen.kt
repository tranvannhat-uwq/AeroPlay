package com.aeroplay.app.feature.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aeroplay.app.core.designsystem.LocalFloatingPlayerInset
import com.aeroplay.app.core.designsystem.GlassSurface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.aeroplay.app.BuildConfig
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.ThemeMode
import com.aeroplay.app.data.youtube.YouTubePolicyFeatureFlags
import com.aeroplay.app.updates.UpdateSettingsCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val toastMessage by viewModel.statusToastMessage.collectAsStateWithLifecycle()
    val accountViewModel: YouTubeAccountViewModel = hiltViewModel()
    val youtubeAccount by accountViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showOpenSourceLicenses by remember { mutableStateOf(false) }
    var showLimitationsModal by remember { mutableStateOf(false) }
    var showYouTubeAccount by remember { mutableStateOf(false) }
    var showRestrictions by remember { mutableStateOf(false) }
    if (showRestrictions) RestrictionsDialog(onDismiss = { showRestrictions = false })
    val authorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            accountViewModel.onAuthorizationResult(context, result.data)
        } else {
            accountViewModel.onAuthorizationCancelled()
        }
    }

    LaunchedEffect(youtubeAccount.pendingAuthorization) {
        val pending = youtubeAccount.pendingAuthorization ?: return@LaunchedEffect
        accountViewModel.authorizationLaunchHandled()
        runCatching {
            authorizationLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
        }.onFailure { accountViewModel.onAuthorizationCancelled() }
    }

    if (toastMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearStatusMessage() },
            title = { Text("Thông báo") },
            text = { Text(toastMessage!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearStatusMessage() }) {
                    Text("Đã hiểu")
                }
            }
        )
    }

    if (showOpenSourceLicenses) {
        OpenSourceLicensesDialog(onDismiss = { showOpenSourceLicenses = false })
    }

    if (showLimitationsModal) {
        ExperimentalSourceLimitationsDialog(onDismiss = { showLimitationsModal = false })
    }

    if (showYouTubeAccount && YouTubePolicyFeatureFlags.accountSyncEnabled) {
        YouTubeAccountDialog(
            state = youtubeAccount,
            onDismiss = { showYouTubeAccount = false },
            onSignIn = { context.findActivity()?.let(accountViewModel::signIn) },
            onRefresh = { accountViewModel.refresh(context) },
            onSignOut = { accountViewModel.signOut(context) },
            onAcceptPolicy = { context.findActivity()?.let(accountViewModel::acceptPrivacyPolicyAndSignIn) },
            onSelectChannel = { accountViewModel.selectChannel(context, it) },
            onOpenPlaylist = { playlistContext, playlistId, force ->
                accountViewModel.openPlaylist(playlistContext, playlistId, force)
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            com.aeroplay.app.core.designsystem.BrandPageHeader(title = "Cài đặt & Tùy chỉnh",
                subtitle = "Giao diện, phát nền và tùy chọn của bạn", modifier = Modifier.fillMaxWidth())
        }

        // 1. Content Source Section
        item {
            SettingsGroupCard(title = "Nguồn Nội Dung (Data Source)") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContentSourceType.onlineSources

                        .forEach { source ->
                        val selected = settings.activeSourceType == source
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { viewModel.setSourceType(source) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = source.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (source.isExperimental && YouTubePolicyFeatureFlags.apiOnlyMode) {
                                        "Dữ liệu YouTube thật và trình phát YouTube chính thức trong AeroPlay."
                                    } else if (source.isExperimental) {
                                        "Dùng NewPipe Extractor (GPLv3) ở chế độ thử nghiệm cá nhân."
                                    } else {
                                        "Dùng luồng phim mở chuẩn Creative Commons để kiểm thử 100% tính năng."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (YouTubePolicyFeatureFlags.apiOnlyMode) {
                        Text(
                            "YouTube dùng dữ liệu API chỉ đọc và trình phát nhúng chính thức trong AeroPlay. Video YouTube dừng khi ra nền.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. Theme Section
        item {
            SettingsGroupCard(title = "Giao diện") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> "Hệ thống"
                                        ThemeMode.DARK -> "Tối (Midnight)"
                                        ThemeMode.LIGHT -> "Sáng"
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }

        // 3. Video Quality & Playback Speed
        item {
            SettingsGroupCard(title = "Chất lượng & Tốc độ mặc định") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.HighQuality,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Chất lượng video ưu tiên",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("360p", "480p", "720p", "1080p").forEach { q ->
                            FilterChip(
                                selected = settings.defaultQualityLabel == q,
                                onClick = { viewModel.setDefaultQuality(q) },
                                label = { Text(q) }
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Tốc độ phát mặc định",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                            FilterChip(
                                selected = kotlin.math.abs(settings.defaultPlaybackSpeed - spd) < 0.01f,
                                onClick = { viewModel.setDefaultSpeed(spd) },
                                label = { Text("${spd}x") }
                            )
                        }
                    }
                }
            }
        }

        // 4. Background Playback, PiP & Data Saver Toggles
        item {
            SettingsGroupCard(title = "Phát nền, Cửa sổ nổi PiP & Dữ liệu") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsToggleRow(
                        icon = Icons.Filled.PlayCircleOutline,
                        title = "Tiếp tục phát nền (Background Audio)",
                        subtitle = "Duy trì phát qua MediaSessionService khi tắt màn hình hoặc chuyển ứng dụng",
                        checked = settings.backgroundPlaybackEnabled,
                        onCheckedChange = { viewModel.setBackgroundPlayback(it) }
                    )

                    SettingsToggleRow(
                        icon = Icons.Filled.AutoAwesome,
                        title = "Tự động phát tiếp",
                        subtitle = "Tự phát mục tiếp theo trong hàng chờ hoặc bài gợi ý khi mục hiện tại kết thúc",
                        checked = settings.autoPlaySimilarMusic,
                        onCheckedChange = { viewModel.setAutoPlaySimilarMusic(it) }
                    )

                    SettingsToggleRow(
                        icon = Icons.Filled.PictureInPictureAlt,
                        title = "Tự động bật Picture-in-Picture (PiP)",
                        subtitle = "Thu nhỏ video về góc màn hình khi nhấn Home lúc đang phát",
                        checked = settings.pipEnabled,
                        onCheckedChange = { viewModel.setPipEnabled(it) }
                    )

                    SettingsToggleRow(
                        icon = Icons.Filled.Headphones,
                        title = "Tự động chuyển sang chế độ Chỉ Âm thanh khi phát nền",
                        subtitle = "Chuyển sang luồng audio-only để giảm băng thông khi ứng dụng ở nền",
                        checked = settings.autoAudioOnlyWhenBackground,
                        onCheckedChange = { viewModel.setAutoAudioOnly(it) }
                    )

                    SettingsToggleRow(
                        icon = Icons.Filled.Headphones,
                        title = "Tự tạm dừng khi ngắt tai nghe",
                        subtitle = "Tạm dừng ngay khi rút tai nghe dây hoặc ngắt kết nối Bluetooth",
                        checked = settings.pauseOnHeadphoneDisconnect,
                        onCheckedChange = { viewModel.setPauseOnHeadphoneDisconnect(it) }
                    )

                    SettingsToggleRow(
                        icon = Icons.Filled.DataSaverOn,
                        title = "Ưu tiên tiết kiệm dữ liệu di động",
                        subtitle = "Ưu tiên luồng 360p và luồng âm thanh bitrate tiết kiệm",
                        checked = settings.dataSaverEnabled,
                        onCheckedChange = { viewModel.setDataSaver(it) }
                    )
                }
            }
        }

        // 5. Storage & Privacy
        item {
            SettingsGroupCard(title = "Nội dung đề xuất") {
                SettingsActionRow(icon = Icons.Filled.AutoAwesome, title = "Danh sách hạn chế",
                    subtitle = "Quản lý video và phong cách bạn ít muốn xem", onClick = { showRestrictions = true })
            }
        }
        item {
            SettingsGroupCard(title = "Dữ liệu cục bộ & Bộ nhớ đệm") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsActionRow(
                        icon = Icons.Filled.DeleteForever,
                        title = "Xóa toàn bộ lịch sử xem & tìm kiếm",
                        subtitle = "Xóa lịch sử xem, tìm kiếm và tín hiệu đề xuất; giữ nguyên yêu thích và danh sách phát",
                        onClick = { viewModel.clearAllHistory() }
                    )
                    SettingsActionRow(
                        icon = Icons.Filled.CleaningServices,
                        title = "Xóa bộ nhớ đệm (Cache)",
                        subtitle = "Giải phóng bộ nhớ đệm ảnh thu nhỏ (Coil) và tạm thời",
                        onClick = { viewModel.clearAppCache() }
                    )
                }
            }
        }

        if (YouTubePolicyFeatureFlags.accountSyncEnabled) item {
            SettingsGroupCard(title = "Tài khoản YouTube") {
                SettingsActionRow(
                    icon = Icons.Filled.AccountCircle,
                    title = if (youtubeAccount.profile == null) "Đăng nhập Google" else "Tài khoản YouTube",
                    subtitle = youtubeAccount.profile?.let {
                        listOf(it.displayName, it.channelTitle).filter(String::isNotBlank).distinct().joinToString(" • ")
                    } ?: "Xem video đã thích, danh sách phát và kênh đã đăng ký",
                    onClick = { showYouTubeAccount = true }
                )
            }
        }

        item { UpdateSettingsCard(viewModel.appUpdates) }

        // 6. Legal, Limitations & Version
        item {
            SettingsGroupCard(title = "Thông tin ứng dụng & Pháp lý") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.aeroplay.app.core.designsystem.AeroPlayWordmark()
                    SettingsActionRow(
                        icon = Icons.Filled.WarningAmber,
                        title = "Giới hạn & Rủi ro của Nguồn Video Thử nghiệm",
                        subtitle = "Xem chi tiết các giới hạn kỹ thuật, chống vượt rào DRM và điều khoản nguồn",
                        onClick = { showLimitationsModal = true }
                    )

                    SettingsActionRow(
                        icon = Icons.Filled.Gavel,
                        title = "Giấy phép mã nguồn mở (Open Source Licenses)",
                        subtitle = "GNU GPLv3 (NewPipe Extractor), Apache 2.0 (AndroidX Media3, Compose, OkHttp)",
                        onClick = { showOpenSourceLicenses = true }
                    )

                    SettingsActionRow(
                        icon = Icons.Filled.Info,
                        title = "Phiên bản AeroPlay",
                        subtitle = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • MinSDK 26 • TargetSDK 35",
                        onClick = {}
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(96.dp + LocalFloatingPlayerInset.current))
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable () -> Unit
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExperimentalSourceLimitationsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Giới hạn & Rủi ro Nguồn Video Thử nghiệm") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "1. Mục đích nguyên mẫu cá nhân:\nAeroPlay là ứng dụng nguyên mẫu phục vụ nghiên cứu kiến trúc Media3 và phát đa phương tiện cá nhân, không mặc định đủ điều kiện phát hành trên Google Play khi bật chế độ trích xuất không chính thức.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "2. Sự phụ thuộc vào cấu trúc trang nguồn:\nChế độ Experimental Extractor sử dụng thư viện NewPipe Extractor để phân tích cú pháp công khai. Khi YouTube thay đổi thuật toán giải mã chữ ký, cấu trúc phản hồi hoặc áp dụng giới hạn PoToken / HTTP 403 / CAPTCHA (HTTP 429), việc phát luồng có thể ngừng hoạt động bất cứ lúc nào.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "3. Không vượt rào bảo mật:\nAeroPlay tuyệt đối KHÔNG vượt DRM, không lấy cookie/token đăng nhập, không vượt rào giới hạn độ tuổi hay địa lý, và không can thiệp vào ứng dụng YouTube chính thức trên máy.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "4. Không cam kết chặn quảng cáo:\nAeroPlay không tự chèn quảng cáo của riêng mình, nhưng không hứa hẹn chặn hoàn toàn quảng cáo từ phía nhà cung cấp nội dung.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}

@Composable
private fun OpenSourceLicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Giấy phép Mã nguồn mở") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "AeroPlay tuân thủ giấy phép GNU General Public License v3.0 (GPLv3) khi tích hợp NewPipe Extractor.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "• NewPipe Extractor (v0.27.1)\n  Copyright (C) Christian Schabesberger & TeamNewPipe\n  License: GNU GPLv3 (https://www.gnu.org/licenses/gpl-3.0.html)",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "• AndroidX Media3 / ExoPlayer, Jetpack Compose, Room, DataStore, Hilt\n  Copyright (C) The Android Open Source Project\n  License: Apache License 2.0",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "• OkHttp & Coil Compose\n  Copyright (C) Square, Inc. & Coil Contributors\n  License: Apache License 2.0",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}
