package com.aeroplay.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.aeroplay.app.core.model.PlaybackPosition
import com.aeroplay.app.core.model.UserPlaylist
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.data.repository.HistoryRepository
import com.aeroplay.app.data.repository.PlaylistRepository
import com.aeroplay.app.data.repository.DeviceMediaRepository
import com.aeroplay.app.data.repository.SettingsRepository
import com.aeroplay.app.playback.controller.PlaybackControllerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab(val label: String) {
    HISTORY("Lịch sử xem"),
    DEVICE("Trên thiết bị"),
    FAVORITES("Yêu thích"),
    PLAYLISTS("Danh sách phát")
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val playlistRepository: PlaylistRepository,
    private val deviceMedia: DeviceMediaRepository,
    private val settingsRepository: SettingsRepository,
    private val playbackController: PlaybackControllerManager
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(LibraryTab.HISTORY)
    val selectedTab: StateFlow<LibraryTab> = _selectedTab.asStateFlow()
    val deviceVideos = deviceMedia.videos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val mediaImport = deviceMedia.importState
    val backgroundPlaybackEnabled = settingsRepository.settingsFlow.map { it.backgroundPlaybackEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun enableBackgroundPlayback() {
        viewModelScope.launch { settingsRepository.setBackgroundPlaybackEnabled(true) }
    }

    fun importVideos(uris: List<Uri>) {
        if (uris.isNotEmpty()) {
            selectTab(LibraryTab.DEVICE)
            deviceMedia.importVideos(uris)
        }
    }

    fun removeDeviceVideo(videoId: String) {
        if (playbackController.playbackState.value.currentVideo?.id == videoId) playbackController.dismissPlayer()
        viewModelScope.launch { deviceMedia.remove(videoId) }
    }

    fun renameDeviceVideo(videoId: String, title: String) {
        if (title.isNotBlank()) viewModelScope.launch { deviceMedia.rename(videoId, title) }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId: StateFlow<Long?> = _selectedPlaylistId.asStateFlow()

    val watchHistory: StateFlow<List<VideoItem>> = historyRepository.observeWatchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<VideoItem>> = historyRepository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val savedPositions: StateFlow<Map<String, PlaybackPosition>> =
        historyRepository.observePlaybackPositions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val playlists: StateFlow<List<UserPlaylist>> = playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedPlaylistVideos: StateFlow<List<VideoItem>> = _selectedPlaylistId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else playlistRepository.observePlaylistVideos(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectTab(tab: LibraryTab) {
        _selectedTab.value = tab
        if (tab != LibraryTab.PLAYLISTS) {
            _selectedPlaylistId.value = null
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun openPlaylistDetail(playlistId: Long?) {
        _selectedPlaylistId.value = playlistId
    }

    fun createPlaylist(name: String, description: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            playlistRepository.createPlaylist(name, description)
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String, newDescription: String = "") {
        if (newName.isBlank()) return
        viewModelScope.launch {
            playlistRepository.renamePlaylist(playlistId, newName, newDescription)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            if (_selectedPlaylistId.value == playlistId) {
                _selectedPlaylistId.value = null
            }
            playlistRepository.deletePlaylist(playlistId)
        }
    }

    fun removeVideoFromPlaylist(playlistId: Long, videoId: String) {
        viewModelScope.launch {
            playlistRepository.removeVideoFromPlaylist(playlistId, videoId)
        }
    }

    fun removeFromHistory(videoId: String) {
        viewModelScope.launch {
            historyRepository.removeFromHistory(videoId)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.clearAllHistory()
        }
    }

    fun toggleFavorite(video: VideoItem) {
        viewModelScope.launch {
            historyRepository.toggleFavorite(video)
        }
    }

    fun playVideo(video: VideoItem, list: List<VideoItem>, title: String) {
        playbackController.loadAndPlayVideo(
            video = video,
            queueItems = list,
            queueTitle = title,
            openFullPlayer = true
        )
    }
}
