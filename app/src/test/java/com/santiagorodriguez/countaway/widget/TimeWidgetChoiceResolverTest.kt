package com.santiagorodriguez.countaway.widget

import com.santiagorodriguez.countaway.countdown.CountdownTimeSnapshot
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

class TimeWidgetChoiceResolverTest {
    private val zone = ZoneId.of("America/New_York")
    private val day = LocalDate.of(2026, 10, 18)
    private fun at(hour: Int, minute: Int, second: Int = 0) =
        CountdownTimeSnapshot(ZonedDateTime.of(2026, 10, 18, hour, minute, second, 0, zone))
    private fun event(id: String, date: LocalDate = day, time: LocalTime? = LocalTime.NOON,
                      repeat: RepeatRule = RepeatRule.NONE, mode: CountMode = CountMode.COUNT_DOWN,
                      eventZone: ZoneId? = zone) = CountdownEvent(
        id = id, title = id, date = date, type = EventType.EVENT,
        createdAt = Instant.EPOCH, eventTime = time, eventZone = eventZone,
        repeatRule = repeat, countMode = mode,
    )

    @Test fun sameDayFutureHoursSortByInstantAndElapsedCountsGoLast() {
        val snapshot = at(11, 0)
        val choices = TimeWidgetChoiceResolver.choices(listOf(
            event("late", time = LocalTime.of(18, 0)),
            event("elapsed", time = LocalTime.of(9, 30)),
            event("early", time = LocalTime.of(12, 5)),
        ), snapshot)
        assertEquals(listOf("early", "late", "elapsed"), choices.map { it.event.id })
        assertEquals(listOf(12, 18, 9), choices.map { it.target.hour })
    }

    @Test fun recurringRowsShowResolvedNextOccurrenceNotOriginalAnchor() {
        val snapshot = at(17, 0)
        val recurring = event("weekly", date = LocalDate.of(2026, 10, 11),
            time = LocalTime.of(16, 30), repeat = RepeatRule.WEEKLY)
        val choice = TimeWidgetChoiceResolver.choices(listOf(recurring), snapshot).single()
        assertEquals(LocalDate.of(2026, 10, 25), choice.target.toLocalDate())
        assertEquals(zone, choice.target.zone)
        assertEquals(choice.target.toInstant().plusSeconds(1),
            TimeWidgetChoiceResolver.nextRecurringBoundary(listOf(choice), snapshot))
        // An old encoded reference must still follow the same event identity.
        assertEquals(recurring.id, TimeWidgetChoiceResolver.selectedId(listOf(choice), recurring.id))
    }

    @Test fun crossingTheSelectedOccurrenceAdvancesAndReschedulesOnce() {
        val recurring = event("weekly", date = LocalDate.of(2026, 10, 11),
            time = LocalTime.of(16, 30), repeat = RepeatRule.WEEKLY)
        val before = at(16, 29)
        val next = TimeWidgetChoiceResolver.choices(listOf(recurring), before).single()
        val boundary = next.target.toInstant().plusSeconds(1)
        val after = at(16, 30, 1)
        assertEquals(boundary, after.now.toInstant())
        val shifted = TimeWidgetChoiceResolver.choices(listOf(recurring), after).single()
        assertEquals(LocalDate.of(2026, 10, 25), shifted.target.toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 18), next.target.toLocalDate())
    }

    @Test fun timeLessOrZoneLessEventsNeverBecomeSelectable() {
        val good = event("timed")
        val choices = TimeWidgetChoiceResolver.choices(listOf(
            good, event("date", time = null), event("no-zone", eventZone = null),
        ), at(10, 0))
        assertEquals(listOf("timed"), choices.map { it.event.id })
        assertNull(TimeWidgetChoiceResolver.selectedId(choices, "date"))
        assertNull(TimeWidgetChoiceResolver.selectedId(emptyList(), "timed"))
    }

    @Test fun monthlyAndYearlyRowsUseDomainClampingAndTravelKeepsTargetInstant() {
        val february = CountdownTimeSnapshot(ZonedDateTime.of(2027, 2, 1, 9, 0, 0, 0, zone))
        val monthly = event("monthly", date = LocalDate.of(2027, 1, 31),
            repeat = RepeatRule.MONTHLY)
        val leap = event("leap", date = LocalDate.of(2024, 2, 29),
            repeat = RepeatRule.YEARLY)
        val rows = TimeWidgetChoiceResolver.choices(listOf(leap, monthly), february)
            .associateBy { it.event.id }
        assertEquals(LocalDate.of(2027, 2, 28), rows.getValue("monthly").target.toLocalDate())
        assertEquals(LocalDate.of(2027, 2, 28), rows.getValue("leap").target.toLocalDate())
        val paris = CountdownTimeSnapshot(february.now.withZoneSameInstant(ZoneId.of("Europe/Paris")))
        assertEquals(TimeWidgetChoiceResolver.choices(listOf(monthly), february).single().target.toInstant(),
            TimeWidgetChoiceResolver.choices(listOf(monthly), paris).single().target.toInstant())
    }

    @Test fun countUpRowsRemainEligibleButDoNotAcquireRecurringRefreshes() {
        val up = event("up", mode = CountMode.COUNT_UP)
        val rows = TimeWidgetChoiceResolver.choices(listOf(up), at(10, 0))
        assertEquals("up", rows.single().event.id)
        assertNull(TimeWidgetChoiceResolver.nextRecurringBoundary(rows, at(10, 0)))
    }
}
