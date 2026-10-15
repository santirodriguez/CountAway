package com.santiagorodriguez.countaway.countdown

import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class CountdownEventOrderTest {
    private val today = LocalDate.of(2026, 8, 7)

    @Test
    fun activeEventsComeFirstNearestToFarthestThenPastNewestToOldest() {
        val events = listOf(
            event("past-old", today.minusDays(10)),
            event("future-far", today.plusDays(30)),
            event("today", today),
            event("past-new", today.minusDays(1)),
            event("future-near", today.plusDays(2)),
        )

        val result = CountdownEventOrder.sortedForDisplay(events, today).map { it.id }

        assertEquals(
            listOf("today", "future-near", "future-far", "past-new", "past-old"),
            result,
        )
    }

    @Test
    fun yearlyEventsSortByTheirNextOccurrence() {
        val recurring = event(
            "recurring",
            LocalDate.of(2020, 8, 8),
            repeatRule = RepeatRule.YEARLY,
        )
        val oneShot = event("one-shot", today.plusDays(5))
        val past = event("past", today.minusDays(1))

        assertEquals(
            listOf("recurring", "one-shot", "past"),
            CountdownEventOrder.sortedForDisplay(listOf(past, oneShot, recurring), today).map { it.id },
        )
    }

    @Test
    fun countUpsSitBetweenActiveAndPastCountdownsNewestStartFirst() {
        val events = listOf(
            event("past", today.minusDays(1)),
            event("up-old", today.minusDays(30), countMode = CountMode.COUNT_UP),
            event("future", today.plusDays(2)),
            event("up-new", today.minusDays(2), countMode = CountMode.COUNT_UP),
        )

        assertEquals(
            listOf("future", "up-new", "up-old", "past"),
            CountdownEventOrder.sortedForDisplay(events, today).map { it.id },
        )
    }

    @Test
    fun equalDatesUseCreationTimeForStableOrdering() {
        val date = today.plusDays(5)
        val older = event("older", date, Instant.parse("2026-01-01T00:00:00Z"))
        val newer = event("newer", date, Instant.parse("2026-02-01T00:00:00Z"))

        assertEquals(
            listOf("older", "newer"),
            CountdownEventOrder.sortedForDisplay(listOf(newer, older), today).map { it.id },
        )
    }

    @Test
    fun timedOneOffReordersAtSelectedHour() {
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        val morning = event("morning", today).copy(
            eventTime = LocalTime.of(9, 0), eventZone = zone)
        val afternoon = event("afternoon", today).copy(
            eventTime = LocalTime.of(17, 0), eventZone = zone)
        val countUp = event("up", today.minusDays(2), countMode = CountMode.COUNT_UP)
        val yesterday = event("yesterday", today.minusDays(1))
        val events = listOf(yesterday, afternoon, countUp, morning)
        val before = ZonedDateTime.of(2026, 8, 7, 8, 0, 0, 0, zone)
        val after = before.plusHours(2)
        assertEquals(listOf("morning", "afternoon", "up", "yesterday"),
            CountdownEventOrder.sortedForDisplay(events, today, before).map { it.id })
        assertEquals(listOf("afternoon", "up", "morning", "yesterday"),
            CountdownEventOrder.sortedForDisplay(events, today, after).map { it.id })
    }

    @Test
    fun timedWeeklyOccurrenceMovesToNextWeekAfterItsHour() {
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        val weekly = event("weekly", today.minusWeeks(1), repeatRule = RepeatRule.WEEKLY)
            .copy(eventTime = LocalTime.of(9, 0), eventZone = zone)
        val later = event("later-today", today)
            .copy(eventTime = LocalTime.of(17, 0), eventZone = zone)
        val events = listOf(weekly, later)
        val before = ZonedDateTime.of(2026, 8, 7, 8, 0, 0, 0, zone)
        val after = before.plusHours(2)
        assertEquals(listOf("weekly", "later-today"),
            CountdownEventOrder.sortedForDisplay(events, today, before).map { it.id })
        assertEquals(listOf("later-today", "weekly"),
            CountdownEventOrder.sortedForDisplay(events, today, after).map { it.id })
    }

    private fun event(
        id: String,
        date: LocalDate,
        createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
        repeatRule: RepeatRule = RepeatRule.NONE,
        countMode: CountMode = CountMode.COUNT_DOWN,
    ) = CountdownEvent(
        id = id,
        title = id,
        date = date,
        type = EventType.CUSTOM,
        createdAt = createdAt,
        repeatRule = repeatRule,
        countMode = countMode,
    )
}
