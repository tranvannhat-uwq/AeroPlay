package com.aeroplay.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeroplay.app.core.model.AppSettings
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.PlaybackPosition
import com.aeroplay.app.core.model.SourceError
import com.aeroplay.app.core.model.UserPlaylist
import com.aeroplay.app.core.model.PlaylistItem
import com.aeroplay.app.core.model.RemotePlaylistUiState
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.core.model.AutoMix
import com.aeroplay.app.core.database.HistoryEntity
import com.aeroplay.app.core.database.ContentRestrictionEntity
import com.aeroplay.app.core.network.SourceDiagnostics
import com.aeroplay.app.data.repository.ContentRepository
import com.aeroplay.app.data.repository.HistoryRepository
import com.aeroplay.app.data.repository.PlaylistRepository
import com.aeroplay.app.data.repository.SettingsRepository
import com.aeroplay.app.data.repository.PersonalizedRecommendations
import com.aeroplay.app.data.repository.PersonalizedResult
import com.aeroplay.app.data.repository.RemotePlaylistRepository
import com.aeroplay.app.data.repository.MusicStyleAnalyzer
import com.aeroplay.app.data.repository.LibraryPreferencesRepository
import com.aeroplay.app.data.repository.ContentRestrictionPolicy
import com.aeroplay.app.data.repository.RecommendationRanking
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
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

data class PersonalizedUiState(
    val loading: Boolean = true,
    val videos: List<VideoItem> = emptyList(),
    val message: String = "",
    val musicVideos: List<VideoItem> = emptyList(),
    val popularMusicVideos: List<VideoItem> = emptyList(),
    val musicPlaylists: List<PlaylistItem> = emptyList(),
    val hasMusicStyleContext: Boolean = false,
    val autoMixes: List<AutoMix> = emptyList()
)

