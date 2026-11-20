package com.aeroplay.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aeroplay.app.core.designsystem.AeroPlayDestination
import com.aeroplay.app.core.designsystem.AeroPlayTheme
import com.aeroplay.app.core.designsystem.FloatingNavigationBar
import com.aeroplay.app.core.designsystem.GlassBackground
import com.aeroplay.app.core.designsystem.LocalAeroPlayStatusColors
import com.aeroplay.app.core.designsystem.LocalAeroGlassEffectsEnabled
import com.aeroplay.app.core.designsystem.LocalGlassLight
import com.aeroplay.app.core.designsystem.OfflineBanner
import com.aeroplay.app.core.model.AppSettings
import com.aeroplay.app.core.network.NetworkConnectionType
import com.aeroplay.app.core.network.NetworkConnectivityObserver
import com.aeroplay.app.data.repository.SettingsRepository
import com.aeroplay.app.data.repository.DeviceMediaRepository
import com.aeroplay.app.feature.home.HomeScreen
import com.aeroplay.app.feature.home.HomeViewModel
import com.aeroplay.app.feature.library.LibraryScreen
import com.aeroplay.app.feature.library.LibraryViewModel
import com.aeroplay.app.feature.player.FullPlayerOverlay
import com.aeroplay.app.playback.widget.PlaybackWidget
import com.aeroplay.app.feature.player.MiniPlayerBar
import com.aeroplay.app.feature.player.PlayerVideoOutput
import com.aeroplay.app.feature.player.PlayerViewModel
import com.aeroplay.app.feature.search.SearchScreen
import com.aeroplay.app.feature.search.SearchViewModel
import com.aeroplay.app.feature.settings.SettingsScreen
import com.aeroplay.app.feature.settings.SettingsViewModel
import com.aeroplay.app.playback.controller.PlaybackControllerManager
import com.aeroplay.app.playback.pip.PipController
import com.aeroplay.app.updates.AppUpdateManager
import com.aeroplay.app.updates.AppUpdateDialogHost
import dagger.hilt.android.AndroidEntryPoint
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var deviceMedia: DeviceMediaRepository
    private val deviceLibraryRequested = MutableStateFlow(false)

    @Inject lateinit var appUpdates: AppUpdateManager

    @Inject
    lateinit var playbackController: PlaybackControllerManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var networkObserver: NetworkConnectivityObserver

    private var currentSettingsSnapshot: AppSettings = AppSettings()

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PipController.ACTION_PIP_TOGGLE_PLAY) {
                playbackController.togglePlayPause()
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* MediaSessionService works regardless, notification shown if granted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        appUpdates.checkOnLaunch()
        if (savedInstanceState == null) {
            handleSharedVideos(intent)
            handleMediaSearch(intent)
            handleWidgetIntent(intent)
        }

        val filter = IntentFilter(PipController.ACTION_PIP_TOGGLE_PLAY)
        ContextCompat.registerReceiver(
            this,
            pipActionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            val settings by settingsRepository.settingsFlow
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            val showDeviceLibrary by deviceLibraryRequested.collectAsStateWithLifecycle()
            val playbackState by playbackController.playbackState
                .collectAsStateWithLifecycle()
            val networkType by networkObserver.observeConnectionType()
                .collectAsStateWithLifecycle(initialValue = NetworkConnectionType.WIFI)

            LaunchedEffect(settings, playbackState.isPlaying, playbackState.isAudioOnlyMode) {
                currentSettingsSnapshot = settings
                val canAutoPip = settings.pipEnabled &&
                    playbackState.isPlaying &&
                    !playbackState.isAudioOnlyMode &&
                    playbackState.currentVideo != null
                PipController.updatePipParams(
                    activity = this@MainActivity,
                    isPlaying = playbackState.isPlaying,
                    autoEnterEnabled = canAutoPip,
                    videoWidth = playbackState.selectedStreamVariant?.width ?: 16,
                    videoHeight = playbackState.selectedStreamVariant?.height ?: 9
                )
            }

            AeroPlayTheme(
                themeMode = settings.themeMode,
                glassEffectsEnabled = settings.glassEffectsEnabled
            ) {
                val light = LocalGlassLight.current
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = light
                        isAppearanceLightNavigationBars = light
                    }
                }
                AeroPlayAppRoot(
                    openWidgetQueue = widgetQueueRequested.value,
                    onWidgetQueueOpened = { widgetQueueRequested.value = false },
                    openDeviceLibrary = showDeviceLibrary,
                    onDeviceLibraryOpened = { deviceLibraryRequested.value = false },
                    isOffline = networkType == NetworkConnectionType.OFFLINE,
                    onEnterPip = {
                        if (playbackState.currentVideo != null) {
                            val entered = PipController.enterPip(
                                activity = this@MainActivity,
                                isPlaying = playbackState.isPlaying,
                                autoEnterEnabled = settings.pipEnabled,
                                videoWidth = playbackState.selectedStreamVariant?.width ?: 16,
                                videoHeight = playbackState.selectedStreamVariant?.height ?: 9
                            )
                            if (entered) {
                                playbackController.onPipModeChanged(true)
                            }
                        }
                    }
                )
                AppUpdateDialogHost(appUpdates)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appUpdates.onActivityResumed(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedVideos(intent)
        handleMediaSearch(intent)
        handleWidgetIntent(intent)
    }

    private val widgetQueueRequested = mutableStateOf(false)

    private fun handleWidgetIntent(intent: Intent?) {
        when (intent?.action) {
            PlaybackWidget.ACTION_OPEN_PLAYER -> playbackController.expandToFullPlayer()
            PlaybackWidget.ACTION_OPEN_QUEUE -> {
                widgetQueueRequested.value = playbackController.playbackState.value.currentVideo != null
                playbackController.expandToFullPlayer()
            }
        }
    }

    private fun handleMediaSearch(intent: Intent?) {
        if (intent?.action != android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return
        playbackController.playFromMediaSearch(intent.getStringExtra(android.app.SearchManager.QUERY).orEmpty().take(200))
    }

    @Suppress("DEPRECATION")
    private fun handleSharedVideos(intent: Intent?) {
        if (intent?.type?.startsWith("video/") != true) return
        val selected = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            )
            Intent.ACTION_SEND_MULTIPLE -> {
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            }
            else -> emptyList()
        }.filter { it.scheme == "content" }
        if (selected.isNotEmpty()) {
            deviceLibraryRequested.value = true
            deviceMedia.importVideos(selected)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val state = playbackController.playbackState.value
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            currentSettingsSnapshot.pipEnabled &&
            state.isPlaying &&
            !state.isAudioOnlyMode &&
            state.currentVideo != null
        ) {
            PipController.enterPip(
                activity = this,
                isPlaying = state.isPlaying,
                autoEnterEnabled = true,
                videoWidth = state.selectedStreamVariant?.width ?: 16,
                videoHeight = state.selectedStreamVariant?.height ?: 9
            )
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playbackController.onPipModeChanged(isInPictureInPictureMode)
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode && !isChangingConfigurations) {
            playbackController.onAppBackgrounded()
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipActionReceiver) }
        super.onDestroy()
    }
}

