package com.aeroplay.app

import android.content.ComponentName
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aeroplay.app.playback.service.AeroPlayMediaService
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Device test: verifies a cold browser connection without opening MainActivity or hitting a network. */
@RunWith(AndroidJUnit4::class)
class AndroidAutoLibraryTest {
    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        return checkNotNull(result)
    }

    private fun browser(): MediaBrowser {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val future = onMain {
            MediaBrowser.Builder(context, SessionToken(context, ComponentName(context, AeroPlayMediaService::class.java))).buildAsync()
        }
        return future.get(10, TimeUnit.SECONDS)
    }

    @Test fun coldServiceExposesBrowsableRootAndAudioFolders() {
        val browser = browser()
        try {
            val root = onMain { browser.getLibraryRoot(null) }.get(10, TimeUnit.SECONDS)
            assertEquals(0, root.resultCode)
            assertEquals("car/root", root.value?.mediaId)
            assertEquals(true, root.value?.mediaMetadata?.isBrowsable)
            val children = onMain { browser.getChildren("car/root", 0, Int.MAX_VALUE, null) }.get(10, TimeUnit.SECONDS)
            assertEquals(0, children.resultCode)
            assertEquals(5, children.value?.size)
            assertTrue(children.value!!.all { it.mediaMetadata.isBrowsable == true && it.mediaMetadata.isPlayable == false })
        } finally { onMain { browser.release(); true } }
    }

    @Test fun rootPagingReturnsEmptyAfterLastPage() {
        val browser = browser()
        try {
            val children = onMain { browser.getChildren("car/root", 10, 20, null) }.get(10, TimeUnit.SECONDS)
            assertEquals(0, children.resultCode)
            assertTrue(children.value!!.isEmpty())
        } finally { onMain { browser.release(); true } }
    }
}
