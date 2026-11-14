package com.aeroplay.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
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
import com.aeroplay.app.core.designsystem.OfflineBanner
import com.aeroplay.app.core.model.AppSettings
import com.aeroplay.app.core.network.NetworkConnectionType
import com.aeroplay.app.core.network.NetworkConnectivityObserver
import com.aeroplay.app.data.repository.SettingsRepository
import com.aeroplay.app.feature.home.HomeScreen
import com.aeroplay.app.feature.home.HomeViewModel
import com.aeroplay.app.feature.library.LibraryScreen
import com.aeroplay.app.feature.library.LibraryViewModel
import com.aeroplay.app.feature.player.FullPlayerOverlay
import com.aeroplay.app.feature.player.MiniPlayerBar
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
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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

            AeroPlayTheme(themeMode = settings.themeMode) {
                AeroPlayAppRoot(
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
    isOffline: Boolean,
    onEnterPip: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AeroPlayDestination.HOME.route

    val playerViewModel: PlayerViewModel = hiltViewModel()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                containerColor = MaterialTheme.colorScheme.background,
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
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
                        LibraryScreen(viewModel = libraryViewModel)
                    }

                    composable(AeroPlayDestination.SETTINGS.route) {
                        val settingsViewModel: SettingsViewModel = hiltViewModel()
                        SettingsScreen(viewModel = settingsViewModel)
                    }
                }
            }

            // Bottom Dock: MiniPlayerBar + FloatingNavigationBar
            if (!playbackState.isFullPlayerOpen && !playbackState.isInPipMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                ) {
                    AnimatedVisibility(
                        visible = playbackState.isMiniPlayerVisible && playbackState.currentVideo != null,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ) {
                        MiniPlayerBar(
                            state = playbackState,
                            onExpand = { playerViewModel.expandPlayer() },
                            onTogglePlayPause = { playerViewModel.togglePlayPause() },
                            onNext = { playerViewModel.playNext() },
                            onDismiss = { playerViewModel.dismissPlayer() }
                        )
                    }

                    FloatingNavigationBar(
                        currentRoute = currentRoute,
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
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.fillMaxSize()
            ) {
                FullPlayerOverlay(
                    viewModel = playerViewModel,
                    onEnterPip = onEnterPip,
                    modifier = Modifier.statusBarsPadding()
                )
            }
        }
    }
}