enum class RecommendationAction { NOT_INTERESTED, BLOCK_CHANNEL, MARK_WATCHED, WATCH_LATER, RESTRICT }

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val historyRepository: HistoryRepository,
    private val playlistRepository: PlaylistRepository,
    private val settingsRepository: SettingsRepository,
    private val personalizedRecommendations: PersonalizedRecommendations,
    private val remotePlaylistRepository: RemotePlaylistRepository,
    private val playbackController: PlaybackControllerManager,
    private val libraryPreferences: LibraryPreferencesRepository
) : ViewModel() {

    private val recommendationFilters = combine(libraryPreferences.restrictions,
        personalizedRecommendations.feedback, personalizedRecommendations.blockedChannels) { restrictions, feedback, blocked ->
        HomeRecommendationFilters(restrictions,
            feedback.filter { it.feedbackType == RecommendationRanking.NOT_INTERESTED || it.feedbackType == RecommendationRanking.MARKED_WATCHED }
                .map { it.videoId }.toSet(), blocked.map { it.normalizedChannelName }.toSet())
    }
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = combine(_uiState, recommendationFilters) { state, filters ->
        state.copy(recommendedVideos = filters.apply(state.recommendedVideos))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val settingsState: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    private val notice = MutableStateFlow<String?>(null)
    val restrictionNotice = notice.asStateFlow()
    fun clearRestrictionNotice() { notice.value = null }

    private val recommendationRefresh = MutableStateFlow(0)
    private val personalized = MutableStateFlow(PersonalizedUiState())
    val personalizedState = combine(personalized, recommendationFilters) { state, filters ->
        state.copy(videos = filters.apply(state.videos),
            musicVideos = filters.apply(state.musicVideos),
            popularMusicVideos = filters.apply(state.popularMusicVideos))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonalizedUiState())
    val autoMixes = combine(personalized, libraryPreferences.editedMixes, recommendationFilters, settingsState) { state, edits, filters, settings ->
        val saved = edits.filter { it.id.startsWith("${settings.activeSourceType.name}:") }
        val generated = state.autoMixes.filterNot { mix -> saved.any { it.id == mix.id } }.map { mix ->
            mix.copy(videos = filters.apply(mix.videos))
        }.filter { it.videos.size >= 3 }
        saved + generated
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val remotePlaylist = MutableStateFlow<RemotePlaylistUiState?>(null)
    val remotePlaylistState = remotePlaylist.asStateFlow()

    val favorites: StateFlow<List<VideoItem>> = historyRepository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteIds: StateFlow<Set<String>> = favorites
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val savedPositions: StateFlow<Map<String, PlaybackPosition>> =
        historyRepository.observePlaybackPositions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val continueWatching: StateFlow<List<VideoItem>> = combine(
        historyRepository.observeWatchHistory(),
        savedPositions
    ) { history, positions ->
        history.mapNotNull { video ->
            val position = positions[video.id] ?: return@mapNotNull null
            if (position.positionMs <= 3_000L || position.progressFraction >= 0.95f) null else video
        }.sortedByDescending { positions[it.id]?.updatedAtEpochMs ?: 0L }.take(12)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<UserPlaylist>> = playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var loadJob: Job? = null
    private var loadedSource: ContentSourceType? = null
    private val impressionsThisSession = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow
                .distinctUntilChangedBy { it.activeSourceType }
                .collectLatest { settings ->
                    loadFeed(isRefresh = false, sourceType = settings.activeSourceType)
                }
        }
        viewModelScope.launch {
            var previousSource: ContentSourceType? = null
            // Only first load, an explicit page refresh or a source switch requests a new
            // recommendation snapshot. History is persisted every few seconds while playing.
            combine(
                settingsRepository.settingsFlow.map { it.activeSourceType }.distinctUntilChanged(),
                recommendationRefresh
            ) { source, _ -> source }
                .collectLatest { source ->
                    val sourceChanged = previousSource != source
                    previousSource = source
                    personalized.update { if (sourceChanged) PersonalizedUiState() else it.copy(loading = true) }
                    val result = try {
                        val input = combine(
                            personalizedRecommendations.history,
                            personalizedRecommendations.favorites,
                            personalizedRecommendations.playlistVideos,
                            personalizedRecommendations.searchHistory
                        ) { history, favorites, playlists, searches ->
                            RecommendationInputs(history, favorites, playlists, searches)
                        }.first()
                        val playback = playbackController.playbackState.value
                        personalizedRecommendations.recommendations(
                            input.history, input.favorites, input.playlists, input.searches,
                            source, playback.currentVideo, playback.currentDetail?.description.orEmpty(),
                            playback.queue.radioMode || playback.queue.autoRadioAfterEnd
                        )
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        SourceDiagnostics.failure("recommendations", error)
                        PersonalizedResult(emptyList(), "Chưa tải được gợi ý. Kéo xuống để thử lại.")
                    }
                    personalized.value = PersonalizedUiState(
                        loading = false,
                        videos = result.videos,
                        message = result.message,
                        musicVideos = result.musicVideos,
                        popularMusicVideos = result.popularMusicVideos,
                        musicPlaylists = result.musicPlaylists,
                        hasMusicStyleContext = result.hasMusicStyleContext,
                        autoMixes = result.autoMixes
                    )
                }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        val source = loadedSource ?: return
        recommendationRefresh.value++
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

    fun playVideo(video: VideoItem, queue: List<VideoItem>, queueTitle: String, radioMode: Boolean = false) {
        viewModelScope.launch { personalizedRecommendations.recordClick(video) }
        val startsMusicRadio = radioMode || MusicStyleAnalyzer.analyze(video.title, video.channelName).isLikelyMusic
        playbackController.loadAndPlayVideo(
            video = video,
            queueItems = if (startsMusicRadio && !radioMode) listOf(video) else queue,
            queueTitle = if (startsMusicRadio && !radioMode) "Phát tiếp cùng phong cách" else queueTitle,
            openFullPlayer = true,
            radioMode = startsMusicRadio
        )
    }

    fun toggleFavorite(video: VideoItem) {
        viewModelScope.launch {
            historyRepository.toggleFavorite(video)
        }
    }

    fun playAutoMix(mix: AutoMix, video: VideoItem) {
        if (mix.videos.none { it.id == video.id && it.sourceType == video.sourceType }) return
        viewModelScope.launch { personalizedRecommendations.recordClick(video) }
        playbackController.loadAndPlayVideo(video, mix.videos, mix.title,
            openFullPlayer = true, radioMode = false, autoRadioAfterEnd = mix.isMusic && !mix.isCustomized, replaceQueue = true)
    }

    fun addVideoToPlaylist(playlistId: Long, video: VideoItem) {
        viewModelScope.launch {
            playlistRepository.addVideoToPlaylist(playlistId, video)
        }
    }

    fun recordRecommendationImpression(video: VideoItem) {
        val key = "${video.sourceType.name}:${video.id}"
        if (!impressionsThisSession.add(key)) return
        viewModelScope.launch { personalizedRecommendations.recordImpression(video) }
    }

    fun actOnRecommendation(video: VideoItem, action: RecommendationAction) {
        viewModelScope.launch {
            when (action) {
                RecommendationAction.NOT_INTERESTED -> personalizedRecommendations.markNotInterested(video)
                RecommendationAction.BLOCK_CHANNEL -> personalizedRecommendations.blockChannel(video)
                RecommendationAction.MARK_WATCHED -> personalizedRecommendations.markWatched(video)
                RecommendationAction.WATCH_LATER -> playlistRepository.addToWatchLater(video)
                RecommendationAction.RESTRICT -> {
                    try {
                        libraryPreferences.restrictVideo(video)
                        notice.value = "Đã thêm vào danh sách hạn chế"
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { notice.value = "Chưa lưu được lựa chọn. Hãy thử lại." }
                }
            }
        }
    }

    fun createPlaylistAndAdd(name: String, video: VideoItem) {
        viewModelScope.launch {
            val id = playlistRepository.createPlaylist(name)
            playlistRepository.addVideoToPlaylist(id, video)
        }
    }

    fun openRemotePlaylist(playlist: PlaylistItem) {
        remotePlaylist.value = RemotePlaylistUiState(playlist)
        viewModelScope.launch {
            val result = remotePlaylistRepository.getPlaylist(playlist)
            result.fold(
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
        playbackController.loadAndPlayVideo(
            video = video,
            queueItems = queue,
            queueTitle = title,
            openFullPlayer = true,
            autoRadioAfterEnd = true
        )
        remotePlaylist.value = null
    }

    fun switchSource(sourceType: ContentSourceType) {
        viewModelScope.launch {
            settingsRepository.setActiveSourceType(sourceType, warningAccepted = true)
        }
    }
}

/** Explicit feedback filters the displayed snapshot; it never starts another network load. */
private data class HomeRecommendationFilters(
    val restrictions: List<ContentRestrictionEntity>,
    val hiddenVideos: Set<String>,
    val blockedChannels: Set<String>
) {
    fun apply(videos: List<VideoItem>): List<VideoItem> = ContentRestrictionPolicy.apply(videos.filterNot {
        RecommendationRanking.stableKey(it) in hiddenVideos || RecommendationRanking.normalize(it.channelName) in blockedChannels
    }, restrictions)
}

private data class RecommendationInputs(
    val history: List<HistoryEntity>,
    val favorites: List<com.aeroplay.app.core.database.FavoriteEntity>,
    val playlists: List<com.aeroplay.app.core.database.PlaylistVideoEntity>,
    val searches: List<com.aeroplay.app.core.database.SearchHistoryEntity>
)
