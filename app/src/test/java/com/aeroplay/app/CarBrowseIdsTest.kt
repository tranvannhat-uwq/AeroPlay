package com.aeroplay.app

import com.aeroplay.app.playback.car.CarBrowseIds
import org.junit.Assert.*
import org.junit.Test

class CarBrowseIdsTest {
    @Test fun stableIdsKeepSourcesDistinctAndRoundTripUnicode() {
        val original = "device_/nhạc/chill + 01"
        val id = CarBrowseIds.track("DEVICE_LOCAL", original)
        assertEquals("DEVICE_LOCAL" to original, CarBrowseIds.decodeTrack(id))
        assertNotEquals(id, CarBrowseIds.track("EXPERIMENTAL_EXTRACTOR", original))
        assertEquals(id, CarBrowseIds.track("DEVICE_LOCAL", original))
    }

    @Test fun malformedIdsAndUnknownSourcesAreRejected() {
        for (id in listOf("https://example.com/track", "car/track/OTHER/YQ", "car/track/DEVICE_LOCAL/!", "car/track/DEVICE_LOCAL/", "car/track/DEVICE_LOCAL/YQ==", "car/track/DEVICE_LOCAL/YQ/extra")) {
            assertNull(id, CarBrowseIds.decodeTrack(id))
        }
    }

    @Test fun paginationHandlesLegacyUnpagedRequestsAndOverflow() {
        val items = (1..120).toList()
        assertEquals((11..20).toList(), CarBrowseIds.page(items, 1, 10))
        assertEquals(100, CarBrowseIds.page(items, 0, Int.MAX_VALUE).size)
        assertTrue(CarBrowseIds.page(items, Int.MAX_VALUE, Int.MAX_VALUE).isEmpty())
        assertTrue(CarBrowseIds.page(emptyList<Int>(), 0, 20).isEmpty())
    }

    @Test fun invalidPaginationIsRejected() {
        for ((page, size) in listOf(-1 to 10, 0 to 0, 1 to -2)) {
            assertThrows(IllegalArgumentException::class.java) { CarBrowseIds.page(listOf(1), page, size) }
        }
    }
}
