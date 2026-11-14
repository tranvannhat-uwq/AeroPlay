package com.aeroplay.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeroplay.app.core.model.ChannelItem
import com.aeroplay.app.core.model.PlaylistItem
import com.aeroplay.app.core.model.SourceError
import com.aeroplay.app.core.model.UserPlaylist
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.core.network.SourceDiagnostics
import com.aeroplay.app.data.repository.HistoryRepository
import com.aeroplay.app.data.repository.PlaylistRepository
import com.aeroplay.app.data.repository.SearchRepository
import com.aeroplay.app.data.source.SourceErrorException
import com.aeroplay.app.playback.controller.PlaybackControllerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val isLoadingMore: Boolean = false,
    val videos: List<VideoItem> = emptyList(),
    val channels: List<ChannelItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val correctedQuery: String? = null,
    val nextPageToken: Any? = null,
    val error: SourceError? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val historyRepository: HistoryRepository,
    private val playlistRepository: PlaylistRepository,
    private val playbackController: PlaybackControllerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val queryInputFlow = MutableStateFlow("")

    val searchHistory: StateFlow<List<String>> = searchRepository.observeSearchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteIds: StateFlow<Set<String>> = historyRepository.observeFavorites()
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val userPlaylists: StateFlow<List<UserPlaylist>> = playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var activeSearchJob: Job? = null

    init {
        // Debounce 400ms (within required 350–500ms window in Section V.2)
        viewModelScope.launch {
            queryInputFlow
                .debounce(400L)
                .distinctUntilChanged()
                .collectLatest { debouncedQuery ->
                    if (debouncedQuery.trim().isNotEmpty()) {
                        executeSearch(debouncedQuery.trim(), saveToHistory = false)
                    } else {
                        activeSearchJob?.cancel()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                videos = emptyList(),
                                channels = emptyList(),
                                playlists = emptyList(),
                                nextPageToken = null,
                                error = null
                            )
                        }
                    }
                }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        queryInputFlow.value = newQuery
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

    private fun executeSearch(query: String, saveToHistory: Boolean) {
        activeSearchJob?.cancel()
        activeSearchJob = viewModelScope.launch {
            if (saveToHistory) {
                searchRepository.saveSearchQuery(query)
            }
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    nextPageToken = null
                )
            }

            val result = searchRepository.search(query = query, pageToken = null)
            result.fold(
                onSuccess = { bundle ->
                    SourceDiagnostics.event("search.ui", "videos=${bundle.videos.size} channels=${bundle.channels.size} playlists=${bundle.playlists.size}")
                    if (saveToHistory || bundle.videos.isNotEmpty()) {
                        searchRepository.saveSearchQuery(query)
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
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
                            error = sourceErr
                        )
                    }
                }
            )
        }
    }

    fun loadMoreResults() {
        val state = _uiState.value
        val token = state.nextPageToken ?: return
        if (state.isLoadingMore || state.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val result = searchRepository.search(query = state.query, pageToken = token)
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
