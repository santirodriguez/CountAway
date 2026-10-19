package com.santiagorodriguez.countaway.widget

import com.santiagorodriguez.countaway.model.CountMode
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class TimeWidgetLongRangePolicyTest {
    private val now = Instant.parse("2026-10-18T15:00:00Z")
    private val elapsedMs = 40_000_000L

    @Test fun centuryRangeIsExactInBothDirectionsIncludingDelayedDailyRefresh() {
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        val past1985 = ZonedDateTime.of(1985, 2, 20, 10, 45, 0, 0, zone).toInstant()
        val past1965 = ZonedDateTime.of(1965, 7, 1, 8, 15, 0, 0, zone).toInstant()
        val future2126 = ZonedDateTime.of(2126, 10, 18, 12, 15, 0, 0, zone).toInstant()
        for ((target, mode) in listOf(
            past1985 to CountMode.COUNT_UP, past1965 to CountMode.COUNT_DOWN,
            future2126 to CountMode.COUNT_DOWN, future2126 to CountMode.COUNT_UP,
        )) {
            val plan = TimeWidgetLongRangePolicy.plan(now, target, elapsedMs, mode)!!
            val totalMs = if (mode == CountMode.COUNT_DOWN) {
                Duration.between(now, target).toMillis()
            } else Duration.between(target, now).toMillis()
            val subdayMs = if (plan.subdayClock.countDown) {
                plan.subdayClock.elapsedRealtimeBase - elapsedMs
            } else elapsedMs - plan.subdayClock.elapsedRealtimeBase
            assertTrue(kotlin.math.abs(subdayMs) < TimeWidgetLongRangePolicy.DAY_MS)
            assertEquals(totalMs, plan.signedDays * TimeWidgetLongRangePolicy.DAY_MS + subdayMs)
            for (elapsed in listOf(0L, 3_600_000L, 30L * 3_600_000L)) {
                val nowSubday = if (plan.subdayClock.countDown) {
                    plan.subdayClock.elapsedRealtimeBase - (elapsedMs + elapsed)
                } else elapsedMs + elapsed - plan.subdayClock.elapsedRealtimeBase
                val expected = totalMs + if (mode == CountMode.COUNT_DOWN) -elapsed else elapsed
                assertEquals(expected, plan.signedDays * TimeWidgetLongRangePolicy.DAY_MS + nowSubday)
            }
        }
    }

    @Test fun leapDaysAndDifferentZonesPreserveExactInstants() {
        val a = ZonedDateTime.of(1924, 2, 29, 9, 30, 0, 0,
            ZoneId.of("America/Argentina/Buenos_Aires")).toInstant()
        val b = ZonedDateTime.of(2024, 3, 1, 12, 30, 0, 0,
            ZoneId.of("Asia/Tokyo")).toInstant()
        val plan = TimeWidgetLongRangePolicy.plan(b, a, elapsedMs, CountMode.COUNT_UP)!!
        val subday = elapsedMs - plan.subdayClock.elapsedRealtimeBase
        assertTrue(plan.signedDays >= 36_000)
        assertEquals(Duration.between(a, b).toMillis(),
            plan.signedDays * TimeWidgetLongRangePolicy.DAY_MS + subday)
    }

    @Test fun negativeFractionalSpanNormalizesAtDayBoundary() {
        val plan = TimeWidgetLongRangePolicy.plan(now.plusSeconds(181L * 86_400L),
            now.plusMillis(500), elapsedMs, CountMode.COUNT_DOWN)!!
        assertEquals(-180L, plan.signedDays)
        assertEquals(-TimeWidgetLongRangePolicy.DAY_MS + 500L,
            plan.subdayClock.elapsedRealtimeBase - elapsedMs)
    }

    @Test fun nearRangeRemainsNativeAndLongRangeUsesBoundedBase() {
        assertNotNull(TimeWidgetClockPolicy.plan(now, now.plusSeconds(180L * 86_400L),
            elapsedMs, CountMode.COUNT_DOWN))
        assertNull(TimeWidgetClockPolicy.plan(now, now.plusSeconds(181L * 86_400L),
            elapsedMs, CountMode.COUNT_DOWN))
        val plan = TimeWidgetLongRangePolicy.plan(now,
            now.plusSeconds(181L * 86_400L), elapsedMs, CountMode.COUNT_DOWN)!!
        assertEquals(181L, plan.signedDays)
        assertEquals(elapsedMs, plan.subdayClock.elapsedRealtimeBase)
    }
}
