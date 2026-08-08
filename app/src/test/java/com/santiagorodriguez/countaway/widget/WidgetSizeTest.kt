package com.santiagorodriguez.countaway.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSizeTest {
    @Test
    fun oneCellAndSingleRowDimensionsStayCompact() {
        assertEquals(WidgetSize.COMPACT, WidgetSize.fromDimensions(40, 40))
        assertEquals(WidgetSize.COMPACT, WidgetSize.fromDimensions(57, 102))
        assertEquals(WidgetSize.COMPACT, WidgetSize.fromDimensions(127, 51))
        assertEquals(WidgetSize.COMPACT, WidgetSize.fromDimensions(130, 102))
    }

    @Test
    fun twoDimensionalRoomUsesStandardLayout() {
        assertEquals(WidgetSize.STANDARD, WidgetSize.fromDimensions(110, 165))
        assertEquals(WidgetSize.STANDARD, WidgetSize.fromDimensions(130, 220))
        assertEquals(WidgetSize.STANDARD, WidgetSize.fromDimensions(203, 229))
    }

    @Test
    fun genuinelyRoomyDimensionsUseLargeLayout() {
        assertEquals(WidgetSize.LARGE, WidgetSize.fromDimensions(180, 230))
        assertEquals(WidgetSize.LARGE, WidgetSize.fromDimensions(203, 337))
        assertEquals(WidgetSize.LARGE, WidgetSize.fromDimensions(260, 337))
    }
}
