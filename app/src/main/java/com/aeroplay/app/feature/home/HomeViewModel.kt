package com.aeroplay.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeroplay.app.core.model.AppSettings
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.PlaybackPosition
import com.aeroplay.app.core.model.SourceError
import com.aeroplay.app.core.model.UserPlaylist
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.core.network.SourceDiagnostics
import com.aeroplay.app.data.repository.ContentRepository
import com.aeroplay.app.data.repository.HistoryRepository
import com.aeroplay.app.data.repository.PlaylistRepository
import com.aeroplay.app.data.repository.SettingsRepository
import com.aeroplay.app.data.source.SourceErrorException
import com.aeroplay.app.playback.controller.PlaybackControllerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val recommendedVideos: List<VideoItem> = emptyList(),
    val error: SourceError? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val historyRepository: HistoryRepository,
    private val playlistRepository: PlaylistRepository,
    private val settingsRepository: SettingsRepository,
    private val playbackController: PlaybackControllerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val settingsState: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val recentHistory: StateFlow<List<VideoItem>> = historyRepository.observeWatchHistory()
        .map { it.take(10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<VideoItem>> = historyRepository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteIds: StateFlow<Set<String>> = favorites
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val savedPositions: StateFlow<Map<String, PlaybackPosition>> =
        historyRepository.observePlaybackPositions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val playlists: StateFlow<List<UserPlaylist>> = playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var loadJob: Job? = null
    private var loadedSource: ContentSourceType? = null

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow
                .distinctUntilChangedBy { it.activeSourceType }
                .collectLatest { settings ->
                    loadFeed(isRefresh = false, sourceType = settings.activeSourceType)
                }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        val source = loadedSource ?: return
        loadFeed(isRefresh = true, sourceType = source)
    }

    private fun loadFeed(isRefresh: Boolean, sourceType: ContentSourceType) {
        loadJob?.cancel()
        val sourceChanged = loadedSource != sourceType
        loadedSource = sourceType
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh && (sourceChanged || it.recommendedVideos.isEmpty()),
                    isRefreshing = isRefresh,
                    recommendedVideos = if (sourceChanged) emptyList() else it.recommendedVideos,
                    error = null
                )
            }

            val result = try {
                withTimeoutOrNull(30_000L) { contentRepository.getHomeFeed(sourceType) }
                    ?: Result.failure(SourceErrorException(SourceError.NetworkError("Tải danh sách quá lâu. Hãy thử lại.")))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            // The local catalog can return in the same frame. Keep the indicator visible
            // briefly so Material3 observes both the start and end of a refresh.
            if (isRefresh && sourceType == ContentSourceType.DEMO_LOCAL) delay(350L)
            coroutineContext.ensureActive()
            result.fold(
                onSuccess = { videos ->
                    SourceDiagnostics.event("home.ui", "videos=${videos.size} source=$sourceType refresh=$isRefresh")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            recommendedVideos = videos,
                            error = null
                        )
                    }
                },
                onFailure = { throwable ->
                    SourceDiagnostics.failure("home.ui", throwable)
                    val err = (throwable as? SourceErrorException)?.sourceError
                        ?: SourceError.UnknownError(throwable.message ?: "Không thể tải trang chủ")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = err
                        )
                    }
                }
            )
        }
    }

    fun playVideo(video: VideoItem, queue: List<VideoItem>, queueTitle: String) {
        playbackController.loadAndPlayVideo(
            video = video,
            queueItems = queue,
            queueTitle = queueTitle,
            openFullPlayer = true
        )
    }

    fun toggleFavorite(video: VideoItem) {
        viewModelScope.launch {
            historyRepository.toggleFavorite(video)
        }
    }

    fun addVideoToPlaylist(playlistId: Long, video: VideoItem) {
        viewModelScope.launch {
            playlistRepository.addVideoToPlaylist(playlistId, video)
        }
    }

    fun createPlaylistAndAdd(name: String, video: VideoItem) {
        viewModelScope.launch {
            val id = playlistRepository.createPlaylist(name)
            playlistRepository.addVideoToPlaylist(id, video)
        }
    }

    fun switchSource(sourceType: ContentSourceType) {
        viewModelScope.launch {
            settingsRepository.setActiveSourceType(sourceType, warningAccepted = true)
        }
    }
}
