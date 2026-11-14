package com.aeroplay.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeroplay.app.core.model.ChannelItem
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.PlaylistItem
import com.aeroplay.app.core.model.SearchResultBundle
import com.aeroplay.app.core.model.SourceError
import com.aeroplay.app.core.model.UserPlaylist
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.core.network.SourceDiagnostics
import com.aeroplay.app.data.repository.HistoryRepository
import com.aeroplay.app.data.repository.PlaylistRepository
import com.aeroplay.app.data.repository.SearchRepository
import com.aeroplay.app.data.repository.SettingsRepository
import com.aeroplay.app.data.source.SourceErrorException
import com.aeroplay.app.playback.controller.PlaybackControllerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

enum class SearchFilterTab(val label: String) {
    ALL("Tất cả"),
    VIDEOS("Video"),
    CHANNELS("Kênh"),
    PLAYLISTS("Playlist")
}

data class SearchUiState(
    val query: String = "",
    val activeFilter: SearchFilterTab = SearchFilterTab.ALL,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val videos: List<VideoItem> = emptyList(),
    val channels: List<ChannelItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val correctedQuery: String? = null,
    val nextPageToken: Any? = null,
    val error: SourceError? = null,
    val source: ContentSourceType = ContentSourceType.DEMO_LOCAL
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val playlistRepository: PlaylistRepository,
    private val playbackController: PlaybackControllerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val searchHistory: StateFlow<List<String>> = searchRepository.observeSearchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteIds: StateFlow<Set<String>> = historyRepository.observeFavorites()
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val userPlaylists: StateFlow<List<UserPlaylist>> = playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var activeSearchJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow
                .map { it.activeSourceType }
                .distinctUntilChanged()
                .collect { source ->
                    if (source != _uiState.value.source) {
                        _uiState.update { it.copy(source = source, activeFilter = SearchFilterTab.ALL) }
                        onQueryChange(_uiState.value.query)
                    }
                }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        executeSearch(newQuery.trim(), debounce = true)
    }

    fun onSubmitSearch(query: String = _uiState.value.query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { it.copy(query = trimmed) }
        executeSearch(trimmed, saveToHistory = true)
    }

    fun selectFilter(tab: SearchFilterTab) {
        _uiState.update { it.copy(activeFilter = tab) }
    }

    fun refresh() {
        val state = _uiState.value
        if (state.query.isNotBlank() && !state.isLoading && !state.isRefreshing) {
            executeSearch(state.query.trim(), refresh = true)
        }
    }

    private fun executeSearch(
        query: String,
        saveToHistory: Boolean = false,
        debounce: Boolean = false,
        refresh: Boolean = false
    ) {
        activeSearchJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = query.isNotEmpty() && !refresh,
                isRefreshing = refresh,
                isLoadingMore = false,
                videos = if (refresh) it.videos else emptyList(),
                channels = if (refresh) it.channels else emptyList(),
                playlists = if (refresh) it.playlists else emptyList(),
                correctedQuery = null,
                nextPageToken = null,
                error = null
            )
        }
        if (query.isEmpty()) return
        val source = _uiState.value.source

        activeSearchJob = viewModelScope.launch {
            if (debounce) delay(400L)
            val result = search(query, null, source)
            if (refresh && source == ContentSourceType.DEMO_LOCAL) delay(350L)
            result.fold(
                onSuccess = { bundle ->
                    SourceDiagnostics.event("search.ui", "source=$source refresh=$refresh videos=${bundle.videos.size} channels=${bundle.channels.size} playlists=${bundle.playlists.size}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            videos = bundle.videos,
                            channels = bundle.channels,
                            playlists = bundle.playlists,
                            correctedQuery = bundle.correctedQuery,
                            nextPageToken = bundle.nextPageToken,
                            error = null
                        )
                    }
                },
                onFailure = { err ->
                    SourceDiagnostics.failure("search.ui", err)
                    val sourceErr = (err as? SourceErrorException)?.sourceError
                        ?: SourceError.UnknownError(err.message ?: "Lỗi tìm kiếm")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = sourceErr
                        )
                    }
                }
            )
            if (!refresh && (saveToHistory || result.getOrNull()?.videos?.isNotEmpty() == true)) {
                searchRepository.saveSearchQuery(query)
            }
        }
    }

    private suspend fun search(query: String, token: Any?, source: ContentSourceType): Result<SearchResultBundle> {
        val result = try {
            withTimeoutOrNull(30_000L) { searchRepository.search(query, token, source) }
                ?: Result.failure(SourceErrorException(SourceError.NetworkError("Tìm kiếm quá lâu. Hãy thử lại.")))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
        coroutineContext.ensureActive()
        return result
    }

    fun loadMoreResults() {
        val state = _uiState.value
        val token = state.nextPageToken ?: return
        if (state.isLoadingMore || state.isLoading || state.isRefreshing) return

        activeSearchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val result = search(state.query.trim(), token, state.source)
            result.fold(
                onSuccess = { bundle ->
                    _uiState.update { current ->
                        current.copy(
                            isLoadingMore = false,
                            videos = (current.videos + bundle.videos).distinctBy { it.id },
                            channels = (current.channels + bundle.channels).distinctBy { it.id },
                            playlists = (current.playlists + bundle.playlists).distinctBy { it.id },
                            nextPageToken = bundle.nextPageToken
                        )
                    }
                },
                onFailure = { error ->
                    SourceDiagnostics.failure("search.page.ui", error)
                    val sourceError = (error as? SourceErrorException)?.sourceError
                        ?: SourceError.UnknownError(error.message ?: "Không thể tải thêm kết quả")
                    _uiState.update { it.copy(isLoadingMore = false, error = sourceError) }
                }
            )
        }
    }

    fun deleteHistoryQuery(query: String) {
        viewModelScope.launch {
            searchRepository.deleteSearchQuery(query)
        }
    }

    fun clearAllSearchHistory() {
        viewModelScope.launch {
            searchRepository.clearSearchHistory()
        }
    }

    fun playVideo(video: VideoItem) {
        viewModelScope.launch {
            if (_uiState.value.query.isNotBlank()) {
                searchRepository.saveSearchQuery(_uiState.value.query.trim())
            }
        }
        playbackController.loadAndPlayVideo(
            video = video,
            queueItems = _uiState.value.videos.ifEmpty { listOf(video) },
            queueTitle = "Kết quả tìm kiếm: ${_uiState.value.query}",
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
}
