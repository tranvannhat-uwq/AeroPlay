package com.aeroplay.app

import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.data.repository.AutoMixPlanner
import com.aeroplay.app.data.repository.SongIdentityResolver
import org.junit.Assert.*
import org.junit.Test

class AutoMixPlannerTest {
    private val youtube = ContentSourceType.EXPERIMENTAL_EXTRACTOR
    private fun video(id: String, title: String, artist: String = id, views: Long = -1) =
        VideoItem(id, "https://www.youtube.com/watch?v=$id", title, artist,
            thumbnailUrl = "", durationSeconds = 180, viewCount = views, sourceType = youtube)

    @Test fun oneSongAndAlternateUploadsDoNotBecomeAMix() {
        val pool = listOf(video("a", "Harbor Lights - Official Audio"),
            video("b", "Harbor Lights Lyrics"), video("c", "Harbor Lights Cover"))
        assertTrue(AutoMixPlanner.build(pool, youtube, fromMusicSearch = pool.map { "${youtube.name}:${it.id}" }.toSet()).isEmpty())
        assertTrue(AutoMixPlanner.build(emptyList(), youtube).isEmpty())
    }

    @Test fun seededMixRejectsAlternateSongVersionsAndConflictingGenreAndLimitsArtist() {
        val seed = video("a", "Harbor Lights acoustic", "Artist")
        val pool = listOf(seed, video("b", "Harbor Lights acoustic Lyrics"),
            video("c", "Summer Breeze acoustic", "Artist"), video("d", "Winter Garden acoustic", "Artist"),
            video("e", "Golden Mountains acoustic"), video("f", "Midnight Ocean acoustic"),
            video("g", "Thunder Road metal rock"))
        val mix = AutoMixPlanner.build(pool, youtube, seed).first { it.id.endsWith(":for-you") }
        assertEquals(seed.id, mix.videos.first().id)
        assertFalse(mix.videos.any { it.id == "b" || it.id == "g" })
        assertTrue(mix.videos.count { it.channelName == "Artist" } <= 2)
        mix.videos.forEachIndexed { index, item ->
            assertTrue(mix.videos.drop(index + 1).none { SongIdentityResolver.likelySameSong(item.title, it.title) })
        }
    }

    @Test fun popularMixUsesRealPositiveViewCountsInDescendingOrder() {
        val pool = listOf(video("a", "Harbor Lights pop", views = 100),
            video("b", "Summer Breeze pop", views = 900), video("c", "Winter Garden pop", views = 300),
            video("d", "Golden Mountains pop"))
        val mix = AutoMixPlanner.build(pool, youtube).first { it.id.endsWith(":popular") }
        assertEquals(listOf(900L, 300L, 100L), mix.videos.map { it.viewCount })
    }

    @Test fun musicSearchAllowsUnlabeledSongsButNotUnrelatedOrLiveCandidates() {
        val pool = listOf(video("a", "Harbor Lights"), video("b", "Summer Breeze"), video("c", "Winter Garden"))
        val mixed = pool + video("news", "Weather forecast") + video("live", "Piano music").copy(isLive = true)
        val mixes = AutoMixPlanner.build(mixed, youtube, fromMusicSearch = pool.map { "${youtube.name}:${it.id}" }.toSet())
        assertTrue(mixes.isNotEmpty())
        assertEquals("Bản phối khám phá", mixes.first().title)
        assertEquals(pool.map { it.id }.toSet(), mixes.first().videos.map { it.id }.toSet())
    }

    @Test fun openMediaIsLabeledAsContentAndNeverMixedWithYouTube() {
        val local = listOf(video("a", "First Movie"), video("b", "Second Film"), video("c", "Third Story"))
            .map { it.copy(sourceType = ContentSourceType.DEMO_LOCAL) }
        val mix = AutoMixPlanner.build(local + video("youtube", "Favorite Song audio"), ContentSourceType.DEMO_LOCAL).single()
        assertFalse(mix.isMusic)
        assertEquals(3, mix.videos.size)
        assertTrue(mix.videos.all { it.sourceType == ContentSourceType.DEMO_LOCAL })
    }

    @Test fun normalSongMixDoesNotFillUpWithHourLongCompilations() {
        val songs = listOf(video("a", "Harbor Lights pop"), video("b", "Summer Breeze pop"), video("c", "Winter Garden pop"))
        val mixes = AutoMixPlanner.build(songs + video("long", "Greatest Hits pop").copy(durationSeconds = 7_200), youtube)
        assertTrue(mixes.isNotEmpty())
        assertFalse(mixes.flatMap { it.videos }.any { it.id == "long" })
    }
}
