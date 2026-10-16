package com.santiagorodriguez.countaway.widget

import com.santiagorodriguez.countaway.model.CountMode
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class TimeWidgetClockPolicyTest {
    private val now = Instant.parse("2026-10-16T13:00:00Z")

    @Test fun countdownUsesNativeTotalHoursWithoutDecomposition() {
        val plan = TimeWidgetClockPolicy.plan(now, now.plusSeconds(3 * 86_400L + 7 * 3_600L + 725),
            20_000L, CountMode.COUNT_DOWN)!!
        assertTrue(plan.countDown)
        assertEquals(20_000L + 3 * 86_400_000L + 7 * 3_600_000L + 725_000L, plan.elapsedRealtimeBase)
    }

    @Test fun countUpRetainsSignThroughSelectedHour() {
        val target = now.plusSeconds(60)
        val before = TimeWidgetClockPolicy.plan(now, target, 15_000, CountMode.COUNT_UP)!!
        val after = TimeWidgetClockPolicy.plan(target.plusSeconds(1), target, 16_000, CountMode.COUNT_UP)!!
        assertFalse(before.countDown)
        assertFalse(after.countDown)
        assertEquals(75_000L, before.elapsedRealtimeBase)
        assertEquals(15_000L, after.elapsedRealtimeBase)
    }

    @Test fun giantDurationMustNotPretendNativeSecondsArePrecise() {
        assertNull(TimeWidgetClockPolicy.plan(now, now.plusSeconds(181 * 86_400L), 50_000, CountMode.COUNT_DOWN))
        assertNull(TimeWidgetClockPolicy.plan(now, now.minusSeconds(181 * 86_400L), 50_000, CountMode.COUNT_UP))
    }
}
