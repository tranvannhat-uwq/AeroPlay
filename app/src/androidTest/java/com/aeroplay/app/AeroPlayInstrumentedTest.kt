package com.aeroplay.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aeroplay.app.core.database.AeroPlayDao
import com.aeroplay.app.core.database.AeroPlayDatabase
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.data.repository.HistoryRepositoryImpl
import com.aeroplay.app.data.repository.PlaylistRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AeroPlayInstrumentedTest {

    private lateinit var database: AeroPlayDatabase
    private lateinit var dao: AeroPlayDao
    private lateinit var historyRepo: HistoryRepositoryImpl
    private lateinit var playlistRepo: PlaylistRepositoryImpl

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AeroPlayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.aeroPlayDao()
        historyRepo = HistoryRepositoryImpl(dao)
        playlistRepo = PlaylistRepositoryImpl(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun roomLibrary_favoritesHistoryAndPlaylistsWorkCorrectly() = runBlocking {
        val sampleVideo = VideoItem(
            id = "demo_bbb",
            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            title = "Big Buck Bunny",
            channelName = "Blender Studio",
            thumbnailUrl = "https://example.org/thumb.jpg",
            durationSeconds = 596L,
            sourceType = ContentSourceType.DEMO_LOCAL
        )

        // 1. Test Favorites (#16)
        val addedFav = historyRepo.toggleFavorite(sampleVideo)
        assertTrue(addedFav)
        assertEquals(1, historyRepo.observeFavorites().first().size)

        val removedFav = historyRepo.toggleFavorite(sampleVideo)
        assertFalse(removedFav)
        assertTrue(historyRepo.observeFavorites().first().isEmpty())

        // 2. Test Watch History & Saved Playback Position (#5, #18)
        historyRepo.recordWatchAndPosition(sampleVideo, positionMs = 120_000L, durationMs = 596_000L)
        val savedPos = historyRepo.getSavedPosition("demo_bbb")
        assertEquals(120_000L, savedPos?.positionMs)
        assertEquals(1, historyRepo.observeWatchHistory().first().size)

        historyRepo.clearAllHistory()
        assertTrue(historyRepo.observeWatchHistory().first().isEmpty())

        // 3. Test Create, Rename, and Delete Playlist (#17)
        val plId = playlistRepo.createPlaylist("Nhạc Nghe Nền", "Playlist test")
        playlistRepo.addVideoToPlaylist(plId, sampleVideo)
        assertEquals(1, playlistRepo.observePlaylistVideos(plId).first().size)

        playlistRepo.renamePlaylist(plId, "Nhạc Yêu Thích 2026", "Updated")
        assertEquals("Nhạc Yêu Thích 2026", playlistRepo.observePlaylists().first().first().name)

        playlistRepo.deletePlaylist(plId)
        assertTrue(playlistRepo.observePlaylists().first().isEmpty())
    }
}
