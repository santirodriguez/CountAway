package com.santiagorodriguez.countaway.countdown

import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

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
    fun equalDatesUseCreationTimeForStableOrdering() {
        val date = today.plusDays(5)
        val older = event("older", date, Instant.parse("2026-01-01T00:00:00Z"))
        val newer = event("newer", date, Instant.parse("2026-02-01T00:00:00Z"))

        assertEquals(
            listOf("older", "newer"),
            CountdownEventOrder.sortedForDisplay(listOf(newer, older), today).map { it.id },
        )
    }

    private fun event(
        id: String,
        date: LocalDate,
        createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    ) = CountdownEvent(
        id = id,
        title = id,
        date = date,
        type = EventType.CUSTOM,
        createdAt = createdAt,
    )
}
