package com.aeroplay.app.feature.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.aeroplay.app.core.common.FormatUtils
import com.aeroplay.app.core.designsystem.AddToPlaylistDialog
import com.aeroplay.app.core.designsystem.CompactVideoRowCard
import com.aeroplay.app.core.designsystem.ErrorStateCard
import com.aeroplay.app.core.designsystem.LocalAeroPlayStatusColors
import com.aeroplay.app.core.model.PlaybackState
import com.aeroplay.app.core.model.StreamVariant

@OptIn(UnstableApi::class)
@Composable
fun FullPlayerOverlay(
    viewModel: PlayerViewModel,
    onEnterPip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.playbackState.collectAsStateWithLifecycle()
    val player by viewModel.playerFlow.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isCurrentFavorite.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val statusColors = LocalAeroPlayStatusColors.current
    val context = LocalContext.current
    val activity = context as? Activity

    val currentVideo = state.currentVideo ?: return
    val detail = state.currentDetail

    var isLandscapeFullscreen by remember { mutableStateOf(false) }
    var showControlsOverlay by remember { mutableStateOf(true) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    var isUserDraggingSeek by remember { mutableStateOf(false) }
    var dragSeekPositionMs by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler(enabled = state.isFullPlayerOpen && !state.isInPipMode) {
        if (isLandscapeFullscreen) {
            isLandscapeFullscreen = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            viewModel.minimizePlayer()
        }
    }

    // In Picture-in-Picture mode, hide all surrounding UI chrome (Requirement V.5)
    if (state.isInPipMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    if (view.player !== player) {
                        view.player = player
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    if (showQualityDialog) {
        QualitySelectorDialog(
            variants = state.availableVariants,
            selectedVariant = state.selectedStreamVariant,
            isAudioOnly = state.isAudioOnlyMode,
            onDismiss = { showQualityDialog = false },
            onSelectVariant = { variant ->
                viewModel.selectQuality(variant)
                showQualityDialog = false
            },
            onSelectAudioOnly = {
                viewModel.setAudioOnlyMode(true)
                showQualityDialog = false
            }
        )
    }

    if (showSpeedDialog) {
        SpeedSelectorDialog(
            currentSpeed = state.playbackSpeed,
            onDismiss = { showSpeedDialog = false },
            onSelectSpeed = { speed ->
                viewModel.setPlaybackSpeed(speed)
                showSpeedDialog = false
            }
        )
    }

    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            video = currentVideo,
            playlists = playlists,
            onDismiss = { showAddToPlaylistDialog = false },
            onSelectPlaylist = { plId -> viewModel.addVideoToPlaylist(plId, currentVideo) },
            onCreateAndAdd = { name -> viewModel.createPlaylistAndAdd(name, currentVideo) }
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Video / Audio Stage
            val videoBoxModifier = if (isLandscapeFullscreen) {
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            }

            Box(
                modifier = videoBoxModifier.clickable {
                    showControlsOverlay = !showControlsOverlay
                }
            ) {
                if (state.isAudioOnlyMode) {
                    // Audio-Only Mode Visual Stage (genuinely playing audio track to save data)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF09131C)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = detail?.thumbnailUrl ?: currentVideo.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alpha = 0.22f,
                            modifier = Modifier.fillMaxSize()
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = statusColors.audioModeActive.copy(alpha = 0.2f),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Headphones,
                                        contentDescription = null,
                                        tint = statusColors.audioModeActive,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Chế độ Chỉ Âm Thanh (Tiết kiệm dữ liệu)",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White
                            )
                            Text(
                                text = state.selectedAudioTrack?.label ?: "Đang phát luồng âm thanh nền",
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColors.audioModeActive
                            )
                        }
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                keepScreenOn = true
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { playerView ->
                            if (playerView.player !== player) {
                                playerView.player = player
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Buffering Spinner
                if (state.isBuffering || state.isLoadingMetadata) {
                    CircularProgressIndicator(
                        color = statusColors.playButton,
                        modifier = Modifier
                            .size(44.dp)
                            .align(Alignment.Center)
                    )
                }

                // Player Controls Overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControlsOverlay,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        // Top Bar inside video stage
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (isLandscapeFullscreen) {
                                        isLandscapeFullscreen = false
                                        activity?.requestedOrientation =
                                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    } else {
                                        viewModel.minimizePlayer()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Thu nhỏ về Mini Player",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Quality chip in top-right of video
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showQualityDialog = true },
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (state.isAudioOnlyMode) {
                                            "Audio Only"
                                        } else {
                                            state.selectedStreamVariant?.resolutionLabel ?: "Auto"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                IconButton(onClick = onEnterPip) {
                                    Icon(
                                        imageVector = Icons.Filled.PictureInPictureAlt,
                                        contentDescription = "Bật Picture-in-Picture",
                                        tint = Color.White
                                    )
                                }
                            }
                        }

                        // Center Transport Controls (Prev / -10s / PlayPause / +10s / Next)
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.playPrevious() },
                                enabled = state.queue.hasPrevious || state.currentPositionMs > 5_000L
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = "Bài trước",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            IconButton(onClick = { viewModel.skipBackward() }) {
                                Icon(
                                    imageVector = Icons.Filled.Replay10,
                                    contentDescription = "Lùi 10 giây",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Surface(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .clickable { viewModel.togglePlayPause() },
                                shape = CircleShape,
                                color = statusColors.playButton
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (state.isPlaying) "Tạm dừng" else "Phát",
                                        tint = Color.White,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }

                            IconButton(onClick = { viewModel.skipForward() }) {
                                Icon(
                                    imageVector = Icons.Filled.Forward10,
                                    contentDescription = "Tua 10 giây",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.playNext() },
                                enabled = state.queue.hasNext
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = "Bài tiếp theo",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // Bottom Seekbar & Fullscreen Button
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            val totalDuration = state.durationMs.coerceAtLeast(1L)
                            val displayPos = if (isUserDraggingSeek) {
                                dragSeekPositionMs.toLong()
                            } else {
                                state.currentPositionMs
                            }

                            Slider(
                                value = displayPos.toFloat().coerceIn(0f, totalDuration.toFloat()),
                                onValueChange = {
                                    isUserDraggingSeek = true
                                    dragSeekPositionMs = it
                                },
                                onValueChangeFinished = {
                                    viewModel.seekTo(dragSeekPositionMs.toLong())
                                    isUserDraggingSeek = false
                                },
                                valueRange = 0f..totalDuration.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = statusColors.playButton,
                                    activeTrackColor = statusColors.playButton,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${FormatUtils.formatMillis(displayPos)} / ${FormatUtils.formatMillis(state.durationMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )

                                IconButton(
                                    onClick = {
                                        val nextLandscape = !isLandscapeFullscreen
                                        isLandscapeFullscreen = nextLandscape
                                        activity?.requestedOrientation = if (nextLandscape) {
                                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isLandscapeFullscreen) {
                                            Icons.Filled.FullscreenExit
                                        } else {
                                            Icons.Filled.Fullscreen
                                        },
                                        contentDescription = "Toàn màn hình / Xoay ngang",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Metadata, Action Chips, Error Banner, and Related Videos (Hidden in fullscreen landscape)
            if (!isLandscapeFullscreen) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Error state if stream extraction or playback failed
                    if (state.error != null) {
                        item {
                            ErrorStateCard(
                                title = "Không thể phát video này",
                                message = state.error!!.message,
                                technicalDetail = state.error!!.technicalDetail,
                                onRetry = { viewModel.retryPlayback() }
                            )
                        }
                    }

                    // Title & Stats
                    item {
                        Column {
                            Text(
                                text = detail?.title ?: currentVideo.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val statsText = buildList {
                                val views = FormatUtils.formatViewCount(
                                    detail?.viewCount ?: currentVideo.viewCount
                                )
                                if (views.isNotBlank()) add(views)
                                val date = detail?.uploadDateText ?: currentVideo.uploadedText
                                if (date.isNotBlank()) add(date)
                            }.joinToString(" • ")

                            if (statsText.isNotBlank()) {
                                Text(
                                    text = statsText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Action Pills Row (Favorite, Audio-Only, Quality, Speed, PiP, Add to Playlist)
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PlayerActionChip(
                                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                label = if (isFavorite) "Đã thích" else "Yêu thích",
                                activeColor = if (isFavorite) statusColors.favoriteActive else null,
                                onClick = { viewModel.toggleFavorite() }
                            )

                            PlayerActionChip(
                                icon = Icons.Filled.Headphones,
                                label = if (state.isAudioOnlyMode) "Chỉ Âm thanh: BẬT" else "Chỉ Âm thanh",
                                activeColor = if (state.isAudioOnlyMode) statusColors.audioModeActive else null,
                                onClick = { viewModel.setAudioOnlyMode(!state.isAudioOnlyMode) }
                            )

                            PlayerActionChip(
                                icon = Icons.Filled.HighQuality,
                                label = state.selectedStreamVariant?.resolutionLabel ?: "Chất lượng",
                                onClick = { showQualityDialog = true }
                            )

                            PlayerActionChip(
                                icon = Icons.Filled.Speed,
                                label = "${state.playbackSpeed}x",
                                onClick = { showSpeedDialog = true }
                            )

                            PlayerActionChip(
                                icon = Icons.Filled.PictureInPictureAlt,
                                label = "Cửa sổ nổi PiP",
                                onClick = onEnterPip
                            )

                            PlayerActionChip(
                                icon = Icons.Filled.BookmarkAdd,
                                label = "Lưu Playlist",
                                onClick = { showAddToPlaylistDialog = true }
                            )
                        }
                    }

                    // Channel Row & Expandable Description
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { isDescriptionExpanded = !isDescriptionExpanded },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = (detail?.channelAvatarUrl ?: currentVideo.channelAvatarUrl)
                                            .ifBlank { detail?.thumbnailUrl ?: currentVideo.thumbnailUrl },
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = detail?.channelName ?: currentVideo.channelName,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            if (detail?.isChannelVerified == true) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Filled.Verified,
                                                    contentDescription = null,
                                                    tint = statusColors.verifiedBadge,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                        val subs = FormatUtils.formatSubscribers(detail?.subscriberCount ?: -1L)
                                        if (subs.isNotBlank()) {
                                            Text(
                                                text = subs,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Filled.ExpandMore,
                                        contentDescription = "Xem mô tả",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                val desc = detail?.description.orEmpty()
                                if (desc.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = if (isDescriptionExpanded) 100 else 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Experimental Source Risk Notice (Requirement II.10 & V.6)
                    if (currentVideo.sourceType.isExperimental) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = statusColors.experimentalWarning.copy(alpha = 0.14f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.WarningAmber,
                                        contentDescription = null,
                                        tint = statusColors.experimentalWarning,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Chế độ Extractor thử nghiệm: Luồng phát và phát nền phụ thuộc vào khả năng tương thích của nguồn tại thời điểm truy cập.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Related Videos or Queue Section
                    val nextList = detail?.relatedVideos?.ifEmpty { state.queue.items }
                        ?: state.queue.items
                    if (nextList.isNotEmpty()) {
                        item {
                            Text(
                                text = "Video tiếp theo & Liên quan",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        items(
                            items = nextList.filter { it.id != currentVideo.id },
                            key = { "rel_${it.id}" }
                        ) { item ->
                            CompactVideoRowCard(
                                video = item,
                                onClick = {
                                    viewModel.playRelatedOrQueueVideo(
                                        video = item,
                                        queue = nextList,
                                        title = "Video liên quan"
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayerBar(
    state: PlaybackState,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentVideo = state.currentVideo ?: return
    val statusColors = LocalAeroPlayStatusColors.current

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd ||
                value == SwipeToDismissBoxValue.EndToStart
            ) {
                onDismiss()
                true
            } else {
                false
            }
        }
    )

    LaunchedEffect(currentVideo.id) {
        dismissState.reset()
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {},
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable { onExpand() },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 10.dp,
            shadowElevation = 8.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 68.dp, height = 42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black)
                    ) {
                        AsyncImage(
                            model = currentVideo.thumbnailUrl,
                            contentDescription = currentVideo.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (state.isAudioOnlyMode) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Headphones,
                                    contentDescription = null,
                                    tint = statusColors.audioModeActive,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentVideo.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (state.isAudioOnlyMode) {
                                "Chỉ âm thanh • ${currentVideo.channelName}"
                            } else {
                                currentVideo.channelName
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.isAudioOnlyMode) {
                                statusColors.audioModeActive
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onTogglePlayPause) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Phát / Tạm dừng",
                            tint = statusColors.playButton
                        )
                    }

                    if (state.queue.hasNext) {
                        IconButton(onClick = onNext) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Bài tiếp theo",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Đóng trình phát",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val progress = if (state.durationMs > 0L) {
                    (state.currentPositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = statusColors.playButton,
                    trackColor = MaterialTheme.colorScheme.surface
                )
            }
        }
    }
}

@Composable
private fun PlayerActionChip(
    icon: ImageVector,
    label: String,
    activeColor: Color? = null,
    onClick: () -> Unit
) {
    val bgColor = activeColor?.copy(alpha = 0.18f)
        ?: MaterialTheme.colorScheme.surfaceVariant
    val contentColor = activeColor ?: MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable { onClick() },
        shape = RoundedCornerShape(50),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun QualitySelectorDialog(
    variants: List<StreamVariant>,
    selectedVariant: StreamVariant?,
    isAudioOnly: Boolean,
    onDismiss: () -> Unit,
    onSelectVariant: (StreamVariant) -> Unit,
    onSelectAudioOnly: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chất lượng phát thực tế") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Chỉ hiển thị các luồng thực sự có sẵn từ nguồn hiện tại (không giả lập 1080p/4K khi nguồn không cung cấp):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (variants.isEmpty()) {
                    Text(
                        text = "Không có biến thể video riêng biệt.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    variants.forEach { variant ->
                        val selected = !isAudioOnly && selectedVariant?.id == variant.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectVariant(variant) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = variant.resolutionLabel,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // Audio-Only option
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectAudioOnly() },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isAudioOnly) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Chỉ Âm thanh (Audio-Only Stream)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (isAudioOnly) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
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

@Composable
private fun SpeedSelectorDialog(
    currentSpeed: Float,
    onDismiss: () -> Unit,
    onSelectSpeed: (Float) -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tốc độ phát") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                speeds.forEach { speed ->
                    val selected = kotlin.math.abs(currentSpeed - speed) < 0.01f
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectSpeed(speed) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (speed == 1.0f) "1.0x (Chuẩn)" else "${speed}x",
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
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
