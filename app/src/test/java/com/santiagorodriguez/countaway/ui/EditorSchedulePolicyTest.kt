package com.santiagorodriguez.countaway.ui

import com.santiagorodriguez.countaway.model.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class EditorSchedulePolicyTest {
    private val newYork = ZoneId.of("America/New_York")
    private val weeklyAnchor = LocalDate.of(2026, 10, 11)
    private val time = LocalTime.of(16, 30)

    @Test fun weeklyTimedSummaryUsesTheSameOccurrenceBeforeAtAndAfterTheClock() {
        fun at(hour: Int, minute: Int, second: Int = 0) =
            ZonedDateTime.of(2026, 10, 18, hour, minute, second, 0, newYork)
        assertEquals(LocalDate.of(2026, 10, 18),
            EditorSchedulePolicy.nextOccurrenceDate(
                weeklyAnchor, RepeatRule.WEEKLY, time, newYork, at(16, 29)))
        assertEquals(LocalDate.of(2026, 10, 18),
            EditorSchedulePolicy.nextOccurrenceDate(
                weeklyAnchor, RepeatRule.WEEKLY, time, newYork, at(16, 30)))
        assertEquals(LocalDate.of(2026, 10, 25),
            EditorSchedulePolicy.nextOccurrenceDate(
                weeklyAnchor, RepeatRule.WEEKLY, time, newYork, at(16, 30, 1)))
        assertEquals(at(16, 30, 1).toInstant(),
            EditorSchedulePolicy.nextRefreshBoundary(
                weeklyAnchor, RepeatRule.WEEKLY, time, newYork, at(16, 29)))
    }

    @Test fun dateOnlyKeepsCivilDaySemanticsAndDoesNotScheduleHourlyRefresh() {
        val now = ZonedDateTime.of(2026, 10, 18, 17, 0, 0, 0, newYork)
        assertEquals(LocalDate.of(2026, 10, 18),
            EditorSchedulePolicy.nextOccurrenceDate(
                weeklyAnchor, RepeatRule.WEEKLY, null, null, now))
        assertNull(EditorSchedulePolicy.nextRefreshBoundary(
            weeklyAnchor, RepeatRule.WEEKLY, null, null, now))
        assertNull(EditorSchedulePolicy.nextRefreshBoundary(
            weeklyAnchor, RepeatRule.NONE, time, newYork, now))
    }

    @Test fun monthlyAndLeapDayRepeatsPreserveTheDomainRules() {
        val now = ZonedDateTime.of(2027, 2, 1, 12, 0, 0, 0, newYork)
        assertEquals(LocalDate.of(2027, 2, 28),
            EditorSchedulePolicy.nextOccurrenceDate(
                LocalDate.of(2027, 1, 31), RepeatRule.MONTHLY, time, newYork, now))
        assertEquals(LocalDate.of(2027, 2, 28),
            EditorSchedulePolicy.nextOccurrenceDate(
                LocalDate.of(2024, 2, 29), RepeatRule.YEARLY, time, newYork, now))
    }

    @Test fun savedEventZoneControlsTheNextOccurrenceWhileTraveling() {
        val paris = ZoneId.of("Europe/Paris")
        val afterTargetInNewYork = ZonedDateTime.of(2026, 10, 18, 17, 0, 0, 0, newYork)
            .withZoneSameInstant(paris)
        assertEquals(LocalDate.of(2026, 10, 25),
            EditorSchedulePolicy.nextOccurrenceDate(
                weeklyAnchor, RepeatRule.WEEKLY, time, newYork, afterTargetInNewYork))
    }
}
