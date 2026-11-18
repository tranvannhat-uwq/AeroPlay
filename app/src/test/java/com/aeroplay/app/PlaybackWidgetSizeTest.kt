package com.aeroplay.app

import com.aeroplay.app.playback.widget.WidgetSize
import com.aeroplay.app.playback.widget.WidgetAppearance
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackWidgetSizeTest {
    @Test fun compactWidthsKeepQuickControlsEvenWhenTall() {
        assertEquals(WidgetSize.QUICK, WidgetSize.fromDp(106, 110))
        assertEquals(WidgetSize.QUICK, WidgetSize.fromDp(210, 310))
    }

    @Test fun mediumWidthsAndShortHostsUseHorizontalLayout() {
        assertEquals(WidgetSize.WIDE, WidgetSize.fromDp(211, 105))
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
}