@Composable
private fun AeroPlayAppRoot(
    openWidgetQueue: Boolean,
    onWidgetQueueOpened: () -> Unit,
    isOffline: Boolean,
    onEnterPip: () -> Unit,
    openDeviceLibrary: Boolean,
    onDeviceLibraryOpened: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AeroPlayDestination.HOME.route

    val playerViewModel: PlayerViewModel = hiltViewModel()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val player by playerViewModel.playerFlow.collectAsStateWithLifecycle()
    val videoOutput = remember { PlayerVideoOutput() }
    val reduceMotion = LocalAeroPlayStatusColors.current.reduceMotion
    val backdrop = rememberLayerBackdrop()
    val captureGlassSource = LocalAeroGlassEffectsEnabled.current &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        !playbackState.isFullPlayerOpen && !playbackState.isInPipMode
    val contentSourceModifier = if (captureGlassSource) Modifier.layerBackdrop(backdrop) else Modifier
    val showFloatingPlayer = playbackState.isMiniPlayerVisible && playbackState.currentVideo != null &&
        !playbackState.isFullPlayerOpen && !playbackState.isInPipMode
    val showBottomDock = !playbackState.isFullPlayerOpen && !playbackState.isInPipMode
    val density = LocalDensity.current
    var bottomDockContentHeight by remember { mutableStateOf(84.dp) }
    val screenAnimation = if (reduceMotion) 0 else 200
    LaunchedEffect(openDeviceLibrary, navBackStackEntry == null) {
        if (openDeviceLibrary && navBackStackEntry != null) {
            playerViewModel.minimizePlayer()
            navController.navigate(AeroPlayDestination.LIBRARY.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().then(contentSourceModifier)) {
                GlassBackground()
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    containerColor = Color.Transparent,
                    topBar = {
                        if (isOffline && !playbackState.isInPipMode) {
                            Column(modifier = Modifier.statusBarsPadding()) {
                                OfflineBanner()
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = AeroPlayDestination.HOME.route,
                        enterTransition = { fadeIn(tween(screenAnimation)) },
                        exitTransition = { fadeOut(tween(screenAnimation)) },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(bottom = if (showBottomDock) bottomDockContentHeight else 0.dp)
                    ) {
                        composable(AeroPlayDestination.HOME.route) {
                            val homeViewModel: HomeViewModel = hiltViewModel()
                            HomeScreen(
                                viewModel = homeViewModel,
                                onOpenSearch = {
                                    navController.navigate(AeroPlayDestination.SEARCH.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }

                        composable(AeroPlayDestination.SEARCH.route) {
                            val searchViewModel: SearchViewModel = hiltViewModel()
                            SearchScreen(viewModel = searchViewModel)
                        }

                        composable(AeroPlayDestination.LIBRARY.route) {
                            val libraryViewModel: LibraryViewModel = hiltViewModel()
                            LaunchedEffect(openDeviceLibrary) {
                                if (openDeviceLibrary) {
                                    libraryViewModel.selectTab(com.aeroplay.app.feature.library.LibraryTab.DEVICE)
                                    onDeviceLibraryOpened()
                                }
                            }
                            LibraryScreen(viewModel = libraryViewModel)
                        }

                        composable(AeroPlayDestination.SETTINGS.route) {
                            val settingsViewModel: SettingsViewModel = hiltViewModel()
                            SettingsScreen(viewModel = settingsViewModel)
                        }
                    }
                }
            }

            // Bottom Dock: MiniPlayerBar + FloatingNavigationBar
            if (showBottomDock) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .onSizeChanged { size ->
                            bottomDockContentHeight = with(density) { size.height.toDp() }
                        }
                ) {
                    AnimatedVisibility(
                        visible = showFloatingPlayer,
                        enter = if (reduceMotion) fadeIn(tween(0)) else fadeIn(tween(180)) + scaleIn(
                            initialScale = 0.84f, transformOrigin = TransformOrigin(0.92f, 1f)
                        ) + slideInVertically { it / 4 },
                        exit = if (reduceMotion) fadeOut(tween(0)) else fadeOut(tween(135)) + scaleOut(
                            targetScale = 0.9f, transformOrigin = TransformOrigin(0.92f, 1f)
                        )
                    ) {
                        MiniPlayerBar(
                            state = playbackState,
                            player = player,
                            videoOutput = videoOutput,
                            backdrop = backdrop.takeIf { captureGlassSource },
                            onExpand = { playerViewModel.expandPlayer() },
                            onTogglePlayPause = { playerViewModel.togglePlayPause() },
                            onNext = { playerViewModel.playNext() },
                            onDismiss = { playerViewModel.dismissPlayer() }
                        )
                    }

                    FloatingNavigationBar(
                        currentRoute = currentRoute,
                        backdrop = backdrop.takeIf { captureGlassSource },
                        onNavigate = { destination ->
                            if (currentRoute != destination.route) {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }

            // Full Video Player Overlay (or PiP surface)
            AnimatedVisibility(
                visible = (playbackState.isFullPlayerOpen || playbackState.isInPipMode) &&
                    playbackState.currentVideo != null,
                enter = if (reduceMotion || playbackState.isInPipMode) fadeIn(tween(0))
                else slideInVertically(tween(300), initialOffsetY = { it / 3 }) + fadeIn(tween(220)),
                exit = if (reduceMotion) fadeOut(tween(0))
                else slideOutVertically(tween(230), targetOffsetY = { it / 3 }) + fadeOut(tween(200)),
                modifier = Modifier.fillMaxSize()
            ) {
                FullPlayerOverlay(
                    openQueue = openWidgetQueue,
                    onQueueOpened = onWidgetQueueOpened,
                    viewModel = playerViewModel,
                    videoOutput = videoOutput,
                    onEnterPip = onEnterPip,
                    modifier = Modifier
                )
            }
        }
    }
}
