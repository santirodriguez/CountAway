package com.santiagorodriguez.countaway.widget

import org.junit.Assert.*
import org.junit.Test

class TimeWidgetLayoutPlannerTest {
    @Test fun compactOneRowRetainsTitleAndClockWithoutOverflowBudget() {
        val short = TimeWidgetLayoutPlanner.plan(160, 90, 1f)
        assertEquals(1, short.titleLines)
        assertFalse(short.showDate)
        assertFalse(short.showAccent)
        assertFalse(short.showLiveIndicator)
        assertEquals(32f, short.clockMaxSizeSp)
        assertEquals(8, short.verticalPaddingDp)
    }

    @Test fun narrowTallCardsGiveTheTitleRoomToWrap() {
        val tall = TimeWidgetLayoutPlanner.plan(160, 200, 1.3f)
        assertEquals(3, tall.titleLines)
        assertTrue(tall.showDate)
        assertTrue(tall.showAccent)
        assertFalse(tall.showLiveIndicator)
    }

    @Test fun wideShortCardsUseHorizontalRoomWithoutForcingATallWidget() {
        val short = TimeWidgetLayoutPlanner.plan(300, 95, 1.3f)
        assertEquals(1, short.titleLines)
        assertFalse(short.showDate)
        assertFalse(short.showAccent)
    }

    @Test fun largeTextMakesSpaceForNativeClockWithoutDiscardingRoomyDetails() {
        val medium = TimeWidgetLayoutPlanner.plan(240, 180, 2f)
        val roomy = TimeWidgetLayoutPlanner.plan(280, 210, 2f)
        assertEquals(12f, medium.titleSizeSp)
        assertEquals(2, medium.titleLines)
        assertFalse(medium.showDate)
        assertTrue(roomy.showDate)
        assertTrue(roomy.showAccent)
    }

    @Test fun largerCardsKeepTheTimerAsTheVisualFocus() {
        val full = TimeWidgetLayoutPlanner.plan(320, 250, 1f)
        assertEquals(48f, full.clockMaxSizeSp)
        assertTrue(full.showDate)
        assertTrue(full.showLiveIndicator)
    }
}
