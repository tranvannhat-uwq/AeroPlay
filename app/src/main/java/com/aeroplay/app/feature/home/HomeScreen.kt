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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
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
import com.aeroplay.app.core.designsystem.RemotePlaylistDialog
import com.aeroplay.app.core.designsystem.LocalFloatingPlayerInset
import com.aeroplay.app.core.designsystem.LocalAeroPlayStatusColors
import com.aeroplay.app.core.designsystem.SkeletonVideoCard
import com.aeroplay.app.core.designsystem.SourceModePill
import com.aeroplay.app.core.designsystem.VideoFeedCard
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.data.youtube.YouTubePolicyFeatureFlags
import com.aeroplay.app.core.model.PlaybackPosition
import com.aeroplay.app.core.model.PlaylistItem
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
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val remotePlaylist by viewModel.remotePlaylistState.collectAsStateWithLifecycle()
    val refreshState = rememberPullToRefreshState()
    val floatingInset = LocalFloatingPlayerInset.current

    var playlistTargetVideo by remember { mutableStateOf<VideoItem?>(null) }
    var showSourceSwitchDialog by remember { mutableStateOf(false) }

    remotePlaylist?.let { state ->
        RemotePlaylistDialog(
            state = state,
            onDismiss = viewModel::closeRemotePlaylist,
            onPlay = viewModel::playRemotePlaylistVideo
        )
    }

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
                                text = if (settings.activeSourceType.isExperimental && YouTubePolicyFeatureFlags.apiOnlyMode) {
                                    "Tìm video YouTube..."
                                } else if (settings.activeSourceType.isExperimental) {
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

            if (continueWatching.isNotEmpty()) {
                item {
                    HorizontalVideoCarouselSection(
                        title = "Tiếp tục xem",
                        icon = Icons.Filled.History,
                        videos = continueWatching,
                        savedPositions = savedPositions,
                        onVideoClick = { video -> viewModel.playVideo(video, continueWatching, "Tiếp tục xem") }
                    )
                }
            }

            // Recommendations from local history, favorites and recent searches.
            if (personalized.videos.isNotEmpty()) {
                item {
                    HorizontalVideoCarouselSection(
                        title = "Dành cho bạn",
                        icon = Icons.Filled.AutoAwesome,
                        videos = personalized.videos,
                        savedPositions = savedPositions,
                        onRecommendationImpression = viewModel::recordRecommendationImpression,
                        onRecommendationAction = viewModel::actOnRecommendation,
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

            if (personalized.musicVideos.isNotEmpty()) {
                item {
                    HorizontalVideoCarouselSection(
                        title = if (personalized.hasMusicStyleContext) "Cùng phong cách" else "Khám phá nhạc Việt",
                        icon = Icons.Filled.AutoAwesome,
                        videos = personalized.musicVideos,
                        savedPositions = savedPositions,
                        onRecommendationImpression = viewModel::recordRecommendationImpression,
                        onRecommendationAction = viewModel::actOnRecommendation,
                        onVideoClick = { video -> viewModel.playVideo(video, personalized.musicVideos,
                            "Cùng phong cách", radioMode = true) }
                    )
                }
            }

            if (personalized.popularMusicVideos.isNotEmpty()) {
                item {
                    HorizontalVideoCarouselSection(
                        title = if (personalized.hasMusicStyleContext) "Nhạc nhiều lượt xem phù hợp" else "Nhạc nhiều lượt xem",
                        icon = Icons.Filled.PlayArrow,
                        videos = personalized.popularMusicVideos,
                        savedPositions = savedPositions,
                        onRecommendationImpression = viewModel::recordRecommendationImpression,
                        onRecommendationAction = viewModel::actOnRecommendation,
                        onVideoClick = { video -> viewModel.playVideo(video, personalized.popularMusicVideos,
                            "Nhạc phổ biến cùng phong cách", radioMode = true) }
                    )
                }
            }

            if (personalized.musicPlaylists.isNotEmpty()) {
                item {
                    RemotePlaylistCarousel(
                        playlists = personalized.musicPlaylists,
                        title = if (personalized.hasMusicStyleContext) "Playlist cùng phong cách" else "Playlist nhạc Việt",
                        onPlaylistClick = viewModel::openRemotePlaylist
                    )
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
                        text = if (settings.activeSourceType.isExperimental && YouTubePolicyFeatureFlags.apiOnlyMode) {
                            "YouTube • Video phổ biến tại Việt Nam"
                        } else if (settings.activeSourceType.isExperimental) {
                            "Khám phá trên YouTube (Extractor)"
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
                            recommendationActions = {
                                RecommendationActionsMenu(video, viewModel::actOnRecommendation)
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemotePlaylistCarousel(
    playlists: List<PlaylistItem>,
    title: String,
    onPlaylistClick: (PlaylistItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(playlists, key = { "remote_playlist_${it.id}" }) { playlist ->
                Card(
                    modifier = Modifier.width(220.dp).clickable { onPlaylistClick(playlist) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        AsyncImage(
                            model = playlist.thumbnailUrl,
                            contentDescription = playlist.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        )
                        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(playlist.title, style = MaterialTheme.typography.titleSmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val metadata = buildList {
                                if (playlist.uploaderName.isNotBlank()) add(playlist.uploaderName)
                                if (playlist.streamCount > 0L) add("${playlist.streamCount} bài")
                            }.joinToString(" • ")
                            if (metadata.isNotBlank()) {
                                Text(metadata, style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
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
    onVideoClick: (VideoItem) -> Unit,
    onRecommendationImpression: ((VideoItem) -> Unit)? = null,
    onRecommendationAction: ((VideoItem, RecommendationAction) -> Unit)? = null
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
                val cardContent: @Composable () -> Unit = {
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
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
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
                                        modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                                        color = statusColors.playButton,
                                        trackColor = Color.White.copy(alpha = 0.3f)
                                    )
                                }
                                if (onRecommendationAction != null) {
                                    Box(Modifier.align(Alignment.TopEnd).padding(3.dp)) {
                                        RecommendationActionsMenu(video, onRecommendationAction)
                                    }
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
                if (onRecommendationImpression != null) {
                    RecommendationVisibilityTracker(video, onRecommendationImpression) { cardContent() }
                } else {
                    cardContent()
                }
            }
        }
    }
}

@Composable
private fun RecommendationActionsMenu(
    video: VideoItem,
    onAction: (VideoItem, RecommendationAction) -> Unit
) {
    var expanded by remember(video.id) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Thao tác đề xuất", tint = MaterialTheme.colorScheme.onSurface)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                RecommendationAction.NOT_INTERESTED to "Không quan tâm",
                RecommendationAction.BLOCK_CHANNEL to "Không đề xuất kênh này",
                RecommendationAction.MARK_WATCHED to "Đã xem",
                RecommendationAction.WATCH_LATER to "Xem sau"
            ).forEach { (action, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onAction(video, action)
                    }
                )
            }
        }
    }
}

@Composable
private fun RecommendationVisibilityTracker(
    video: VideoItem,
    onImpression: (VideoItem) -> Unit,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val latestCallback by rememberUpdatedState(onImpression)
    var visibleFraction by remember(video.id) { mutableFloatStateOf(0f) }
    LaunchedEffect(video.id) {
        snapshotFlow { visibleFraction >= 0.5f }
            .distinctUntilChanged()
            .collectLatest { isVisibleEnough ->
                if (isVisibleEnough) {
                    delay(1_000L)
                    if (visibleFraction >= 0.5f) latestCallback(video)
                }
            }
    }
    Box(modifier = Modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        val screenWidth = view.rootView.width.toFloat()
        val screenHeight = view.rootView.height.toFloat()
        visibleFraction = if (bounds.width > 0f && bounds.height > 0f && screenWidth > 0f && screenHeight > 0f) {
            val visibleWidth = (minOf(bounds.right, screenWidth) - maxOf(bounds.left, 0f)).coerceAtLeast(0f)
            val visibleHeight = (minOf(bounds.bottom, screenHeight) - maxOf(bounds.top, 0f)).coerceAtLeast(0f)
            ((visibleWidth * visibleHeight) / (bounds.width * bounds.height)).coerceIn(0f, 1f)
        } else 0f
    }) { content() }
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

                ContentSourceType.onlineSources

                    .forEach { source ->
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
                                text = if (source.isExperimental && YouTubePolicyFeatureFlags.apiOnlyMode) {
                                        "Dữ liệu YouTube thật và trình phát YouTube chính thức trong AeroPlay."
                                    } else if (source.isExperimental) {
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
