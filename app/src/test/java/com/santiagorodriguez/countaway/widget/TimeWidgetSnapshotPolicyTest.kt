package com.santiagorodriguez.countaway.widget

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeWidgetSnapshotPolicyTest {
    private val now = Instant.parse("2026-01-05T12:00:00Z")

    @Test fun remainingRoundsUpAndElapsedRoundsDownAcrossDayBoundaries() {
        val day = Duration.ofDays(1)
        assertEquals(1L, TimeWidgetSnapshotPolicy.calculate(now, now.plus(day).minusSeconds(1)).days)
        assertEquals(0L, TimeWidgetSnapshotPolicy.calculate(now, now.minus(day).plusSeconds(1)).days)
        assertEquals(23L, TimeWidgetSnapshotPolicy.calculate(now, now.minus(day).plusSeconds(1)).hours)
        val elapsed = TimeWidgetSnapshotPolicy.calculate(now, now.minus(day))
        assertEquals(1L, elapsed.days)
        assertEquals(0L, elapsed.hours)
        assertFalse(TimeWidgetSnapshotPolicy.calculate(now, now).underHour)
        assertTrue(TimeWidgetSnapshotPolicy.calculate(now, now.plusNanos(1)).underHour)
    }

    @Test fun delayedRefreshKeepsTheOriginalReadingAndStampTogether() {
        val target = now.plus(Duration.ofHours(77)).plus(Duration.ofMinutes(42))
        val first = TimeWidgetSnapshotPolicy.calculate(now, target)
        assertEquals(TimeWidgetSnapshot(3, 6, false, now), first)
        val refresh = now.plus(Duration.ofHours(30))
        assertEquals(TimeWidgetSnapshot(2, 0, false, refresh),
            TimeWidgetSnapshotPolicy.calculate(refresh, target))
        assertEquals(now, first.calculatedAt)
        assertEquals(6L, first.hours)
    }
}
