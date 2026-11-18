package com.aeroplay.app.feature.player

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.text.HtmlCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.aeroplay.app.core.common.FormatUtils
import com.aeroplay.app.BuildConfig
import com.aeroplay.app.core.designsystem.AddToPlaylistDialog
import com.aeroplay.app.core.designsystem.CompactVideoRowCard
import com.aeroplay.app.core.designsystem.ErrorStateCard
import com.aeroplay.app.core.designsystem.GlassBackground
import com.aeroplay.app.core.designsystem.GlassSurface
import com.aeroplay.app.core.designsystem.LocalAeroPlayStatusColors
import com.aeroplay.app.core.designsystem.AeroPlayBrand
import androidx.compose.material.icons.filled.Block
import com.aeroplay.app.core.model.PlaybackState
import com.aeroplay.app.data.youtube.YouTubePolicyFeatureFlags
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Composable
fun FullPlayerOverlay(
    viewModel: PlayerViewModel,
    videoOutput: PlayerVideoOutput,
    onEnterPip: () -> Unit,
    modifier: Modifier = Modifier,
    openQueue: Boolean = false,
    onQueueOpened: () -> Unit = {}
) {
    val state by viewModel.playbackState.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val player by viewModel.playerFlow.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isCurrentFavorite.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val statusColors = LocalAeroPlayStatusColors.current
    val context = LocalContext.current
    val restrictionNotice by viewModel.restrictionNotice.collectAsStateWithLifecycle()
    LaunchedEffect(restrictionNotice) {
        restrictionNotice?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearRestrictionNotice()
        }
    }
    val activity = context as? Activity

    val currentVideo = state.currentVideo ?: return
    val detail = state.currentDetail
    PlaybackScreenAwakeEffect(
        activity = activity,
        player = player,
        enabled = state.isFullPlayerOpen && !state.isInPipMode &&
            !state.isAudioOnlyMode && state.error == null
    )
    val externalYouTubeUri = remember(currentVideo.id, currentVideo.url) {
        if (YouTubePolicyFeatureFlags.apiOnlyMode && YouTubePolicyFeatureFlags.isYouTubeVideo(currentVideo)) {
            Uri.parse(currentVideo.url).takeIf { YouTubePolicyFeatureFlags.isYouTubeUrl(currentVideo.url) }
                ?: Uri.Builder()
                    .scheme("https")
                    .authority("www.youtube.com")
                    .appendPath("watch")
                    .appendQueryParameter("v", currentVideo.id)
                    .build()
        } else null
    }

    fun openYouTubeExternally() {
        val uri = externalYouTubeUri ?: return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Không tìm thấy ứng dụng hoặc trình duyệt để mở video.", Toast.LENGTH_LONG).show()
        }
    }

    var landscapeRequested by remember { mutableStateOf(false) }
    val orientation = LocalConfiguration.current.orientation
    val isLandscapeFullscreen = landscapeRequested ||
        orientation == Configuration.ORIENTATION_LANDSCAPE

    fun exitLandscape() {
        landscapeRequested = false
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var draggingPlayer by remember { mutableStateOf(false) }
    val (dismissDistance, flingDistance, minFlingDrag) = with(LocalDensity.current) {
        Triple(76.dp.toPx(), 820.dp.toPx(), 24.dp.toPx())
    }
    val playerOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = if (draggingPlayer || statusColors.reduceMotion) snap() else spring(dampingRatio = 0.86f),
        label = "Player drag"
    )
    var showControlsOverlay by remember(currentVideo.id) { mutableStateOf(true) }
    var controlsInteraction by remember { mutableIntStateOf(0) }
    var touchingPlayer by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showPlayerSettings by remember(currentVideo.id) { mutableStateOf(false) }
    val immersive = isLandscapeFullscreen && state.isFullPlayerOpen && !state.isInPipMode
    var touchLockRequested by rememberSaveable { mutableStateOf(false) }
    val isTouchLocked = immersive && touchLockRequested
    val detailsScroll = rememberLazyListState()
    val nextList = state.queue.items.drop(state.queue.currentIndex + 1).filter { it.id != currentVideo.id }
    LaunchedEffect(openQueue, isLandscapeFullscreen, isTouchLocked, state.isFullPlayerOpen, nextList.size) {
        if (!openQueue || !state.isFullPlayerOpen) return@LaunchedEffect
        if (isTouchLocked || nextList.isEmpty()) {
            onQueueOpened()
        } else if (isLandscapeFullscreen) {
            exitLandscape()
        } else {
            val total = snapshotFlow { detailsScroll.layoutInfo.totalItemsCount }.first { it > nextList.size }
            detailsScroll.scrollToItem(total - nextList.size - 1)
            onQueueOpened()
        }
    }
    LaunchedEffect(immersive) {
        if (!immersive) touchLockRequested = false
    }
    var isOnTheGoMode by rememberSaveable(currentVideo.id) { mutableStateOf(false) }
    var showYouTubeMusicFallback by remember { mutableStateOf(false) }
    var isDescriptionExpanded by rememberSaveable(currentVideo.sourceType.name, currentVideo.id) { mutableStateOf(false) }
    val description = remember(detail?.description) {
        val raw = detail?.description.orEmpty().replace("\r\n", "\n").replace('\r', '\n')
        if (descriptionHtmlTag.containsMatchIn(raw)) {
            HtmlCompat.fromHtml(raw.replace("\n", "<br>"), HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
        } else raw.trim()
    }
    val channelName = detail?.channelName.orEmpty().ifBlank { currentVideo.channelName }
    val channelUri = remember(detail?.channelUrl, currentVideo.channelUrl) {
        sequenceOf(detail?.channelUrl.orEmpty(), currentVideo.channelUrl)
            .map { Uri.parse(it.trim()) }
            .firstOrNull { (it.scheme == "https" || it.scheme == "http") && !it.host.isNullOrBlank() }
    }

    var isUserDraggingSeek by remember { mutableStateOf(false) }
    var dragSeekPositionMs by remember { mutableFloatStateOf(0f) }

    fun keepControlsVisible() {
        if (isTouchLocked) return
        controlsInteraction++
        showControlsOverlay = true
    }

    val canAutoHideControls = showControlsOverlay && state.isPlaying &&
        !state.isBuffering && !state.isLoadingMetadata && state.error == null &&
        !isUserDraggingSeek && !draggingPlayer && !touchingPlayer &&
        !showAddToPlaylistDialog && !showPlayerSettings &&
        !isTouchLocked &&
        state.isFullPlayerOpen && !state.isInPipMode

    LaunchedEffect(canAutoHideControls, controlsInteraction, currentVideo.id, isLandscapeFullscreen) {
        if (canAutoHideControls) {
            delay(4_000L)
            showControlsOverlay = false
        }
    }

    LaunchedEffect(state.isPlaying, state.error) {
        if (!isTouchLocked && (!state.isPlaying || state.error != null)) showControlsOverlay = true
    }

    LaunchedEffect(state.isFullPlayerOpen, state.isInPipMode) {
        if (state.isFullPlayerOpen || state.isInPipMode) dragOffset = 0f
        if (!state.isFullPlayerOpen || state.isInPipMode) showPlayerSettings = false
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(activity, immersive) {
        val window = activity?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousBehavior = bars?.systemBarsBehavior
        if (immersive) {
            bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            bars?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (immersive) bars?.show(WindowInsetsCompat.Type.systemBars())
            previousBehavior?.let { bars?.systemBarsBehavior = it }
        }
    }

    BackHandler(enabled = state.isFullPlayerOpen && !state.isInPipMode) {
        if (isTouchLocked) return@BackHandler
        if (isLandscapeFullscreen) {
            exitLandscape()
        } else {
            viewModel.minimizePlayer()
        }
    }

    // In Picture-in-Picture mode, hide all surrounding UI chrome (Requirement V.5)
    if (state.isInPipMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AeroPlayBrand.PlayerScrim),
            contentAlignment = Alignment.Center
        ) {
            PlayerVideoSurface(
                player = player,
                output = videoOutput,
                active = true,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
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

    Box(
        modifier = modifier.fillMaxSize()
            .then(if (isLandscapeFullscreen) Modifier else Modifier.statusBarsPadding())
            .graphicsLayer {
                translationY = playerOffset
                val shrink = if (statusColors.reduceMotion) 0f else (playerOffset / size.height.coerceAtLeast(1f)).coerceAtMost(0.15f)
                scaleX = 1f - shrink
                scaleY = 1f - shrink
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
    ) {
        GlassBackground()
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Video / Audio Stage
            val videoBoxModifier = if (isLandscapeFullscreen) {
                Modifier
                    .fillMaxSize()
                    .background(AeroPlayBrand.PlayerScrim)
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(AeroPlayBrand.PlayerScrim)
            }

            Box(
                modifier = videoBoxModifier
                    .testTag("FullVideoStage")
                    .pointerInput(currentVideo.id, isTouchLocked) {
                        if (isTouchLocked) return@pointerInput
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            touchingPlayer = true
                            try {
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                } while (event.changes.any { it.pressed })
                            } finally {
                                touchingPlayer = false
                                controlsInteraction++
                            }
                        }
                    }
                    .draggable(
                        state = rememberDraggableState { delta ->
                            dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                        },
                        orientation = Orientation.Vertical,
                        enabled = !isLandscapeFullscreen && state.isFullPlayerOpen,
                        onDragStarted = { draggingPlayer = true },
                        onDragStopped = { velocity ->
                            draggingPlayer = false
                            if (dragOffset >= dismissDistance || (dragOffset >= minFlingDrag && velocity > flingDistance)) {
                                viewModel.minimizePlayer()
                            } else {
                                dragOffset = 0f
                            }
                        }
                    )
                    .clickable(enabled = !isTouchLocked) { showControlsOverlay = !showControlsOverlay }
            ) {
                if (state.isAudioOnlyMode) {
                    // Audio-Only Mode Visual Stage (genuinely playing audio track to save data)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AeroPlayBrand.Midnight),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = detail?.thumbnailUrl ?: currentVideo.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alpha = 0.22f,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (!isTouchLocked && isOnTheGoMode) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                AsyncImage(
                                    model = detail?.thumbnailUrl ?: currentVideo.thumbnailUrl,
                                    contentDescription = "Ảnh bìa ${detail?.title ?: currentVideo.title}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(94.dp).clip(RoundedCornerShape(18.dp))
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(detail?.title ?: currentVideo.title, style = MaterialTheme.typography.titleSmall,
                                    color = AeroPlayBrand.PlayerForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = viewModel::playPrevious) {
                                        Icon(Icons.Filled.SkipPrevious, "Bài trước", tint = AeroPlayBrand.PlayerForeground)
                                    }
                                    IconButton(onClick = viewModel::togglePlayPause, modifier = Modifier.size(52.dp)) {
                                        Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            if (state.isPlaying) "Tạm dừng" else "Phát", tint = AeroPlayBrand.PlayerForeground,
                                            modifier = Modifier.size(34.dp))
                                    }
                                    IconButton(onClick = viewModel::playNext) {
                                        Icon(Icons.Filled.SkipNext, "Bài tiếp", tint = AeroPlayBrand.PlayerForeground)
                                    }
                                }
                                TextButton(onClick = {
                                    isOnTheGoMode = false
                                    viewModel.setAudioOnlyMode(false)
                                }) { Text("Trở về xem video") }
                                if (state.selectedAudioTrack == null) {
                                    Text("Nguồn không có luồng âm thanh riêng; chế độ này vẫn dùng luồng video hiện tại.",
                                        style = MaterialTheme.typography.labelSmall, color = statusColors.audioModeActive, maxLines = 2)
                                }
                            }
                        } else if (!isTouchLocked) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                                Surface(shape = CircleShape, color = statusColors.audioModeActive.copy(alpha = 0.2f), modifier = Modifier.size(64.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Headphones, null, tint = statusColors.audioModeActive, modifier = Modifier.size(34.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Chế độ Chỉ Âm Thanh (Tiết kiệm dữ liệu)", style = MaterialTheme.typography.titleSmall, color = AeroPlayBrand.PlayerForeground)
                                Text(state.selectedAudioTrack?.label ?: "Đang phát luồng âm thanh nền", style = MaterialTheme.typography.bodySmall, color = statusColors.audioModeActive)
                            }
                        }
                    }
                } else {
                    val showAmbient = appSettings.ambientLightingEnabled && state.isFullPlayerOpen &&
                        !state.isInPipMode && !state.isAudioOnlyMode
                    Box(Modifier.fillMaxSize()) {
                        if (showAmbient) {
                            val ambientCover = detail?.thumbnailUrl ?: currentVideo.thumbnailUrl
                            Crossfade(
                                targetState = ambientCover,
                                animationSpec = tween(if (statusColors.reduceMotion) 0 else 1_000),
                                label = "Player ambient cover",
                                modifier = Modifier.fillMaxSize()
                            ) { cover ->
                                AsyncImage(
                                    model = cover,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    alpha = 0.82f,
                                    modifier = Modifier.fillMaxSize().blur(if (statusColors.reduceMotion) 18.dp else 28.dp)
                                )
                            }
                        }
                        PlayerVideoSurface(
                            player = player,
                            output = videoOutput,
                            active = state.isFullPlayerOpen,
                            modifier = if (showAmbient) Modifier.fillMaxSize().padding(12.dp).clip(RoundedCornerShape(14.dp)) else Modifier.fillMaxSize()
                        )
                    }
                }

                // Buffering Spinner
                if (!isTouchLocked && (state.isBuffering || state.isLoadingMetadata)) {
                    CircularProgressIndicator(
                        color = statusColors.playButton,
                        modifier = Modifier
                            .size(44.dp)
                            .align(Alignment.Center)
                    )
                }

                // Player Controls Overlay
                if (isTouchLocked) {
                    IconButton(
                        onClick = {
                            touchLockRequested = false
                            controlsInteraction++
                            showControlsOverlay = true
                        },
                        modifier = Modifier.align(Alignment.TopEnd)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "Mở khóa màn hình",
                            tint = AeroPlayBrand.PlayerForeground.copy(alpha = 0.55f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else androidx.compose.animation.AnimatedVisibility(
                    visible = showControlsOverlay,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize().testTag("VideoControls")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(
                                AeroPlayBrand.PlayerScrim.copy(alpha = 0.67f),
                                AeroPlayBrand.Navy.copy(alpha = 0.32f),
                                AeroPlayBrand.PlayerScrim.copy(alpha = 0.76f)
                            )))
                    ) {
                        // Top Bar inside video stage
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .then(if (isLandscapeFullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (isLandscapeFullscreen) {
                                        exitLandscape()
                                    } else {
                                        viewModel.minimizePlayer()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Thu nhỏ về Mini Player",
                                    tint = AeroPlayBrand.PlayerForeground,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = appSettings.autoPlaySimilarMusic,
                                    onCheckedChange = {
                                        keepControlsVisible()
                                        viewModel.setAutoplay(it)
                                    },
                                    modifier = Modifier.semantics { contentDescription = "Tự động phát tiếp" },
                                    thumbContent = {
                                        Icon(Icons.Filled.SkipNext, null, modifier = Modifier.size(SwitchDefaults.IconSize))
                                    }
                                )
                                IconButton(
                                    onClick = {
                                        keepControlsVisible()
                                        showPlayerSettings = true
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Settings,
                                        contentDescription = "Cài đặt phát",
                                        tint = AeroPlayBrand.PlayerForeground,
                                        modifier = Modifier.size(25.dp)
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
                                onClick = {
                                    keepControlsVisible()
                                    viewModel.playPrevious()
                                },
                                enabled = state.queue.hasPrevious || state.currentPositionMs > 5_000L
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = "Bài trước",
                                    tint = AeroPlayBrand.PlayerForeground,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            IconButton(onClick = {
                                keepControlsVisible()
                                viewModel.skipBackward()
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Replay10,
                                    contentDescription = "Lùi 10 giây",
                                    tint = AeroPlayBrand.PlayerForeground,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Surface(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        keepControlsVisible()
                                        viewModel.togglePlayPause()
                                    },
                                shape = CircleShape,
                                color = statusColors.playButton
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (state.isPlaying) "Tạm dừng" else "Phát",
                                        tint = statusColors.onPlayButton,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }

                            IconButton(onClick = {
                                keepControlsVisible()
                                viewModel.skipForward()
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Forward10,
                                    contentDescription = "Tua 10 giây",
                                    tint = AeroPlayBrand.PlayerForeground,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    keepControlsVisible()
                                    viewModel.playNext()
                                },
                                enabled = state.queue.hasNext
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = "Bài tiếp theo",
                                    tint = AeroPlayBrand.PlayerForeground,
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
                                    inactiveTrackColor = AeroPlayBrand.PlayerForeground.copy(alpha = 0.35f)
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
                                    color = AeroPlayBrand.PlayerForeground
                                )

                                IconButton(
                                    onClick = {
                                        if (isLandscapeFullscreen) {
                                            exitLandscape()
                                        } else {
                                            landscapeRequested = true
                                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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
                                        tint = AeroPlayBrand.PlayerForeground
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
                    state = detailsScroll,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Error state if stream extraction or playback failed
                    if (state.error != null) {
                        item {
                            Column {
                                ErrorStateCard(
                                    title = "Không thể phát video này",
                                    message = state.error!!.message,
                                    technicalDetail = state.error!!.technicalDetail,
                                    onRetry = { viewModel.retryPlayback() }
                                )
                                if (externalYouTubeUri != null) {
                                    TextButton(
                                        onClick = { openYouTubeExternally() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Mở video bằng ứng dụng YouTube")
                                    }
                                }
                            }
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

                    // Actions below the video
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
                                iconOnly = true,
                                activeColor = if (isFavorite) statusColors.favoriteActive else null,
                                onClick = { viewModel.toggleFavorite() }
                            )

                            PlayerActionChip(
                                icon = Icons.Filled.Headphones,
                                label = if (state.isAudioOnlyMode) "Chỉ âm thanh: Bật" else "Chỉ âm thanh: Tắt",
                                iconOnly = true,
                                activeColor = if (state.isAudioOnlyMode) statusColors.audioModeActive else null,
                                onClick = { viewModel.setAudioOnlyMode(!state.isAudioOnlyMode) }
                            )

                            PlayerActionChip(
                                icon = Icons.Filled.Block,
                                label = "Hạn chế",
                                onClick = viewModel::restrictCurrentVideo
                            )

                            PlayerActionChip(
                                icon = Icons.Filled.BookmarkAdd,
                                label = "Lưu Playlist",
                                onClick = { showAddToPlaylistDialog = true }
                            )
                        }
                    }

                    if (channelName.isNotBlank() || description.isNotBlank()) {
                        item {
                            GlassSurface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                elevation = 2.dp
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val channelLink = if (channelUri != null) {
                                            Modifier.clip(RoundedCornerShape(8.dp)).clickable(
                                                onClickLabel = "Mở trang kênh"
                                            ) {
                                                try {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, channelUri))
                                                } catch (_: ActivityNotFoundException) {
                                                    Toast.makeText(context, "Không tìm thấy ứng dụng để mở trang kênh.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } else Modifier
                                        Row(
                                            modifier = Modifier.weight(1f).then(channelLink),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = detail?.channelAvatarUrl.orEmpty()
                                                    .ifBlank { currentVideo.channelAvatarUrl }
                                                    .ifBlank { currentVideo.thumbnailUrl },
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(40.dp).clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = channelName,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    if (detail?.isChannelVerified == true) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Icon(Icons.Filled.Verified, null, tint = statusColors.verifiedBadge,
                                                            modifier = Modifier.size(15.dp))
                                                    }
                                                }
                                                val subs = FormatUtils.formatSubscribers(detail?.subscriberCount ?: -1L)
                                                if (subs.isNotBlank()) {
                                                    Text(subs, style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                        if (description.isNotBlank()) {
                                            IconButton(onClick = { isDescriptionExpanded = !isDescriptionExpanded }) {
                                                Icon(
                                                    imageVector = if (isDescriptionExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                    contentDescription = if (isDescriptionExpanded) "Thu gọn mô tả" else "Xem thêm mô tả",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    if (description.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Show the same ordered queue that powers Next and automatic playback.
                    if (nextList.isNotEmpty()) {
                        item {
                            Text(
                                text = "Tiếp theo",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        items(
                            items = nextList,
                            key = { "rel_${it.sourceType.name}_${it.id}" }
                        ) { item ->
                            CompactVideoRowCard(
                                video = item,
                                onClick = { viewModel.playQueuedVideo(item) },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.restrictUpcomingVideo(item) }) {
                                        Icon(Icons.Filled.Block, contentDescription = "Hạn chế ${item.title}")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPlayerSettings && !isTouchLocked) {
        PlayerSettingsSheet(
            currentQuality = if (state.isAudioOnlyMode) "Chỉ âm thanh" else state.selectedStreamVariant?.resolutionLabel ?: "Theo chất lượng mặc định",
            selectedVariantId = state.selectedStreamVariant?.id,
            variants = state.availableVariants,
            isAudioOnly = state.isAudioOnlyMode,
            playbackSpeed = state.playbackSpeed,
            subtitles = detail?.subtitleTracks.orEmpty(),
            selectedSubtitle = state.selectedSubtitleTrack,
            isLive = detail?.isLive == true || currentVideo.isLive,
            repeatEnabled = state.isRepeatOneEnabled,
            ambientEnabled = appSettings.ambientLightingEnabled,
            stableVolumeEnabled = appSettings.stableVolumeEnabled,
            stableVolumeError = state.stableVolumeEffectError,
            isOnTheGo = isOnTheGoMode,
            sleepDeadlineRealtimeMs = state.sleepTimerDeadlineRealtimeMs,
            sleepAtEndOfVideo = state.sleepTimerEndsAfterCurrentVideo,
            canOpenYouTubeMusic = currentVideo.sourceType.isExperimental && YouTubePolicyFeatureFlags.isYouTubeVideo(currentVideo),
            feedbackUrl = "https://github.com/${BuildConfig.UPDATE_REPOSITORY}/issues/new",
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            deviceSummary = "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            onDismiss = { showPlayerSettings = false; keepControlsVisible() },
            onQuality = { viewModel.selectQuality(it); showPlayerSettings = false; keepControlsVisible() },
            onDefaultQuality = { viewModel.setDefaultQuality(); showPlayerSettings = false; keepControlsVisible() },
            onAudioOnly = { enabled -> viewModel.setAudioOnlyMode(enabled); showPlayerSettings = false; keepControlsVisible() },
            onSpeed = { viewModel.setPlaybackSpeed(it); showPlayerSettings = false; keepControlsVisible() },
            onSubtitle = { viewModel.selectSubtitle(it); showPlayerSettings = false; keepControlsVisible() },
            onLockScreen = {
                if (immersive) {
                    showControlsOverlay = false
                    touchLockRequested = true
                }
            },
            canLockScreen = immersive,
            onEnterPip = onEnterPip,
            onTheGo = { enabled ->
                isOnTheGoMode = enabled
                viewModel.setAudioOnlyMode(enabled)
            },
            onRepeat = viewModel::setRepeatOne,
            onAmbient = viewModel::setAmbientLighting,
            onStableVolume = viewModel::setStableVolume,
            onSleepMinutes = { if (it == 0) viewModel.cancelSleepTimer() else viewModel.setSleepTimer(it) },
            onSleepAtEnd = viewModel::setSleepAtEndOfCurrentVideo,
            onOpenYouTubeMusic = {
                showPlayerSettings = false
                val musicUri = Uri.Builder().scheme("https").authority("music.youtube.com").path("watch")
                    .appendQueryParameter("v", currentVideo.id).build()
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, musicUri).setPackage("com.google.android.apps.youtube.music"))
                    if (state.isPlaying) viewModel.togglePlayPause()
                } catch (_: ActivityNotFoundException) {
                    showYouTubeMusicFallback = true
                }
            },
            onOpenFeedback = { baseUrl ->
                val device = "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                val body = "AeroPlay ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n$device\n\nMô tả vấn đề hoặc đề xuất:"
                val feedbackUri = Uri.parse(baseUrl).buildUpon()
                    .appendQueryParameter("title", "Phản hồi AeroPlay")
                    .appendQueryParameter("body", body)
                    .build()
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, feedbackUri)) }
                    .onFailure { Toast.makeText(context, "Không mở được GitHub. Hãy thử lại sau.", Toast.LENGTH_SHORT).show() }
            }
        )
    }

    if (showYouTubeMusicFallback) {
        AlertDialog(
            onDismissRequest = { showYouTubeMusicFallback = false },
            title = { Text("Không tìm thấy YouTube Music") },
            text = { Text("Bạn có thể mở trang nghe trên web. AeroPlay chỉ tạm dừng sau khi trang web mở được.") },
            confirmButton = {
                TextButton(onClick = {
                    showYouTubeMusicFallback = false
                    val uri = Uri.Builder().scheme("https").authority("music.youtube.com").path("watch")
                        .appendQueryParameter("v", currentVideo.id).build()
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        if (state.isPlaying) viewModel.togglePlayPause()
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "Không mở được liên kết YouTube Music.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Mở web") }
            },
            dismissButton = { TextButton(onClick = { showYouTubeMusicFallback = false }) { Text("Ở lại") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayerBar(
    state: PlaybackState,
    player: Player?,
    videoOutput: PlayerVideoOutput,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentVideo = state.currentVideo ?: return
    val statusColors = LocalAeroPlayStatusColors.current
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val gestureThreshold = with(LocalDensity.current) { 34.dp.toPx() }
    val dragLimit = with(LocalDensity.current) { 62.dp.toPx() }
    val offset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = if (dragging || statusColors.reduceMotion) snap() else spring(dampingRatio = 0.83f),
        label = "Mini player drag"
    )

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

    Box(modifier.fillMaxWidth().padding(horizontal = 18.dp), contentAlignment = Alignment.CenterEnd) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {},
            modifier = Modifier.widthIn(max = 216.dp).fillMaxWidth()
        ) {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("FloatingVideoPlayer")
                    .graphicsLayer { translationY = offset }
                    .draggable(
                        state = rememberDraggableState { delta ->
                            dragOffset = (dragOffset + delta * 0.65f).coerceIn(-dragLimit, dragLimit)
                        },
                        orientation = Orientation.Vertical,
                        onDragStarted = { dragging = true },
                        onDragStopped = {
                            dragging = false
                            when {
                                dragOffset <= -gestureThreshold -> onExpand()
                                dragOffset >= gestureThreshold -> onDismiss()
                            }
                            dragOffset = 0f
                        }
                    ),
                shape = RoundedCornerShape(22.dp),
                elevation = 11.dp
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(5.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(17.dp))
                            .background(AeroPlayBrand.PlayerScrim)
                            .clickable(onClickLabel = "Mở rộng trình phát", onClick = onExpand)
                    ) {
                        if (state.isAudioOnlyMode || state.isLoadingMetadata || state.error != null) {
                            AsyncImage(
                                model = currentVideo.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            PlayerVideoSurface(player, videoOutput, state.isMiniPlayerVisible, Modifier.fillMaxSize())
                        }
                        if (state.isBuffering || state.isLoadingMetadata) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center).size(24.dp),
                                color = AeroPlayBrand.PlayerForeground,
                                strokeWidth = 2.dp
                            )
                        }
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                            shape = CircleShape,
                            color = AeroPlayBrand.Navy.copy(alpha = 0.75f)
                        ) {
                            IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Filled.Close, "Đóng trình phát", tint = AeroPlayBrand.PlayerForeground, modifier = Modifier.size(19.dp))
                            }
                        }
                        if (state.isAudioOnlyMode) {
                            Icon(
                                Icons.Filled.Headphones,
                                "Chỉ âm thanh",
                                tint = AeroPlayBrand.PlayerForeground,
                                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).size(20.dp)
                            )
                        }
                        LinearProgressIndicator(
                            progress = {
                                if (state.durationMs > 0) (state.currentPositionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
                            },
                            modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                            color = AeroPlayBrand.Cyan,
                            trackColor = AeroPlayBrand.PlayerForeground.copy(alpha = 0.27f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentVideo.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).clickable(
                                onClickLabel = "Mở rộng trình phát", onClick = onExpand
                            )
                        )

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

                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerActionChip(
    icon: ImageVector,
    label: String,
    activeColor: Color? = null,
    iconOnly: Boolean = false,
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
                contentDescription = if (iconOnly) label else null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            if (!iconOnly) {
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
}

private val descriptionHtmlTag = Regex("""</?(?:a|br|p|div|span|b|strong|i|em|u|ul|ol|li|blockquote)\b""", RegexOption.IGNORE_CASE)
