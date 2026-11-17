package com.aeroplay.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeroplay.app.core.database.AeroPlayDao
import com.aeroplay.app.core.model.AutoMix
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.data.repository.DeviceMediaRepository
import com.aeroplay.app.data.repository.LibraryPreferencesRepository
import com.aeroplay.app.data.repository.RecommendationRanking
import com.aeroplay.app.data.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

data class MixEditorState(
    val mix: AutoMix? = null,
    val query: String = "",
    val candidates: List<VideoItem> = emptyList(),
    val searching: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val message: String = ""
)

@HiltViewModel
class MixEditorViewModel @Inject constructor(
    private val preferences: LibraryPreferencesRepository,
    private val searchRepository: SearchRepository,
    private val deviceMedia: DeviceMediaRepository,
    private val dao: AeroPlayDao
) : ViewModel() {
    private val mutable = MutableStateFlow(MixEditorState())
    val state = mutable.asStateFlow()
    private var local = emptyList<VideoItem>()
    private var searchJob: Job? = null
    private var localJob: Job? = null

    fun open(mix: AutoMix) {
        searchJob?.cancel()
        localJob?.cancel()
        local = emptyList()
        mutable.value = MixEditorState(mix = mix)
        localJob = viewModelScope.launch {
            local = (dao.observeFavorites().first().map { it.toVideoItem() } +
                dao.observeWatchHistory().first().map { it.toVideoItem() } +
                dao.observeAllPlaylistVideos().first().map { it.toVideoItem() } + deviceMedia.videos.first())
                .distinctBy(RecommendationRanking::stableKey)
            if (!mutable.value.searching) updateQuery(mutable.value.query)
        }
    }

    fun close() {
        searchJob?.cancel()
        localJob?.cancel()
        mutable.value = MixEditorState()
    }

    fun updateQuery(query: String) {
        searchJob?.cancel()
        val selected = mutable.value.mix?.videos.orEmpty().map(RecommendationRanking::stableKey).toSet()
        val matches = local.filter { video -> RecommendationRanking.stableKey(video) !in selected &&
            (query.isBlank() || video.title.contains(query, true) || video.channelName.contains(query, true)) }
        mutable.update { it.copy(query = query, candidates = matches.take(50), searching = false, message = "") }
    }

    fun search() {
        val query = mutable.value.query.trim()
        val mix = mutable.value.mix ?: return
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            mutable.update { it.copy(searching = true, message = "") }
            try {
                val source = runCatching { ContentSourceType.valueOf(mix.id.substringBefore(':')) }
                    .getOrDefault(ContentSourceType.EXPERIMENTAL_EXTRACTOR)
                val found = withTimeout(20_000) { searchRepository.search(query, sourceOverride = source).getOrThrow().videos }
                val selected = mutable.value.mix?.videos.orEmpty().map(RecommendationRanking::stableKey).toSet()
                mutable.update { it.copy(searching = false, candidates = (it.candidates + found)
                    .distinctBy(RecommendationRanking::stableKey).filterNot { RecommendationRanking.stableKey(it) in selected }) }
            } catch (_: TimeoutCancellationException) {
                mutable.update { it.copy(searching = false, message = "Tìm kiếm quá lâu. Hãy thử lại.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(searching = false, message = "Chưa tìm được nội dung. Hãy thử lại.") } }
        }
    }

    fun add(video: VideoItem) {
        mutable.update { state ->
            val mix = state.mix ?: return@update state
            state.copy(mix = mix.copy(videos = (mix.videos + video).distinctBy(RecommendationRanking::stableKey)),
                candidates = state.candidates.filterNot { RecommendationRanking.stableKey(it) == RecommendationRanking.stableKey(video) })
        }
    }

    fun remove(video: VideoItem) {
        mutable.update { state -> state.copy(mix = state.mix?.let { mix ->
            mix.copy(videos = mix.videos.filterNot { RecommendationRanking.stableKey(it) == RecommendationRanking.stableKey(video) })
        }) }
    }

    fun restrict(video: VideoItem) {
        viewModelScope.launch {
            try {
                preferences.restrictVideo(video)
                mutable.update { it.copy(message = "Đã thêm vào danh sách hạn chế") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(message = "Chưa lưu được lựa chọn. Hãy thử lại.") } }
        }
    }

    fun save() {
        val snapshot = mutable.value
        val mix = snapshot.mix ?: return
        if (snapshot.saving) return
        mutable.update { it.copy(saving = true, message = "") }
        viewModelScope.launch {
            try {
                preferences.saveMix(mix)
                mutable.update { it.copy(saving = false, saved = true) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(saving = false, message = "Chưa lưu được bản phối. Hãy thử lại.") } }
        }
    }
}
