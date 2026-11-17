package com.aeroplay.app

import com.aeroplay.app.core.database.ContentRestrictionEntity
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.data.repository.ContentRestrictionPolicy
import com.aeroplay.app.data.repository.SmartShufflePlanner
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SmartShufflePlannerTest {
    private fun video(id: String, artist: String = id, title: String = "Song $id") =
        VideoItem(id, "https://example.invalid/$id", title, artist, thumbnailUrl = "", durationSeconds = 180)

    @Test fun emptyAndSingleItemAreSafe() {
        assertTrue(SmartShufflePlanner.plan(emptyList()).isEmpty())
        val only = video("one")
        assertEquals(listOf(only), SmartShufflePlanner.plan(listOf(only), only))
    }

    @Test fun keepsEveryUniqueItemAndAvoidsImmediatelyRepeatingCurrent() {
        val items = (0..30).map { video("$it", "Artist ${it % 4}") }
        repeat(20) { seed ->
            val result = SmartShufflePlanner.plan(items + items.take(8), items.first(), Random(seed))
            assertEquals(items.toSet(), result.toSet())
            assertEquals(items.size, result.size)
            assertNotEquals(items.first().id, result.first().id)
        }
    }

    @Test fun separatesArtistsWhenPossibleAndRetainsDifferentSources() {
        val items = (0..3).flatMap { artist -> (0..3).map { video("$artist-$it", "Artist $artist") } }
        val result = SmartShufflePlanner.plan(items, random = Random(8))
        assertTrue(result.zipWithNext().none { (a, b) -> a.channelName == b.channelName })
        val sameIdOtherSource = items.first().copy(sourceType = ContentSourceType.EXPERIMENTAL_EXTRACTOR)
        assertEquals(items.size + 1, SmartShufflePlanner.plan(items + sameIdOtherSource).size)
    }

    @Test fun finiteItemsTakePriorityOverAnEndlessLiveStream() {
        val clip = video("clip")
        assertEquals(listOf(clip), SmartShufflePlanner.plan(listOf(video("live").copy(isLive = true), clip)))
    }

    @Test fun styleLimitsReduceExposureAndCanBeRemoved() {
        val remix = (0..8).map { video("remix-$it", title = "Summer $it EDM Remix") }
        val acoustic = (0..7).map { video("acoustic-$it", title = "Harbor $it Acoustic") }
        val restriction = ContentRestrictionEntity("style:remix", "style", "remix", "")
        val filtered = ContentRestrictionPolicy.apply(remix + acoustic, listOf(restriction))
        assertEquals(acoustic, filtered.take(acoustic.size))
        assertTrue(filtered.count { it in remix } <= 2)
        assertEquals(remix + acoustic, ContentRestrictionPolicy.apply(remix + acoustic, emptyList()))
        assertTrue(ContentRestrictionPolicy.preferredSeeds(remix, listOf(restriction)).isEmpty())
    }
}
