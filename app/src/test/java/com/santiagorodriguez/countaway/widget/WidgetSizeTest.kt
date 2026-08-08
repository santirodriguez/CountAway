package com.santiagorodriguez.countaway.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSizeTest {
    @Test
    fun oneCellAndShortDimensionsUseCompactLayout() {
        assertEquals(WidgetSize.COMPACT, WidgetSize.fromDimensions(40, 40))
        assertEquals(WidgetSize.COMPACT, WidgetSize.fromDimensions(119, 200))
        assertEquals(WidgetSize.COMPACT, WidgetSize.fromDimensions(160, 109))
    }

    @Test
    fun dimensionsWithRoomForThePrimaryCountUseStandardLayout() {
        assertEquals(WidgetSize.STANDARD, WidgetSize.fromDimensions(120, 110))
        assertEquals(WidgetSize.STANDARD, WidgetSize.fromDimensions(179, 169))
        assertEquals(WidgetSize.STANDARD, WidgetSize.fromDimensions(180, 169))
    }

    @Test
    fun roomyDimensionsUseLargeLayout() {
        assertEquals(WidgetSize.LARGE, WidgetSize.fromDimensions(180, 170))
        assertEquals(WidgetSize.LARGE, WidgetSize.fromDimensions(260, 180))
    }
}
