package com.aeroplay.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aeroplay.app.core.common.FormatUtils
import com.aeroplay.app.core.designsystem.AddToPlaylistDialog
import com.aeroplay.app.core.designsystem.CoralRedAccent
import com.aeroplay.app.core.designsystem.ElectricViolet
import com.aeroplay.app.core.designsystem.ErrorStateCard
import com.aeroplay.app.core.designsystem.GlassSurface
import com.aeroplay.app.core.designsystem.LocalFloatingPlayerInset
import com.aeroplay.app.core.designsystem.LocalAeroPlayStatusColors
import com.aeroplay.app.core.designsystem.SkeletonVideoCard
import com.aeroplay.app.core.designsystem.SourceModePill
import com.aeroplay.app.core.designsystem.VideoFeedCard
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.PlaybackPosition
import com.aeroplay.app.core.model.VideoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val personalized by viewModel.personalizedState.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val savedPositions by viewModel.savedPositions.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val refreshState = rememberPullToRefreshState()
    val floatingInset = LocalFloatingPlayerInset.current

    var playlistTargetVideo by remember { mutableStateOf<VideoItem?>(null) }
    var showSourceSwitchDialog by remember { mutableStateOf(false) }

    if (playlistTargetVideo != null) {
        AddToPlaylistDialog(
            video = playlistTargetVideo!!,
            playlists = playlists,
            onDismiss = { playlistTargetVideo = null },
            onSelectPlaylist = { plId -> viewModel.addVideoToPlaylist(plId, playlistTargetVideo!!) },
            onCreateAndAdd = { name -> viewModel.createPlaylistAndAdd(name, playlistTargetVideo!!) }
        )
    }

    if (showSourceSwitchDialog) {
        SourceSelectionDialog(
            currentSource = settings.activeSourceType,
            onDismiss = { showSourceSwitchDialog = false },
            onSelectSource = { selected ->
                viewModel.switchSource(selected)
                showSourceSwitchDialog = false
            }
        )
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = modifier.fillMaxSize(),
        state = refreshState,
        indicator = {
            if (uiState.isRefreshing || refreshState.distanceFraction > 0f) {
                PullToRefreshDefaults.Indicator(
                    state = refreshState,
                    isRefreshing = uiState.isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).testTag("PullToRefreshIndicator")
                )
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp + floatingInset),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Brand Header & Source Pill
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(CoralRedAccent, ElectricViolet)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "AeroPlay Logo",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "AeroPlay",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Pure Native Media Player",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        SourceModePill(
                            sourceType = settings.activeSourceType,
                            onClick = { showSourceSwitchDialog = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Prominent Search Bar
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onOpenSearch() },
                        shape = RoundedCornerShape(22.dp),
                        elevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Tìm kiếm",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (settings.activeSourceType.isExperimental) {
                                    "Tìm kiếm video, kênh hoặc playlist (Extractor)..."
                                } else {
                                    "Tìm kiếm video mở, phim Blender, luồng kiểm thử..."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Recommendations based on viewing history.
            if (personalized.videos.isNotEmpty()) {
                item {
                    HorizontalVideoCarouselSection(
                        title = "Dành cho bạn",
                        icon = Icons.Filled.AutoAwesome,
                        videos = personalized.videos,
                        savedPositions = savedPositions,
                        onVideoClick = { video ->
                            viewModel.playVideo(video, personalized.videos, "Dành cho bạn")
                        }
                    )
                }
            } else {
                item {
                    GlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Dành cho bạn", style = MaterialTheme.typography.titleMedium)
                            if (personalized.loading) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text("Đang tìm video tương tự…", style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text(personalized.message, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // 3. Favorites Section (if not empty)
            if (favorites.isNotEmpty()) {
                item {
                    HorizontalVideoCarouselSection(
                        title = "Danh sách yêu thích",
                        icon = Icons.Filled.Favorite,
                        videos = favorites.take(10),
                        savedPositions = savedPositions,
                        onVideoClick = { video ->
                            viewModel.playVideo(video, favorites, "Danh sách yêu thích")
                        }
                    )
                }
            }

            // 4. Recommended / Featured Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (settings.activeSourceType.isExperimental) {
                            "Đề xuất thịnh hành (Extractor)"
                        } else {
                            "Video Chuẩn Mở Đề Xuất"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // 5. Loading / Error / Empty / Feed States
            when {
                uiState.isLoading -> {
                    items(3) {
                        SkeletonVideoCard(
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                uiState.error != null -> {
                    item {
                        ErrorStateCard(
                            title = "Không thể tải danh sách đề xuất",
                            message = uiState.error!!.message,
                            technicalDetail = uiState.error!!.technicalDetail,
                            onRetry = { viewModel.refresh() },
                            onSwitchToDemoSource = if (settings.activeSourceType.isExperimental) {
                                { viewModel.switchSource(ContentSourceType.DEMO_LOCAL) }
                            } else null,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                uiState.recommendedVideos.isEmpty() -> {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Chưa có video đề xuất",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Hãy thử làm mới hoặc dùng thanh tìm kiếm để khám phá nội dung.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                else -> {
                    items(
                        items = uiState.recommendedVideos,
                        key = { it.id }
                    ) { video ->
                        val progress = savedPositions[video.id]?.progressFraction ?: 0f
                        VideoFeedCard(
                            video = video,
                            isFavorite = video.id in favoriteIds,
                            savedProgressFraction = progress,
                            onClick = {
                                viewModel.playVideo(
                                    video = video,
                                    queue = uiState.recommendedVideos,
                                    queueTitle = "Đề xuất trang chủ"
                                )
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(video) },
                            onAddToPlaylist = { playlistTargetVideo = video },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HorizontalVideoCarouselSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    videos: List<VideoItem>,
    savedPositions: Map<String, PlaybackPosition>,
    onVideoClick: (VideoItem) -> Unit
) {
    val statusColors = LocalAeroPlayStatusColors.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(videos, key = { "${title}_${it.id}" }) { video ->
                val progress = savedPositions[video.id]?.progressFraction ?: 0f
                Card(
                    modifier = Modifier
                        .width(210.dp)
                        .clickable { onVideoClick(video) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = video.thumbnailUrl,
                                contentDescription = video.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp),
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Black.copy(alpha = 0.80f)
                            ) {
                                Text(
                                    text = FormatUtils.formatDuration(video.durationSeconds),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            if (progress > 0.01f) {
                                LinearProgressIndicator(
                                    progress = { progress.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .align(Alignment.BottomCenter),
                                    color = statusColors.playButton,
                                    trackColor = Color.White.copy(alpha = 0.3f)
                                )
                            }
                        }
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = video.channelName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SourceSelectionDialog(
    currentSource: ContentSourceType,
    onDismiss: () -> Unit,
    onSelectSource: (ContentSourceType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Chọn Nguồn Nội Dung Media")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "AeroPlay tách biệt rõ nguồn media chuẩn mở và chế độ trích xuất thử nghiệm (NewPipe Extractor):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ContentSourceType.onlineSources.forEach { source ->
                    val isSelected = source == currentSource
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectSource(source) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = source.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (source.isExperimental) {
                                    "Chế độ thử nghiệm không chính thức (GPLv3). Có thể ngừng hoạt động khi YouTube thay đổi cấu trúc trang, chặn IP hoặc yêu cầu CAPTCHA. Chỉ dùng cho nghiên cứu cá nhân."
                                } else {
                                    "Phát các luồng phim/nhạc Creative Commons hợp lệ độc lập với YouTube để kiểm thử Media3, PiP và phát nền ổn định 100%."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}
