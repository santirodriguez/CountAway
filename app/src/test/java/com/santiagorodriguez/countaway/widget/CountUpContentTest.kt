package com.santiagorodriguez.countaway.widget

import com.santiagorodriguez.countaway.countdown.CountdownCalculator
import com.santiagorodriguez.countaway.countdown.CountdownOccurrenceResolver
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.countdown.CountUpState
import com.santiagorodriguez.countaway.countdown.EventCountResolver
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.RepeatRule
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CountUpContentTest {
    private val today = LocalDate.of(2026, 9, 29)

    @Test fun countUpMarkersDoNotUseCountdownArrivalStates() {
        for (elapsed in listOf(-1L, 0L, 1L, 12345L)) {
            val content = WidgetEventContentFactory.from(event(today.minusDays(elapsed)), today)
            val expected = (if (elapsed < 0) "−" else "+") + kotlin.math.abs(elapsed)
            assertNull(content.status)
            assertEquals(if (elapsed < 0) CountUpState.STARTS_IN else CountUpState.ELAPSED, content.countUpState)
            assertEquals(expected, content.countTextFor(WidgetSize.COMPACT))
            assertEquals(expected, content.countTextFor(WidgetSize.SHORT))
            for (size in WidgetSize.entries) {
                val presentation = WidgetPresentationResolver.resolve(content, size, 2f)
                assertEquals(expected, presentation.countText)
                assertNull(presentation.milestone)
                assertFalse(presentation.showMilestone)
            }
        }
    }

    @Test fun nextExcludesCountUpsButFixedSelectionKeepsThem() {
        val up = event(today.plusDays(1))
        val recurring = up.copy(id = "down", countMode = CountMode.COUNT_DOWN,
            date = today.minusWeeks(1), repeatRule = RepeatRule.WEEKLY)
        assertNull(WidgetEventResolver.resolveNext(listOf(up), today))
        assertEquals(recurring, WidgetEventResolver.resolveNext(listOf(up, recurring), today))
        assertEquals(up, WidgetEventResolver.resolve(WidgetEventSelection.FIXED, up.id, listOf(up), today))
    }

    @Test fun everyCountdownStatusAndRecurrenceKeepTheirExistingMeaning() {
        for (rule in RepeatRule.entries) for (offset in -5L..5L) {
            val down = event(today.plusDays(offset)).copy(countMode = CountMode.COUNT_DOWN, repeatRule = rule)
            val display = CountdownOccurrenceResolver.displayDate(down, today)
            val previous = CountdownCalculator.value(today, display)
            val current = EventCountResolver.resolve(down, today)
            assertEquals(display, current.displayDate)
            assertEquals(previous.status, current.countdownStatus)
            assertEquals(kotlin.math.abs(previous.days), current.magnitude)
            assertNull(current.countUpState)
        }
    }

    @Test fun elapsedDaysFollowTheSnapshotLocalDateAcrossZonesAndDst() {
        val cases = listOf(
            Triple("2026-03-09T04:30:00Z", "America/New_York", "2026-03-08"),
            Triple("2026-11-02T05:30:00Z", "America/New_York", "2026-11-01"),
            Triple("2026-01-01T00:30:00Z", "Pacific/Kiritimati", "2025-12-31"),
            Triple("2026-01-01T00:30:00Z", "America/Argentina/Buenos_Aires", "2025-12-30"),
        )
        for ((instant, zone, start) in cases) {
            val snapshot = CountdownTime.snapshot(Clock.fixed(Instant.parse(instant), ZoneId.of(zone)))
            val value = EventCountResolver.resolve(event(LocalDate.parse(start)), snapshot.today)
            assertEquals(1L, value.magnitude)
            assertEquals(CountUpState.ELAPSED, value.countUpState)
        }
        val start = LocalDate.of(2026, 1, 1)
        val instant = Instant.parse("2026-01-01T00:30:00Z")
        val west = CountdownTime.snapshot(Clock.fixed(instant, ZoneId.of("America/New_York")))
        val east = CountdownTime.snapshot(Clock.fixed(instant, ZoneId.of("Pacific/Kiritimati")))
        assertEquals(CountUpState.STARTS_IN, EventCountResolver.resolve(event(start), west.today).countUpState)
        assertEquals(0L, EventCountResolver.resolve(event(start), east.today).magnitude)
    }

    private fun event(date: LocalDate) = CountdownEvent("up", "Event", date, EventType.EVENT,
        createdAt = Instant.EPOCH, countMode = CountMode.COUNT_UP)
}
