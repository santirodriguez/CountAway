package com.santiagorodriguez.countaway.countdown

import com.santiagorodriguez.countaway.model.RepeatRule
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Stage A behavior fixtures only; these do not yet test a migrated app model. */
class OptionalTimeStageAContractTest {
    private val newYork = ZoneId.of("America/New_York")

    @Test fun absentTimeIsDifferentFromAnExplicitMidnight() {
        val absent: LocalTime? = null
        val explicit: LocalTime? = LocalTime.MIDNIGHT
        assertNotEquals(absent, explicit)
        assertEquals(0, explicit!!.hour)
    }

    @Test fun targetLaterTodayIsNotAlreadyArrived() {
        val today = LocalDate.of(2026, 10, 14)
        val now = today.atTime(15, 0).atZone(newYork).toInstant()
        val target = today.atTime(20, 0).atZone(newYork).toInstant()
        assertEquals(5L, Duration.between(now, target).toHours())
        assertTrue(target.isAfter(now))
    }

    @Test fun durationDecomposesDaysRatherThanAccumulatingHours() {
        val remaining = 3L * 86_400L + 7L * 3_600L + 12L * 60L + 5L
        assertEquals(3L, remaining / 86_400L)
        assertEquals(7L, remaining % 86_400L / 3_600L)
        assertEquals(12L, remaining % 3_600L / 60L)
        assertEquals(5L, remaining % 60L)
        assertEquals(79L, remaining / 3_600L)
    }

    @Test fun remainingSecondsDoNotShowZeroEarly() {
        val now = Instant.ofEpochMilli(1_000L)
        val target = Instant.ofEpochMilli(1_001L)
        val remainingMillis = Duration.between(now, target).toMillis()
        assertEquals(1L, (remainingMillis + 999L) / 1000L)
        assertEquals(0L, Duration.between(now, target).seconds)
    }

    @Test fun timezoneTravelKeepsTheOriginalInstant() {
        val zoned = LocalDateTime.of(2026, 10, 18, 16, 30).atZone(newYork)
        val converted = zoned.withZoneSameInstant(ZoneId.of("Europe/Paris"))
        assertEquals(zoned.toInstant(), converted.toInstant())
        assertNotEquals(zoned.toLocalTime(), converted.toLocalTime())
    }

    @Test fun nonexistentAndAmbiguousLocalTimesNeedExplicitRules() {
        val gap = LocalDateTime.of(2026, 3, 8, 2, 30)
        val overlap = LocalDateTime.of(2026, 11, 1, 1, 30)
        assertEquals(0, newYork.rules.getValidOffsets(gap).size)
        assertEquals(2, newYork.rules.getValidOffsets(overlap).size)
        assertNotEquals(newYork.rules.getValidOffsets(overlap)[0],
            newYork.rules.getValidOffsets(overlap)[1])
    }

    @Test fun oneCalendarDayPriorIsNotAlwaysTwentyFourHours() {
        val event = ZonedDateTime.of(2026, 3, 8, 9, 0, 0, 0, newYork)
        val reminder = event.minusDays(1)
        assertEquals(LocalDate.of(2026, 3, 7), reminder.toLocalDate())
        assertEquals(9, reminder.hour)
        assertEquals(23L, Duration.between(reminder, event).toHours())
    }

    @Test fun monthlyRecurrenceRetainsTheOriginalDayAnchor() {
        val anchor = LocalDate.of(2027, 1, 31)
        val february = CountdownOccurrenceResolver.nextOccurrenceOnOrAfter(
            anchor, RepeatRule.MONTHLY, LocalDate.of(2027, 2, 1))
        val march = CountdownOccurrenceResolver.nextOccurrenceOnOrAfter(
            anchor, RepeatRule.MONTHLY, LocalDate.of(2027, 3, 1))
        assertEquals(LocalDate.of(2027, 2, 28), february)
        assertEquals(LocalDate.of(2027, 3, 31), march)
        assertEquals(28L, ChronoUnit.DAYS.between(LocalDate.of(2027, 2, 28),
            LocalDate.of(2027, 3, 28)))
    }
}
