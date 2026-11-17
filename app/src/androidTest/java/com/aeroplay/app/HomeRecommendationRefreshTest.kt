package com.aeroplay.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aeroplay.app.core.database.AeroPlayDatabase
import com.aeroplay.app.core.database.SearchHistoryEntity
import com.aeroplay.app.core.model.AppSettings
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.data.repository.*
import com.aeroplay.app.data.source.DemoLocalMediaSource
import com.aeroplay.app.feature.home.HomeViewModel
import com.aeroplay.app.feature.home.PersonalizedUiState
import com.aeroplay.app.feature.home.RecommendationAction
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class HomeRecommendationRefreshTest {
    @Test fun progressAndLibraryChangesKeepTheSnapshotUntilPageRefresh() = runBlocking<Unit> {
        withHome {
            val initial = home.personalizedState.value.videos
            val watched = initial.first()
            states.clear()
            repeat(3) { step ->
                history.recordWatchAndPosition(watched, (step + 1) * 5_000L, 120_000)
                history.recordWatchedDuration(watched.id, 5_000, 120_000, (step + 1) * 5_000L)
                if (step == 0) {
                    history.toggleFavorite(watched)
                    val id = playlists.createPlaylist("Snapshot test")
                    playlists.addVideoToPlaylist(id, watched)
                    dao.upsertSearchQuery(SearchHistoryEntity("Blender"))
                    recommendations.recordImpression(watched)
                    recommendations.recordClick(watched)
                    settings.value = settings.value.copy(dataSaverEnabled = true)
                }
                delay(5_100) // Cross the real history persistence interval and the old debounce.
                assertEquals(initial, home.personalizedState.value.videos)
                assertFalse(home.personalizedState.value.loading)
            }
            assertTrue("History changes must not restart recommendation loading", states.none { it.loading })
            assertEquals(1, feedLoads.get())
            assertEquals(15_000L, history.getSavedPosition(watched.id)?.positionMs)
            assertTrue(home.favorites.first { list -> list.any { it.id == watched.id } }.isNotEmpty())

            onMain { home.refresh() }
            await { feedLoads.get() == 2 && !home.personalizedState.value.loading &&
                home.personalizedState.value.videos.none { it.id == watched.id } }
            assertNotEquals(initial, home.personalizedState.value.videos)
        }
    }

    @Test fun explicitFeedbackFiltersCardsAndUndoRestoresThemWithoutReloading() = runBlocking<Unit> {
        withHome {
            val initial = home.personalizedState.value.videos
            val video = initial.first()
            states.clear()
            onMain { home.actOnRecommendation(video, RecommendationAction.WATCH_LATER) }
            withTimeout(5_000) { dao.observeAllPlaylistVideos().first { it.any { it.videoId == video.id } } }
            preferences.restrictVideo(video)
            await { home.personalizedState.value.videos.none { it.id == video.id } }
            preferences.removeRestriction(RecommendationRanking.stableKey(video))
            await { home.personalizedState.value.videos == initial }

            onMain { home.actOnRecommendation(video, RecommendationAction.NOT_INTERESTED) }
            await { home.personalizedState.value.videos == initial.filterNot { it.id == video.id } }
            dao.deleteRecommendationFeedback(RecommendationRanking.stableKey(video))
            await { home.personalizedState.value.videos == initial }

            onMain { home.actOnRecommendation(video, RecommendationAction.BLOCK_CHANNEL) }
            val channel = RecommendationRanking.normalize(video.channelName)
            await { home.personalizedState.value.videos == initial.filterNot { RecommendationRanking.normalize(it.channelName) == channel } }
            assertTrue(states.none { it.loading })
            assertEquals(1, feedLoads.get())
        }
    }

    private suspend fun onMain(block: () -> Unit) = withContext(Dispatchers.Main) { block() }
    private suspend fun await(condition: () -> Boolean) = withTimeout(8_000) {
        while (!condition()) delay(50)
    }

    private class Fixture(val database: AeroPlayDatabase, val graph: YouTubeTestEntryPoint) {
        val dao = database.aeroPlayDao()
        val history = HistoryRepositoryImpl(dao)
        val playlists = PlaylistRepositoryImpl(dao)
        val preferences = LibraryPreferencesRepository(dao)
        val recommendations = PersonalizedRecommendations(dao, DemoLocalMediaSource(), graph.extractor())
        val settings = MutableStateFlow(AppSettings(activeSourceType = ContentSourceType.DEMO_LOCAL))
        val feedLoads = AtomicInteger()
        val states = CopyOnWriteArrayList<PersonalizedUiState>()
        val store = ViewModelStore()
        lateinit var home: HomeViewModel
    }

    private suspend fun withHome(block: suspend Fixture.() -> Unit) = coroutineScope {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = EntryPointAccessors.fromApplication(context, YouTubeTestEntryPoint::class.java)
        val fixture = Fixture(Room.inMemoryDatabaseBuilder(context, AeroPlayDatabase::class.java).build(), graph)
        var observer: Job? = null
        try {
            val seed = graph.content().getHomeFeed(ContentSourceType.DEMO_LOCAL).getOrThrow().first { it.id == "demo_sintel" }
            fixture.history.recordWatchAndPosition(seed, 10_000, 120_000)
            onMain {
                val settings = object : SettingsRepository by graph.settings() {
                    override val settingsFlow = fixture.settings
                    override suspend fun getSettingsSnapshot() = fixture.settings.value
                }
                val content = object : ContentRepository {
                    override suspend fun getHomeFeed(sourceOverride: ContentSourceType?): Result<List<VideoItem>> {
                        fixture.feedLoads.incrementAndGet()
                        return graph.content().getHomeFeed(sourceOverride)
                    }
                }
                fixture.home = ViewModelProvider(fixture.store, object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(content,
                        fixture.history, fixture.playlists, settings, fixture.recommendations, graph.remotePlaylists(),
                        graph.playback(), fixture.preferences) as T
                })[HomeViewModel::class.java]
            }
            observer = launch { fixture.home.personalizedState.collect { fixture.states.add(it) } }
            await { !fixture.home.personalizedState.value.loading && fixture.home.personalizedState.value.videos.isNotEmpty() }
            fixture.block()
        } finally {
            observer?.cancelAndJoin()
            onMain { fixture.store.clear() }
            fixture.database.close()
        }
    }
}
