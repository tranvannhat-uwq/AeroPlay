package com.aeroplay.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aeroplay.app.core.database.AeroPlayDatabase
import com.aeroplay.app.core.model.AutoMix
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.data.repository.ContentRestrictionPolicy
import com.aeroplay.app.data.repository.LibraryPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryPreferencesDeviceTest {
    @Test fun editedMixAndLearnedRestrictionsSurviveDatabaseReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "library-preferences-roundtrip-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, AeroPlayDatabase::class.java, name).build()
        var database = open()
        val first = VideoItem("first", "https://example.invalid/first", "Morning Rain Acoustic", "Artist A",
            thumbnailUrl = "https://example.invalid/image", durationSeconds = 180, sourceType = ContentSourceType.EXPERIMENTAL_EXTRACTOR)
        val added = first.copy(id = "added", title = "Evening Sky Acoustic", url = "content://example.invalid/added", sourceType = ContentSourceType.DEVICE_LOCAL)
        try {
            val repository = LibraryPreferencesRepository(database.aeroPlayDao())
            repository.saveMix(AutoMix("EXPERIMENTAL_EXTRACTOR:for-you", "Saved mix", "", listOf(first, added, added)))
            repository.restrictVideo(first)
            repository.restrictStyle("Remix")
            database.close()
            database = open()
            val reopened = LibraryPreferencesRepository(database.aeroPlayDao())
            val mix = reopened.editedMixes.first().single()
            assertTrue(mix.isCustomized)
            assertEquals(listOf(first, added), mix.videos)
            val restrictions = reopened.restrictions.first()
            val candidates = listOf(first) + (0..5).map { first.copy(id = "acoustic-$it", title = "Summer $it Acoustic") } +
                (0..5).map { first.copy(id = "rock-$it", title = "Mountain $it Rock") }
            val allowed = ContentRestrictionPolicy.apply(candidates, restrictions)
            assertFalse(allowed.any { it.id == first.id })
            assertTrue(allowed.count { it.title.contains("Acoustic") } <= 1)
            assertEquals(6, allowed.count { it.title.contains("Rock") })
            reopened.removeRestriction("EXPERIMENTAL_EXTRACTOR:first")
            reopened.removeRestriction("style:remix")
            assertEquals(candidates, ContentRestrictionPolicy.apply(candidates, reopened.restrictions.first()))
            reopened.saveMix(mix.copy(videos = emptyList()))
            assertTrue(reopened.editedMixes.first().single().videos.isEmpty())
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
