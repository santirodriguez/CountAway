package com.santiagorodriguez.countaway.widget

import com.santiagorodriguez.countaway.countdown.TimedEventResolver
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.RepeatRule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class TimeWidgetBoundaryPlannerTest {
    @Test fun selectsOnlyTheEarliestStrictlyFutureOccurrenceAcrossInstances() {
        val now = Instant.parse("2026-10-16T15:00:00Z")
        assertEquals(now.plusSeconds(12), TimeWidgetBoundaryPlanner.nextUpcoming(
            listOf(now.plusSeconds(90), now.minusSeconds(5), now.plusSeconds(12),
                now, now.plusSeconds(12)), now))
        assertNull(TimeWidgetBoundaryPlanner.nextUpcoming(listOf(now, now.minusSeconds(1)), now))
        assertNull(TimeWidgetBoundaryPlanner.nextUpcoming(emptyList(), now))
    }

    @Test fun weeklyGapRolloverKeepsAnchorAndSavedZoneThroughTravel() {
        val zone = ZoneId.of("America/New_York")
        val event = CountdownEvent(
            id = "weekly-boundary", title = "Weekly rehearsal",
            date = LocalDate.of(2026, 3, 1), type = EventType.EVENT,
            createdAt = Instant.parse("2026-01-01T12:00:00Z"),
            eventTime = LocalTime.of(2, 30), eventZone = zone,
            repeatRule = RepeatRule.WEEKLY,
        )
        val beforeGap = ZonedDateTime.of(2026, 3, 8, 1, 59, 59, 0, zone)
        val gapOccurrence = TimedEventResolver.resolve(event, beforeGap)!!.target
        assertEquals(LocalDate.of(2026, 3, 8), gapOccurrence.toLocalDate())
        assertEquals(LocalTime.of(3, 30), gapOccurrence.toLocalTime())
        assertEquals(gapOccurrence.toInstant(), TimedEventResolver.resolve(
            event, beforeGap.withZoneSameInstant(ZoneId.of("Asia/Tokyo")))!!.target.toInstant())
        assertEquals(gapOccurrence.toInstant(), TimeWidgetBoundaryPlanner.nextUpcoming(
            listOf(gapOccurrence.toInstant()), beforeGap.toInstant()))

        val afterGap = ZonedDateTime.of(2026, 3, 8, 4, 0, 0, 0, zone)
        val nextWeek = TimedEventResolver.resolve(event, afterGap)!!.target
        assertEquals(LocalDate.of(2026, 3, 15), nextWeek.toLocalDate())
        assertEquals(LocalTime.of(2, 30), nextWeek.toLocalTime())
        assertEquals(nextWeek.toInstant(), TimeWidgetBoundaryPlanner.nextUpcoming(
            listOf(gapOccurrence.toInstant(), nextWeek.toInstant()), afterGap.toInstant()))
        assertEquals(LocalDate.of(2026, 3, 1), event.date)
        assertEquals(zone, event.eventZone)
    }
}
