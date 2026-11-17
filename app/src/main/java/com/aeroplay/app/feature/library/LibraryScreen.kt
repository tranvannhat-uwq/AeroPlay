package com.aeroplay.app.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aeroplay.app.core.designsystem.LocalFloatingPlayerInset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aeroplay.app.core.designsystem.CompactVideoRowCard
import com.aeroplay.app.core.designsystem.GlassSurface
import com.aeroplay.app.core.model.UserPlaylist
import com.aeroplay.app.core.model.VideoItem

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val history by viewModel.watchHistory.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val savedPositions by viewModel.savedPositions.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val selectedPlaylistVideos by viewModel.selectedPlaylistVideos.collectAsStateWithLifecycle()
    val deviceVideos by viewModel.deviceVideos.collectAsStateWithLifecycle()
    val mediaImport by viewModel.mediaImport.collectAsStateWithLifecycle()
    val backgroundPlaybackEnabled by viewModel.backgroundPlaybackEnabled.collectAsStateWithLifecycle()
    val videoPicker = rememberLauncherForActivityResult(GalleryVideoPicker()) {
        viewModel.importVideos(it)
    }
    var pickerError by remember { mutableStateOf<String?>(null) }

    fun chooseVideoFromPhotos() {
        pickerError = null
        try {
            videoPicker.launch(Unit)
        } catch (_: ActivityNotFoundException) {
            pickerError = "Chưa mở được ứng dụng Ảnh trên thiết bị này. Bạn có thể mở Ảnh và chia sẻ video sang AeroPlay."
        }
    }

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<UserPlaylist?>(null) }
    var removingDeviceVideo by remember { mutableStateOf<VideoItem?>(null) }
    var renamingDeviceVideo by remember { mutableStateOf<VideoItem?>(null) }
    var deviceVideoName by remember { mutableStateOf("") }

    renamingDeviceVideo?.let { video ->
        AlertDialog(
            onDismissRequest = { renamingDeviceVideo = null },
            title = { Text("Đổi tên video") },
            text = {
                OutlinedTextField(deviceVideoName, onValueChange = { deviceVideoName = it },
                    label = { Text("Tên video") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameDeviceVideo(video.id, deviceVideoName)
                    renamingDeviceVideo = null
                }, enabled = deviceVideoName.isNotBlank()) { Text("Lưu") }
            },
            dismissButton = { TextButton(onClick = { renamingDeviceVideo = null }) { Text("Hủy") } }
        )
    }

    removingDeviceVideo?.let { video ->
        AlertDialog(
            onDismissRequest = { removingDeviceVideo = null },
            title = { Text("Xóa video khỏi AeroPlay?") },
            text = { Text("Bản sao “${video.title}” trong AeroPlay sẽ được xóa. Video gốc trong Ảnh vẫn được giữ.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeDeviceVideo(video.id)
                    removingDeviceVideo = null
                }) { Text("Xóa bản sao") }
            },
            dismissButton = { TextButton(onClick = { removingDeviceVideo = null }) { Text("Hủy") } }
        )
    }

    if (showCreatePlaylistDialog) {
        PlaylistEditDialog(
            title = "Tạo Danh sách phát mới",
            initialName = "",
            initialDescription = "",
            onDismiss = { showCreatePlaylistDialog = false },
            onConfirm = { name, desc ->
                viewModel.createPlaylist(name, desc)
                showCreatePlaylistDialog = false
            }
        )
    }

    if (editingPlaylist != null) {
        PlaylistEditDialog(
            title = "Đổi tên Danh sách phát",
            initialName = editingPlaylist!!.name,
            initialDescription = editingPlaylist!!.description,
            onDismiss = { editingPlaylist = null },
            onConfirm = { name, desc ->
                viewModel.renamePlaylist(editingPlaylist!!.id, name, desc)
                editingPlaylist = null
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 12.dp)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            com.aeroplay.app.core.designsystem.BrandPageHeader(
                title = "Thư viện cá nhân", subtitle = "Lưu trên thiết bị • Không cần tài khoản",
                modifier = Modifier.fillMaxWidth())

            if (selectedTab == LibraryTab.DEVICE) {
                Button(
                    onClick = ::chooseVideoFromPhotos,
                    enabled = !mediaImport.busy
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Thêm video", maxLines = 1, softWrap = false)
                }
            } else if (selectedTab == LibraryTab.PLAYLISTS && selectedPlaylistId == null) {
                Button(onClick = { showCreatePlaylistDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tạo mới", maxLines = 1, softWrap = false)
                }
            } else if (selectedTab == LibraryTab.HISTORY && history.isNotEmpty()) {
                TextButton(onClick = { viewModel.clearAllHistory() }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Xóa lịch sử", maxLines = 1, softWrap = false)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // In-Library Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChange(it) },
            placeholder = {
                Text("Tìm kiếm trong thư viện…", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Xóa")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LibraryTab.entries.forEach { tab ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    label = { Text(tab.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val trimmedFilter = searchQuery.trim()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (selectedTab) {
                LibraryTab.DEVICE -> {
                    if (deviceVideos.isEmpty() || mediaImport.busy || mediaImport.message.isNotBlank() ||
                        !backgroundPlaybackEnabled || pickerError != null) {
                        item {
                            GlassSurface(shape = RoundedCornerShape(20.dp)) {
                                Column(
                                    Modifier.padding(if (deviceVideos.isEmpty()) 16.dp else 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (deviceVideos.isEmpty()) {
                                        Icon(Icons.Filled.VideoLibrary, null, tint = MaterialTheme.colorScheme.primary)
                                        Text("Video của bạn, luôn sẵn sàng", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Chọn video từ Ảnh. AeroPlay lưu một bản sao để phát ngoại tuyến, khi tắt màn hình hoặc dùng ứng dụng khác.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!backgroundPlaybackEnabled) {
                                        Text("Phát nền đang tắt.", style = MaterialTheme.typography.bodySmall)
                                        TextButton(onClick = viewModel::enableBackgroundPlayback) { Text("Bật phát nền") }
                                    }
                                    if (mediaImport.busy) {
                                        val progress = mediaImport.progress
                                        if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                                        else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                        Text(
                                            "Đã xử lý ${mediaImport.completed}/${mediaImport.total} video",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (mediaImport.message.isNotBlank()) Text(mediaImport.message, style = MaterialTheme.typography.bodySmall)
                                    pickerError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                                    if (deviceVideos.isEmpty()) {
                                        TextButton(onClick = ::chooseVideoFromPhotos, enabled = !mediaImport.busy) {
                                            Text("Chọn từ Ảnh")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val filtered = deviceVideos.filter {
                        trimmedFilter.isEmpty() || it.title.contains(trimmedFilter, ignoreCase = true)
                    }
                    if (filtered.isEmpty()) {
                        item {
                            EmptyLibraryCard(
                                if (trimmedFilter.isNotEmpty()) "Không có video khớp từ khóa này."
                                else "Chưa có video từ thiết bị. Nhấn Thêm video để chọn từ Ảnh."
                            )
                        }
                    } else {
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${filtered.size} video", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                TextButton(onClick = { viewModel.playVideo(filtered.first(), filtered, "Video từ thiết bị") }) {
                                    Icon(Icons.Filled.PlayArrow, null)
                                    Text("Phát tất cả")
                                }
                            }
                        }
                        items(filtered, key = { "device_${it.id}" }) { video ->
                            CompactVideoRowCard(
                                video = video,
                                savedProgressFraction = savedPositions[video.id]?.progressFraction ?: 0f,
                                onClick = { viewModel.playVideo(video, filtered, "Video từ thiết bị") },
                                trailingContent = {
                                    var menuOpen by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(onClick = { menuOpen = true }) {
                                            Icon(Icons.Filled.MoreVert, "Tùy chọn video")
                                        }
                                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                            DropdownMenuItem(
                                                text = { Text("Đổi tên") },
                                                onClick = {
                                                    menuOpen = false
                                                    deviceVideoName = video.title
                                                    renamingDeviceVideo = video
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Xóa khỏi AeroPlay") },
                                                onClick = { menuOpen = false; removingDeviceVideo = video }
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                LibraryTab.HISTORY -> {
                    val filtered = history.filter {
                        trimmedFilter.isEmpty() ||
                            it.title.contains(trimmedFilter, ignoreCase = true) ||
                            it.channelName.contains(trimmedFilter, ignoreCase = true)
                    }
                    if (filtered.isEmpty()) {
                        item {
                            EmptyLibraryCard("Chưa có video nào trong lịch sử xem.")
                        }
                    } else {
                        items(filtered, key = { "hist_${it.id}" }) { video ->
                            val progress = savedPositions[video.id]?.progressFraction ?: 0f
                            CompactVideoRowCard(
                                video = video,
                                savedProgressFraction = progress,
                                onClick = {
                                    viewModel.playVideo(video, filtered, "Lịch sử xem")
                                },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.removeFromHistory(video.id) }) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Xóa khỏi lịch sử",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                LibraryTab.FAVORITES -> {
                    val filtered = favorites.filter {
                        trimmedFilter.isEmpty() ||
                            it.title.contains(trimmedFilter, ignoreCase = true) ||
                            it.channelName.contains(trimmedFilter, ignoreCase = true)
                    }
                    if (filtered.isEmpty()) {
                        item {
                            EmptyLibraryCard("Chưa có video yêu thích nào.")
                        }
                    } else {
                        items(filtered, key = { "fav_${it.id}" }) { video ->
                            val progress = savedPositions[video.id]?.progressFraction ?: 0f
                            CompactVideoRowCard(
                                video = video,
                                savedProgressFraction = progress,
                                onClick = {
                                    viewModel.playVideo(video, filtered, "Danh sách yêu thích")
                                },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.toggleFavorite(video) }) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Bỏ yêu thích",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                LibraryTab.PLAYLISTS -> {
                    val currentPl = playlists.find { it.id == selectedPlaylistId }
                    if (currentPl != null) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { viewModel.openPlaylistDetail(null) }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                                    }
                                    Column {
                                        Text(
                                            text = currentPl.name,
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Text(
                                            text = "${selectedPlaylistVideos.size} video",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (selectedPlaylistVideos.isNotEmpty()) {
                                    Button(
                                        onClick = {
                                            viewModel.playVideo(
                                                selectedPlaylistVideos.first(),
                                                selectedPlaylistVideos,
                                                currentPl.name
                                            )
                                        }
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Phát tất cả")
                                    }
                                }
                            }
                        }

                        val filteredPlVideos = selectedPlaylistVideos.filter {
                            trimmedFilter.isEmpty() ||
                                it.title.contains(trimmedFilter, ignoreCase = true)
                        }

                        if (filteredPlVideos.isEmpty()) {
                            item {
                                EmptyLibraryCard("Danh sách phát này chưa có video nào.")
                            }
                        } else {
                            items(filteredPlVideos, key = { "plv_${it.id}" }) { video ->
                                CompactVideoRowCard(
                                    video = video,
                                    onClick = {
                                        viewModel.playVideo(video, filteredPlVideos, currentPl.name)
                                    },
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                viewModel.removeVideoFromPlaylist(currentPl.id, video.id)
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Xóa khỏi playlist",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        val filteredPlaylists = playlists.filter {
                            trimmedFilter.isEmpty() ||
                                it.name.contains(trimmedFilter, ignoreCase = true)
                        }
                        if (filteredPlaylists.isEmpty()) {
                            item {
                                EmptyLibraryCard("Bạn chưa tạo danh sách phát nào. Nhấn 'Tạo mới' để bắt đầu.")
                            }
                        } else {
                            items(filteredPlaylists, key = { "pl_${it.id}" }) { pl ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { viewModel.openPlaylistDetail(pl.id) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 88.dp, height = 54.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (pl.coverThumbnailUrl.isNotBlank()) {
                                                AsyncImage(
                                                    model = pl.coverThumbnailUrl,
                                                    contentDescription = pl.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pl.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${pl.videoCount} video",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        IconButton(onClick = { editingPlaylist = pl }) {
                                            Icon(
                                                imageVector = Icons.Filled.Edit,
                                                contentDescription = "Đổi tên",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        IconButton(onClick = { viewModel.deletePlaylist(pl.id) }) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Xóa danh sách phát",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(96.dp + LocalFloatingPlayerInset.current))
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard(message: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaylistEditDialog(
    title: String,
    initialName: String,
    initialDescription: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên danh sách phát") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Mô tả (tùy chọn)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), description.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
