package com.santiagorodriguez.countaway.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSizeTest {
    @Test
    fun oneCellAndSmallDimensionsUseCompactLayout() {
        assertEquals(WidgetSize.COMPACT, WidgetSize.fromDimensions(40, 40))
        assertEquals(WidgetSize.COMPACT, WidgetSize.fromDimensions(90, 100))
        assertEquals(WidgetSize.COMPACT, WidgetSize.fromDimensions(160, 60))
    }

    @Test
    fun normalDimensionsUseStandardLayout() {
        assertEquals(WidgetSize.STANDARD, WidgetSize.fromDimensions(120, 80))
        assertEquals(WidgetSize.STANDARD, WidgetSize.fromDimensions(179, 119))
    }

    @Test
    fun roomyDimensionsUseLargeLayout() {
        assertEquals(WidgetSize.LARGE, WidgetSize.fromDimensions(180, 120))
        assertEquals(WidgetSize.LARGE, WidgetSize.fromDimensions(260, 180))
    }
}
