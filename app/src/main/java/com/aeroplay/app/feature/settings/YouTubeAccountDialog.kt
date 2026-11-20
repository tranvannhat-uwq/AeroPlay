package com.aeroplay.app.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.aeroplay.app.core.designsystem.AeroPlaySurface
import com.aeroplay.app.data.youtube.YouTubeAccountUiState
import com.aeroplay.app.data.youtube.YouTubeChannelOption
import com.aeroplay.app.data.youtube.YouTubePlaylist
import com.aeroplay.app.data.youtube.YouTubeSyncStatus
import com.aeroplay.app.R
import java.text.DateFormat
import java.util.Date

@Composable
fun YouTubeAccountDialog(
    state: YouTubeAccountUiState,
    onDismiss: () -> Unit,
    onSignIn: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onAcceptPolicy: () -> Unit,
    onSelectChannel: (YouTubeChannelOption) -> Unit,
    onOpenPlaylist: (Context, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val profile = state.profile
    var expandedPlaylists by remember { mutableStateOf(emptySet<String>()) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    val busy = state.status == YouTubeSyncStatus.SYNCING

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tài khoản YouTube") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.youtube_logo),
                        contentDescription = "YouTube — mở YouTube",
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(6.dp))
                            .clickable { openYouTubeUrl(context, "https://www.youtube.com") }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .size(width = 150.dp, height = 34.dp)
                    )
                }

                item {
                    if (profile == null) {
                        Text(
                            "Đăng nhập Google để đọc dữ liệu YouTube của bạn. AeroPlay chỉ yêu cầu quyền youtube.readonly.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val avatarUrl = profile.channelThumbnailUrl.ifBlank { profile.photoUrl }
                            if (avatarUrl.isNotBlank()) {
                                YouTubeThumbnail(
                                    url = avatarUrl,
                                    contentDescription = "Ảnh đại diện YouTube",
                                    modifier = Modifier.size(52.dp).clip(CircleShape),
                                    shape = CircleShape
                                )
                            }
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(profile.channelTitle.ifBlank { profile.displayName }, fontWeight = FontWeight.SemiBold)
                                if (profile.channelTitle.isNotBlank()) {
                                    Text(profile.displayName, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(profile.email, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                if (!state.featureEnabled || !state.configured) {
                    item {
                        AccountNote(
                            "Tính năng chưa được bật trong bản APK này. Cần cấu hình OAuth Web Client ID, bật YouTube Data API v3 và build với chế độ API-only. Xem GOOGLE_OAUTH_SETUP.md."
                        )
                    }
                }

                if (state.statusMessage.isNotBlank()) {
                    item {
                        AccountNote(
                            state.statusMessage,
                            progress = busy
                        )
                    }
                }

                state.lastSyncAtEpochMs?.let { timestamp ->
                    item {
                        Text(
                            "Làm mới gần nhất: ${DateFormat.getDateTimeInstance().format(Date(timestamp))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (state.channels.size > 1) {
                    item { SectionTitle("Chọn kênh YouTube") }
                    items(state.channels, key = { "channel-${it.channelId}" }) { channel ->
                        AccountRow(
                            title = channel.title,
                            subtitle = if (profile?.channelId == channel.channelId) "Đang chọn" else "Chạm để xem dữ liệu của kênh này",
                            imageUrl = channel.thumbnailUrl,
                            onClick = { onSelectChannel(channel) }
                        )
                    }
                }

                if (profile != null) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onRefresh, enabled = state.featureEnabled && !busy) {
                                if (busy) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.size(8.dp))
                                }
                                Text("Làm mới dữ liệu YouTube")
                            }
                        }
                    }

                    item { SectionTitle("Video đã thích • ${state.likedVideos.size}") }
                    state.groupErrors["likes"]?.let { error -> item { AccountNote("Video đã thích: $error") } }
                    if (state.likedVideos.isEmpty()) {
                        item { EmptyDataMessage("Không có video đã thích hoặc dữ liệu chưa tải.") }
                    } else {
                        items(state.likedVideos, key = { "like-${it.videoId}" }) { video ->
                            AccountRow(
                                title = video.title,
                                subtitle = video.channelTitle,
                                imageUrl = video.thumbnailUrl,
                                onClick = { openYouTubeUrl(context, video.watchUrl) }
                            )
                        }
                    }

                    item { SectionTitle("Danh sách phát • ${state.playlists.size}") }
                    state.groupErrors["playlists"]?.let { error -> item { AccountNote("Danh sách phát: $error") } }
                    if (state.playlists.isEmpty()) {
                        item { EmptyDataMessage("Không có playlist hoặc dữ liệu chưa tải.") }
                    } else {
                        items(state.playlists, key = { "playlist-${it.playlistId}" }) { playlist ->
                            PlaylistRow(
                                playlist = playlist,
                                expanded = playlist.playlistId in expandedPlaylists,
                                loading = playlist.playlistId in state.loadingPlaylistIds,
                                error = state.playlistLoadErrors[playlist.playlistId],
                                onToggle = {
                                    val opening = playlist.playlistId !in expandedPlaylists
                                    expandedPlaylists = if (opening) expandedPlaylists + playlist.playlistId
                                    else expandedPlaylists - playlist.playlistId
                                    if (opening) onOpenPlaylist(context, playlist.playlistId, false)
                                },
                                onRefresh = { onOpenPlaylist(context, playlist.playlistId, true) },
                                onOpenVideo = { url -> openYouTubeUrl(context, url) }
                            )
                        }
                    }

                    item { SectionTitle("Kênh đã đăng ký • ${state.subscriptions.size}") }
                    state.groupErrors["subscriptions"]?.let { error -> item { AccountNote("Kênh đăng ký: $error") } }
                    if (state.subscriptions.isEmpty()) {
                        item { EmptyDataMessage("Không có kênh đăng ký hoặc dữ liệu chưa tải.") }
                    } else {
                        items(state.subscriptions, key = { "subscription-${it.subscriptionId}" }) { subscription ->
                            AccountRow(
                                title = subscription.channelTitle,
                                subtitle = subscription.description.ifBlank { "Mở kênh YouTube" },
                                imageUrl = subscription.thumbnailUrl,
                                onClick = {
                                    openYouTubeUrl(context, "https://www.youtube.com/channel/${subscription.channelId}")
                                }
                            )
                        }
                    }

                    item {
                        AccountNote(
                            "Dữ liệu tài khoản YouTube được lưu trên thiết bị tối đa 30 ngày. Lịch sử xem và Xem sau vẫn là dữ liệu riêng của AeroPlay, không được lấy từ YouTube. Đăng xuất sẽ thu hồi quyền và xóa cache YouTube cục bộ; thao tác này không xóa dữ liệu trên YouTube."
                        )
                    }

                    item {
                        TextButton(onClick = { showPrivacyPolicy = true }) {
                            Text("Chính sách quyền riêng tư YouTube API")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (profile == null) {
                TextButton(
                    onClick = {
                        if (state.privacyPolicyAccepted) onSignIn() else showPrivacyPolicy = true
                    },
                    enabled = state.featureEnabled && state.configured
                ) {
                    Text("Đăng nhập Google")
                }
            } else {
                TextButton(onClick = onSignOut) { Text("Đăng xuất & xóa dữ liệu") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Đóng") }
        }
    )

    if (showPrivacyPolicy) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicy = false },
            title = { Text("Quyền riêng tư và điều khoản") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("AeroPlay dùng YouTube Data API v3 để đọc kênh YouTube, video bạn đã thích, playlist và kênh đăng ký sau khi bạn cấp quyền youtube.readonly.")
                    Text("Ứng dụng lưu email, tên/ảnh đại diện Google, channel ID và dữ liệu nói trên trong DataStore/Room trên thiết bị này; cache API tối đa 30 ngày. Access token chỉ được dùng trong bộ nhớ để gọi API, không ghi log, không lưu vào cache hoặc gửi tới trình phát. AeroPlay không gửi dữ liệu này tới máy chủ riêng. Ảnh thu nhỏ được tải từ URL YouTube do API trả về khi hiển thị và không ghi vào image cache của AeroPlay.")
                    Text("Chạm vào video hoặc kênh sẽ mở ứng dụng YouTube hoặc trình duyệt; YouTube tự xử lý nội dung, quảng cáo và điều khoản tại đó. AeroPlay không thích/bỏ thích, sửa playlist hay đăng ký/hủy đăng ký thay bạn qua API. Lịch sử xem và Xem sau chỉ thuộc AeroPlay, không phải dữ liệu đồng bộ từ YouTube.")
                    Text("Đăng xuất sẽ yêu cầu Google thu hồi quyền, xóa hồ sơ và cache YouTube trên thiết bị. Nếu việc thu hồi trực tuyến thất bại, dữ liệu cục bộ vẫn bị xóa. Việc này không thay đổi dữ liệu trên YouTube.")
                    Text("Bạn có thể thu hồi quyền bất cứ lúc nào trong cài đặt bảo mật Google. Câu hỏi về quyền riêng tư: GitHub Issues của AeroPlay.")
                    Text("Bằng cách tiếp tục, bạn xác nhận đã đọc chính sách này và đồng ý tuân thủ Điều khoản dịch vụ YouTube khi sử dụng dữ liệu YouTube trong AeroPlay.")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { openYouTubeUrl(context, "https://www.youtube.com/t/terms") }) { Text("Điều khoản YouTube") }
                        TextButton(onClick = { openYouTubeUrl(context, "https://policies.google.com/privacy") }) { Text("Quyền riêng tư Google") }
                    }
                    TextButton(onClick = { openYouTubeUrl(context, "https://security.google.com/settings/security/permissions") }) {
                        Text("Quản lý quyền đã cấp cho AeroPlay")
                    }
                    TextButton(onClick = { openYouTubeUrl(context, "https://github.com/tranvannhat-uwq/AeroPlay/issues") }) {
                        Text("Liên hệ về quyền riêng tư")
                    }
                }
            },
            confirmButton = {
                if (!state.privacyPolicyAccepted && profile == null) {
                    Button(onClick = {
                        showPrivacyPolicy = false
                        onAcceptPolicy()
                    }) { Text("Đồng ý & đăng nhập") }
                } else {
                    TextButton(onClick = { showPrivacyPolicy = false }) { Text("Đóng") }
                }
            },
            dismissButton = {
                if (!state.privacyPolicyAccepted && profile == null) {
                    TextButton(onClick = { showPrivacyPolicy = false }) { Text("Từ chối") }
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Column {
        HorizontalDivider()
        Text(
            title,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AccountNote(text: String, progress: Boolean = false) {
    AeroPlaySurface(shape = RoundedCornerShape(12.dp), elevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (progress) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyDataMessage(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AccountRow(
    title: String,
    subtitle: String,
    imageUrl: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (imageUrl.isNotBlank()) {
            YouTubeThumbnail(
                url = imageUrl,
                contentDescription = null,
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp)
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: YouTubePlaylist,
    expanded: Boolean,
    loading: Boolean,
    error: String?,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onOpenVideo: (String) -> Unit
) {
    Column {
        AccountRow(
            title = playlist.title,
            subtitle = "${playlist.itemCount} video • ${playlist.privacyStatus.toPrivacyLabel()} • Nhấn để ${if (expanded) "thu gọn" else "xem video"}",
            imageUrl = playlist.thumbnailUrl,
            onClick = onToggle
        )
        if (expanded) {
            TextButton(
                onClick = onRefresh,
                enabled = !loading,
                modifier = Modifier.align(Alignment.End)
            ) { Text("Làm mới playlist") }
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Đang tải video trong playlist…", modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    if (error != null) {
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (playlist.items.isEmpty()) {
                        EmptyDataMessage("Playlist chưa có video hoặc chưa tải được nội dung.")
                    } else {
                        playlist.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onOpenVideo(item.watchUrl) }.padding(start = 20.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${item.position + 1}.", style = MaterialTheme.typography.bodySmall)
                                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(item.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(item.channelTitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun String.toPrivacyLabel(): String = when (lowercase()) {
    "private" -> "Riêng tư"
    "unlisted" -> "Không công khai"
    "public" -> "Công khai"
    else -> ""
}

private fun openYouTubeUrl(context: Context, url: String) {
    if (com.aeroplay.app.feature.player.YouTubePlayerActivity.open(context, url)) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        // Nothing to open on this device; the account screen remains usable.
    }
}

@Composable
private fun YouTubeThumbnail(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape
) {
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(shape)
    )
}
