package com.aeroplay.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.aeroplay.app.core.model.ChannelItem
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.PlaylistItem
import com.aeroplay.app.core.model.RemotePlaylistUiState
import com.aeroplay.app.core.model.SearchResultBundle
import com.aeroplay.app.core.model.SearchContentType
import com.aeroplay.app.core.model.SourceError
import com.aeroplay.app.core.model.UserPlaylist
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.core.network.SourceDiagnostics
import com.aeroplay.app.data.repository.HistoryRepository
import com.aeroplay.app.data.repository.MusicStyleAnalyzer
import com.aeroplay.app.data.repository.PlaylistRepository
import com.aeroplay.app.data.repository.RemotePlaylistRepository
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
import kotlinx.coroutines.flow.combine
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
    val sortOrder: SearchSortOrder = SearchSortOrder.RELEVANCE,
    val timeRange: SearchTimeRange = SearchTimeRange.ANY,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val videos: List<VideoItem> = emptyList(),
    val candidateVideos: List<VideoItem> = emptyList(),
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
    private val remotePlaylistRepository: RemotePlaylistRepository,
    private val playbackController: PlaybackControllerManager,
    private val savedStateHandle: SavedStateHandle,
    private val libraryPreferences: com.aeroplay.app.data.repository.LibraryPreferencesRepository
) : ViewModel() {
    private val notice = MutableStateFlow<String?>(null)
    val restrictionNotice = notice.asStateFlow()
    fun clearRestrictionNotice() { notice.value = null }
    fun restrictVideo(video: VideoItem) {
        viewModelScope.launch {
            try {
                libraryPreferences.restrictVideo(video)
                notice.value = "Đã thêm vào danh sách hạn chế"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notice.value = "Chưa lưu được lựa chọn. Hãy thử lại." }
        }
    }

    private val _uiState = MutableStateFlow(
        SearchUiState(
            activeFilter = savedStateHandle.get<String>("search_content_filter")
                ?.let { runCatching { SearchFilterTab.valueOf(it) }.getOrNull() } ?: SearchFilterTab.ALL,
            sortOrder = savedStateHandle.get<String>("search_sort_order")
                ?.let { runCatching { SearchSortOrder.valueOf(it) }.getOrNull() } ?: SearchSortOrder.RELEVANCE,
            timeRange = savedStateHandle.get<String>("search_time_range")
                ?.let { runCatching { SearchTimeRange.valueOf(it) }.getOrNull() } ?: SearchTimeRange.ANY
        )
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private val remotePlaylist = MutableStateFlow<RemotePlaylistUiState?>(null)
    val remotePlaylistState: StateFlow<RemotePlaylistUiState?> = remotePlaylist.asStateFlow()

    val searchHistory: StateFlow<List<String>> = searchRepository.observeSearchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val personalTerms = combine(
        historyRepository.observeWatchHistory(),
        historyRepository.observeFavorites()
    ) { watched, favorites ->
        (watched.take(30) + favorites.take(20))
            .flatMap { video -> Regex("[\\p{L}\\p{N}]{2,}").findAll(SearchQueryAnalyzer.normalize(video.title + " " + video.channelName)).map { it.value }.toList() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(12).map { it.key }.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

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
        if (tab == _uiState.value.activeFilter) return
        savedStateHandle["search_content_filter"] = tab.name
        _uiState.update { it.copy(activeFilter = tab) }
        val query = _uiState.value.query.trim()
        if (query.isNotEmpty()) executeSearch(query)
    }

    fun selectSortOrder(order: SearchSortOrder) {
        savedStateHandle["search_sort_order"] = order.name
        _uiState.update { current ->
            val ranked = VideoSearchRanker.rank(
                current.candidateVideos,
                SearchQueryAnalyzer.analyze(current.query),
                order,
                current.timeRange,
                personalTerms.value
            ).map { it.video }
            current.copy(sortOrder = order, videos = ranked)
        }
    }

    fun selectTimeRange(range: SearchTimeRange) {
        savedStateHandle["search_time_range"] = range.name
        _uiState.update { current ->
            val ranked = VideoSearchRanker.rank(
                current.candidateVideos,
                SearchQueryAnalyzer.analyze(current.query),
                current.sortOrder,
                range,
                personalTerms.value
            ).map { it.video }
            current.copy(timeRange = range, videos = ranked)
        }
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
                candidateVideos = if (refresh) it.candidateVideos else emptyList(),
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
            val contentType = _uiState.value.activeFilter.toContentType()
            val startedAtNanos = System.nanoTime()
            val result = search(query, null, source, contentType)
            if (refresh && source == ContentSourceType.DEMO_LOCAL) delay(350L)
            result.fold(
                onSuccess = { bundle ->
                    val analysis = SearchQueryAnalyzer.analyze(query)
                    val rankedVideos = VideoSearchRanker.rank(
                        bundle.videos,
                        analysis,
                        _uiState.value.sortOrder,
                        _uiState.value.timeRange,
                        personalTerms.value
                    )
                    SourceDiagnostics.event("search.ui", "source=$source refresh=$refresh videos=${bundle.videos.size} channels=${bundle.channels.size} playlists=${bundle.playlists.size}")
                    SourceDiagnostics.event(
                        "search.rank",
                        "query=${analysis.originalQuery.take(80)} intent=${analysis.intent} language=${analysis.language} locale=vi-VN country=VN candidates=${bundle.videos.size} ranked=${rankedVideos.size} duplicateDrop=${bundle.videos.size - bundle.videos.distinctBy { "${it.sourceType.name}:${it.id}" }.size} durationMs=${(System.nanoTime() - startedAtNanos) / 1_000_000} top=${rankedVideos.take(10).joinToString { "${it.video.id}:${it.score.total}(q${it.score.queryRelevance},f${it.score.freshness ?: "?"},p${it.score.popularity ?: "?"})" }}"
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            videos = rankedVideos.map { ranked -> ranked.video },
                            candidateVideos = bundle.videos.distinctBy { "${it.sourceType.name}:${it.id}" },
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

    private suspend fun search(
        query: String,
        token: Any?,
        source: ContentSourceType,
        contentType: SearchContentType
    ): Result<SearchResultBundle> {
        val result = try {
            withTimeoutOrNull(30_000L) { searchRepository.search(query, token, source, contentType) }
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
            val result = search(state.query.trim(), token, state.source, state.activeFilter.toContentType())
            result.fold(
                onSuccess = { bundle ->
                    val distinctPage = bundle.videos.distinctBy { "${it.sourceType.name}:${it.id}" }
                    val duplicateCount = bundle.videos.size - distinctPage.size
                    val analysis = SearchQueryAnalyzer.analyze(state.query)
                    val newVideos = VideoSearchRanker.rank(
                        distinctPage,
                        analysis,
                        state.sortOrder,
                        state.timeRange,
                        personalTerms.value
                    ).map { it.video }.filterNot { incoming -> state.candidateVideos.any { existing -> existing.sourceType == incoming.sourceType && existing.id == incoming.id } }
                    SourceDiagnostics.event("search.page.rank", "query=${analysis.originalQuery.take(80)} pageCandidates=${bundle.videos.size} duplicates=$duplicateCount appended=${newVideos.size} next=${bundle.nextPageToken != null}")
                    _uiState.update { current ->
                        current.copy(
                            isLoadingMore = false,
                            videos = current.videos + newVideos,
                            candidateVideos = (current.candidateVideos + distinctPage).distinctBy { "${it.sourceType.name}:${it.id}" },
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
        val searchState = _uiState.value
        val musicResults = searchState.videos.count {
            MusicStyleAnalyzer.analyze(it.title, it.channelName).isLikelyMusic
        }
        val resultsLookLikeMusic = searchState.videos.isNotEmpty() && musicResults >= 2 &&
            musicResults * 2 >= searchState.videos.size
        val radioMode = MusicStyleAnalyzer.analyze(video.title, video.channelName).isLikelyMusic ||
            SearchQueryAnalyzer.analyze(searchState.query).intent == SearchIntent.MUSIC || resultsLookLikeMusic
        viewModelScope.launch {
            if (searchState.query.isNotBlank()) {
                searchRepository.saveSearchQuery(searchState.query.trim())
            }
        }
        playbackController.loadAndPlayVideo(
            video = video,
            queueItems = if (radioMode) listOf(video) else searchState.videos.ifEmpty { listOf(video) },
            queueTitle = if (radioMode) "Phát tiếp cùng phong cách" else "Kết quả tìm kiếm: ${searchState.query}",
            openFullPlayer = true,
            radioMode = radioMode
        )
    }

    fun openRemotePlaylist(playlist: PlaylistItem) {
        remotePlaylist.value = RemotePlaylistUiState(playlist)
        viewModelScope.launch {
            remotePlaylistRepository.getPlaylist(playlist).fold(
                onSuccess = { detail ->
                    if (remotePlaylist.value?.playlist?.id == playlist.id) {
                        remotePlaylist.value = RemotePlaylistUiState(
                            playlist = detail.playlist,
                            isLoading = false,
                            videos = detail.videos,
                            hasMore = detail.hasMore
                        )
                    }
                },
                onFailure = { error ->
                    if (remotePlaylist.value?.playlist?.id == playlist.id) {
                        remotePlaylist.value = RemotePlaylistUiState(
                            playlist = playlist,
                            isLoading = false,
                            error = error.message ?: "Không tải được playlist."
                        )
                    }
                }
            )
        }
    }

    fun closeRemotePlaylist() {
        remotePlaylist.value = null
    }

    fun playRemotePlaylistVideo(video: VideoItem, queue: List<VideoItem>, title: String) {
        val playlist = remotePlaylist.value?.playlist
        val musicPlaylist = playlist?.let {
            MusicStyleAnalyzer.analyze(it.title, it.uploaderName, it.description).isLikelyMusic
        } == true || SearchQueryAnalyzer.analyze(_uiState.value.query).intent == SearchIntent.MUSIC
        playbackController.loadAndPlayVideo(
            video, queue, title,
            openFullPlayer = true,
            autoRadioAfterEnd = musicPlaylist
        )
        remotePlaylist.value = null
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

    private fun SearchFilterTab.toContentType(): SearchContentType = when (this) {
        SearchFilterTab.ALL -> SearchContentType.ALL
        SearchFilterTab.VIDEOS -> SearchContentType.VIDEO
        SearchFilterTab.CHANNELS -> SearchContentType.CHANNEL
        SearchFilterTab.PLAYLISTS -> SearchContentType.PLAYLIST
    }
}
