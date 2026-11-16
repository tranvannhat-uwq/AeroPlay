package com.aeroplay.app.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.aeroplay.app.core.model.PlaybackState
import com.aeroplay.app.core.model.StreamVariant
import com.aeroplay.app.core.model.UserPlaylist
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.data.repository.HistoryRepository
import com.aeroplay.app.data.repository.PlaylistRepository
import com.aeroplay.app.playback.controller.PlaybackControllerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackController: PlaybackControllerManager,
    private val historyRepository: HistoryRepository,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState
    val playerFlow: StateFlow<Player?> = playbackController.playerFlow

    val isCurrentFavorite: StateFlow<Boolean> = playbackState
        .flatMapLatest { state ->
            val id = state.currentVideo?.id
            if (id.isNullOrBlank()) flowOf(false) else historyRepository.observeIsFavorite(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val playlists: StateFlow<List<UserPlaylist>> = playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun togglePlayPause() = playbackController.togglePlayPause()

    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)

    fun skipForward() = playbackController.skipDelta(10_000L)

    fun skipBackward() = playbackController.skipDelta(-10_000L)

    fun playNext() = playbackController.playNext()

    fun playPrevious() = playbackController.playPrevious()

    fun selectQuality(variant: StreamVariant) = playbackController.selectQualityVariant(variant)

    fun setPlaybackSpeed(speed: Float) = playbackController.setPlaybackSpeed(speed)

    fun setAudioOnlyMode(enabled: Boolean) = playbackController.setAudioOnlyMode(enabled)

    fun minimizePlayer() = playbackController.minimizeToMiniPlayer()

    fun expandPlayer() = playbackController.expandToFullPlayer()

    fun dismissPlayer() = playbackController.dismissPlayer()

    fun retryPlayback() = playbackController.retryCurrentVideo()

    fun playRelatedOrQueueVideo(video: VideoItem, queue: List<VideoItem>, title: String, radioMode: Boolean) {
        playbackController.loadAndPlayVideo(
            video = video,
            queueItems = queue,
            queueTitle = title,
            openFullPlayer = true,
            radioMode = radioMode
        )
    }

    fun toggleFavorite() {
        val current = playbackState.value.currentVideo ?: return
        viewModelScope.launch {
            historyRepository.toggleFavorite(current)
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
