package com.santiagorodriguez.countaway.countdown

import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class EventCountResolverTest {
    @Test
    fun countUpBeforeStartReportsPendingMagnitudeWithoutNegativeElapsedTime() {
        val today = LocalDate.of(2026, 9, 29)
        val value = EventCountResolver.resolve(event(today.plusDays(4), CountMode.COUNT_UP), today)

        assertEquals(CountMode.COUNT_UP, value.mode)
        assertEquals(CountUpState.STARTS_IN, value.countUpState)
        assertEquals(4L, value.magnitude)
        assertEquals(today.plusDays(4), value.displayDate)
        assertNull(value.countdownStatus)
    }

    @Test
    fun countUpOnAndAfterStartUsesCalendarDays() {
        val start = LocalDate.of(2026, 2, 28)
        assertEquals(
            EventCountValue(CountMode.COUNT_UP, start, 0L, countUpState = CountUpState.ELAPSED),
            EventCountResolver.resolve(event(start, CountMode.COUNT_UP), start),
        )
        assertEquals(
            2L,
            EventCountResolver.resolve(
                event(start, CountMode.COUNT_UP),
                LocalDate.of(2026, 3, 2),
            ).magnitude,
        )
    }

    @Test
    fun countUpIgnoresRecurrenceAndKeepsItsPermanentStartDate() {
        val start = LocalDate.of(2024, 2, 29)
        val event = event(start, CountMode.COUNT_UP).copy(repeatRule = RepeatRule.YEARLY)
        val value = EventCountResolver.resolve(event, LocalDate.of(2026, 3, 1))

        assertEquals(start, value.displayDate)
        assertEquals(731L, value.magnitude)
        assertEquals(CountUpState.ELAPSED, value.countUpState)
    }

    @Test
    fun countdownDelegatesToExistingOccurrenceAndCalculatorSemantics() {
        val today = LocalDate.of(2026, 9, 29)
        val event = event(LocalDate.of(2020, 10, 1), CountMode.COUNT_DOWN)
            .copy(repeatRule = RepeatRule.YEARLY)
        val value = EventCountResolver.resolve(event, today)

        assertEquals(LocalDate.of(2026, 10, 1), value.displayDate)
        assertEquals(2L, value.magnitude)
        assertEquals(CountdownStatus.TWO_DAYS, value.countdownStatus)
        assertNull(value.countUpState)
    }

    private fun event(date: LocalDate, mode: CountMode) = CountdownEvent(
        id = "event",
        title = "Event",
        date = date,
        type = EventType.EVENT,
        createdAt = Instant.EPOCH,
        countMode = mode,
    )
}
