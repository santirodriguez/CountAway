package com.santiagorodriguez.countaway.widget

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeWidgetCalendarReadingTest {
    @Test fun midnightDoesNotBecomeADayBeforeTwentyFourElapsedHours() {
        val start = ZonedDateTime.parse("2026-01-30T23:00:00Z")
        val before = TimeWidgetCalendarReading.between(start, start.plusHours(24).minusSeconds(1))
        assertEquals(TimeWidgetCalendarReading(0, 0, 0, 23, 59, true), before)
        assertEquals(TimeWidgetCalendarReading(0, 0, 1, 0, 0, true),
            TimeWidgetCalendarReading.between(start, start.plusHours(24)))
    }

    @Test fun endOfMonthAndLeapYearUseTheEventCalendar() {
        val jan31 = ZonedDateTime.parse("2026-01-31T15:00:00Z")
        val feb28 = ZonedDateTime.parse("2026-02-28T15:00:00Z")
        assertEquals(TimeWidgetCalendarReading(0, 1, 0, 0, 0, true),
            TimeWidgetCalendarReading.between(jan31, feb28))
        assertEquals(0L, TimeWidgetCalendarReading.between(jan31, feb28.minusSeconds(1)).months)
        val leap = ZonedDateTime.parse("2024-02-29T15:00:00Z")
        val yearLater = ZonedDateTime.parse("2025-02-28T15:00:00Z")
        assertEquals(TimeWidgetCalendarReading(1, 0, 0, 0, 0, false),
            TimeWidgetCalendarReading.between(yearLater, leap))
    }

    @Test fun fallBackRetainsElapsedTimeAndNormalizesTheSubdayClock() {
        val start = ZonedDateTime.of(2026, 10, 24, 13, 0, 0, 0, ZoneId.of("Europe/Paris"))
        val after = start.plusDays(1)
        assertEquals(TimeWidgetCalendarReading(0, 0, 1, 1, 0, false),
            TimeWidgetCalendarReading.between(after, start))
        val otherZone = after.withZoneSameInstant(ZoneId.of("Asia/Tokyo"))
        assertEquals(TimeWidgetCalendarReading.between(after, start),
            TimeWidgetCalendarReading.between(otherZone, start))
    }

    @Test fun formatsDirectionOnEitherSideOfZeroAndUsesTheFullRange() {
        val start = ZonedDateTime.parse("1900-01-05T12:00:00Z")
        assertEquals(TimeWidgetCalendarReading(0, 0, 0, 0, 0, false),
            TimeWidgetCalendarReading.between(start, start))
        assertTrue(TimeWidgetCalendarReading.between(start, start.plusSeconds(1)).remaining)
        assertFalse(TimeWidgetCalendarReading.between(start, start.minusSeconds(1)).remaining)
        val future = start.plusYears(200).plusMonths(8).plusDays(17).plusSeconds(3599)
        assertEquals(TimeWidgetCalendarReading(200, 8, 17, 0, 59, true),
            TimeWidgetCalendarReading.between(start, future))
    }
}
