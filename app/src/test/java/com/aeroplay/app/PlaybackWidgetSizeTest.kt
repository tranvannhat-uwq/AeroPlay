package com.aeroplay.app

import com.aeroplay.app.playback.widget.WidgetSize
import com.aeroplay.app.playback.widget.WidgetAppearance
import com.aeroplay.app.playback.widget.AdaptiveWidgetColorScheme
import com.aeroplay.app.playback.widget.WidgetAccent
import com.aeroplay.app.playback.widget.WidgetLayoutPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackWidgetSizeTest {
    @Test fun compactWidthsKeepQuickControlsEvenWhenTall() {
        assertEquals(WidgetSize.QUICK, WidgetSize.fromDp(106, 110))
        assertEquals(WidgetSize.QUICK, WidgetSize.fromDp(210, 310))
    }

    @Test fun mediumWidthsAndShortHostsUseHorizontalLayout() {
        assertEquals(WidgetSize.QUICK, WidgetSize.fromDp(211, 105))
        assertEquals(WidgetSize.WIDE, WidgetSize.fromDp(211, 122))
        assertEquals(WidgetSize.WIDE, WidgetSize.fromDp(320, 124))
        assertEquals(WidgetSize.WIDE, WidgetSize.fromDp(239, 300))
    }

    @Test fun expandedLayoutRequiresBothDimensions() {
        assertEquals(WidgetSize.WIDE, WidgetSize.fromDp(240, 254))
        assertEquals(WidgetSize.EXPANDED, WidgetSize.fromDp(240, 255))
        assertEquals(WidgetSize.EXPANDED, WidgetSize.fromDp(328, 290))
    }

    @Test fun glassOpacityMapsToAnIndependentEightBitAlpha() {
        assertEquals(71, WidgetAppearance().fillAlpha)
        assertEquals(0, WidgetAppearance(opacity = 0).fillAlpha)
        assertEquals(89, WidgetAppearance(opacity = 35).fillAlpha)
        assertEquals(255, WidgetAppearance(opacity = 100).fillAlpha)
    }

    @Test fun customOpacityIsAppliedExactlyAtEverySliderValue() {
        for (dark in listOf(false, true)) {
            for (percent in 0..100) {
                val appearance = WidgetAppearance(opacity = percent, customOpacity = true)
                val colors = AdaptiveWidgetColorScheme.create(dark, appearance)
                assertEquals(appearance.fillAlpha, colors.guardedAlpha)
            }
            val auto = AdaptiveWidgetColorScheme.create(dark, WidgetAppearance(opacity = 0))
            assertTrue(auto.guardedAlpha > 0)
            val high = AdaptiveWidgetColorScheme.create(dark,
                WidgetAppearance(opacity = 0, customOpacity = true, highContrast = true))
            assertTrue(high.guardedAlpha >= 222)
        }
    }

    @Test fun textAndProgressRemainReadableAgainstAdverseWallpaper() {
        for (dark in listOf(false, true)) {
            for (accent in WidgetAccent.entries) {
                val appearance = WidgetAppearance(opacity = 0, accent = accent)
                val theme = AdaptiveWidgetColorScheme.create(dark, appearance, dynamic = 0xFF888888.toInt())
                val worst = AdaptiveWidgetColorScheme.worstSurface(dark, theme.guardedAlpha)
                assertTrue(AdaptiveWidgetColorScheme.contrast(theme.ink, worst) >= 4.5)
                assertTrue(AdaptiveWidgetColorScheme.contrast(theme.secondary, worst) >= 4.5)
                assertTrue(AdaptiveWidgetColorScheme.contrast(theme.accent, theme.rail) >= 3.0)
                val high = AdaptiveWidgetColorScheme.create(dark, appearance.copy(highContrast = true))
                assertTrue(high.guardedAlpha > theme.guardedAlpha)
                assertEquals(0, high.opticsAlpha)
            }
        }
    }

    @Test fun customPearlGlassUsesLightTextOnDarkWallpaperWithoutChangingOpacity() {
        val wallpaper = 0xFF102036.toInt()
        val appearance = WidgetAppearance(opacity = 14, customOpacity = true)
        val theme = AdaptiveWidgetColorScheme.create(dark = false, appearance = appearance, wallpaper = wallpaper)
        assertTrue(AdaptiveWidgetColorScheme.prefersDarkGlass(wallpaper))
        assertFalse(theme.dark)
        assertTrue(theme.lightInk)
        assertEquals(appearance.fillAlpha, theme.guardedAlpha)
    }

    @Test fun narrowAndLargeFontHostsTrimSupplementaryContent() {
        assertFalse(WidgetLayoutPlan(268, 280, 1f).secondaryControls)
        assertTrue(WidgetLayoutPlan(290, 290, 1f).secondaryControls)
        assertEquals(1, WidgetLayoutPlan(290, 310, 1.5f).titleLines)
        assertFalse(WidgetLayoutPlan(255, 151, 1.5f).times)
    }
}
