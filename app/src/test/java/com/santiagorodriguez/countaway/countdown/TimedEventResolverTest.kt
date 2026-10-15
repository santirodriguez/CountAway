package com.santiagorodriguez.countaway.countdown

import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.RepeatRule
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TimedEventResolverTest {
    private val zone = ZoneId.of("America/New_York")
    private val event = CountdownEvent(
        id = "timed", title = "Launch", date = LocalDate.of(2026, 10, 18),
        type = EventType.EVENT, createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        eventTime = LocalTime.of(16, 30), eventZone = zone,
    )

    @Test fun dateOnlyNeverInventsAMidnightTarget() {
        assertNull(TimedEventResolver.resolve(event.copy(eventTime = null, eventZone = null),
            ZonedDateTime.of(2026, 10, 18, 8, 0, 0, 0, zone)))
    }

    @Test fun fourUnitsAreDecomposedInsteadOfAccumulatedHours() {
        val target = event.date.atTime(event.eventTime).atZone(zone)
        val now = target.minusDays(3).minusHours(7).minusMinutes(12).minusSeconds(5)
        val value = TimedEventResolver.resolve(event, now)!!
        assertEquals(TimedCountDirection.REMAINING, value.direction)
        assertEquals(3L, value.days)
        assertEquals(7L, value.hours)
        assertEquals(12L, value.minutes)
        assertEquals(5L, value.seconds)
        assertEquals("3d · 07h · 12m · 05s", value.compact())
    }

    @Test fun oneOffCountdownFinishesAtSelectedHour() {
        assertEquals(TimedCountDirection.REMAINING, TimedEventResolver.resolve(
            event, event.date.atTime(16, 29, 59).atZone(zone))!!.direction)
        val done = TimedEventResolver.resolve(
            event, event.date.atTime(16, 30, 1).atZone(zone))!!
        assertEquals(TimedCountDirection.ELAPSED, done.direction)
        assertEquals(1L, done.seconds)
    }

    @Test fun countUpBeforeAndAfterTargetHasCorrectDirection() {
        val up = event.copy(countMode = CountMode.COUNT_UP)
        assertEquals(TimedCountDirection.STARTS_IN, TimedEventResolver.resolve(
            up, event.date.atTime(16, 29).atZone(zone))!!.direction)
        assertEquals(TimedCountDirection.ELAPSED, TimedEventResolver.resolve(
            up, event.date.atTime(16, 31).atZone(zone))!!.direction)
    }

    @Test fun subsecondPositiveRemainingRoundsUp() {
        val target = event.date.atTime(event.eventTime).atZone(zone)
        assertEquals(1L, TimedEventResolver.resolve(event, target.minusNanos(1_000_000))!!.seconds)
        assertEquals(0L, TimedEventResolver.resolve(event, target.plusNanos(1_000_000))!!.seconds)
    }

    @Test fun recurringCountdownSkipsPassedLocalTimeToday() {
        val weekly = event.copy(date = LocalDate.of(2026, 10, 11), repeatRule = RepeatRule.WEEKLY)
        val count = TimedEventResolver.resolve(weekly, ZonedDateTime.of(
            2026, 10, 18, 17, 0, 0, 0, zone))!!
        assertEquals(LocalDate.of(2026, 10, 25), count.occurrenceDate)
        assertEquals(TimedCountDirection.REMAINING, count.direction)
    }

    @Test fun savedZoneIsStableThroughDeviceTravel() {
        val now = ZonedDateTime.of(2026, 10, 18, 18, 0, 0, 0, ZoneId.of("Europe/Paris"))
        val value = TimedEventResolver.resolve(event, now)!!
        assertEquals(event.date.atTime(16, 30).atZone(zone).toInstant(), value.target.toInstant())
        assertEquals(zone, value.target.zone)
    }

    @Test fun dstGapMovesNonexistentWallTimeForwardDeterministically() {
        val gap = event.copy(date = LocalDate.of(2026, 3, 8), eventTime = LocalTime.of(2, 30))
        val value = TimedEventResolver.resolve(gap, ZonedDateTime.of(
            2026, 3, 7, 12, 0, 0, 0, zone))!!
        assertEquals(LocalTime.of(3, 30), value.target.toLocalTime())
    }

    @Test fun dstDayHasOnlyTwentyThreeElapsedHours() {
        val target = event.copy(date = LocalDate.of(2026, 3, 9), eventTime = LocalTime.of(9))
        val now = ZonedDateTime.of(2026, 3, 8, 9, 0, 0, 0, zone)
        val value = TimedEventResolver.resolve(target, now)!!
        assertEquals(0L, value.days)
        assertEquals(23L, value.hours)
    }
}
