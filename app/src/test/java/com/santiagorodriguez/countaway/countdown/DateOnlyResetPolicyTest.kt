package com.santiagorodriguez.countaway.countdown

import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventIcon
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.ReminderOption
import com.santiagorodriguez.countaway.model.RepeatRule
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DateOnlyResetPolicyTest {
    private val today = LocalDate.of(2026, 11, 7)
    private val base = CountdownEvent(
        id = "event-1", title = "Replace water filter", date = today.minusDays(90),
        type = EventType.CUSTOM, icon = EventIcon.BOOK,
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        countMode = CountMode.COUNT_UP,
    )

    @Test fun pastAndFutureDateOnlyEventsResetWithoutChangingAnyOtherField() {
        for (date in listOf(today.minusDays(90), today.plusDays(30))) {
            val source = base.copy(date = date)
            val reset = DateOnlyResetPolicy.reset(source, today)
            assertEquals(source.copy(date = today), reset)
            assertEquals(date, source.date)
            assertEquals(source.id, reset?.id)
            assertEquals(source.createdAt, reset?.createdAt)
            assertNull(reset?.eventTime)
            assertNull(reset?.eventZone)
        }
    }

    @Test fun alreadyTodayAndCountdownAreIneligible() {
        assertFalse(DateOnlyResetPolicy.canReset(base.copy(date = today), today))
        assertNull(DateOnlyResetPolicy.reset(base.copy(date = today), today))
        assertNull(DateOnlyResetPolicy.reset(base.copy(countMode = CountMode.COUNT_DOWN), today))
    }

    @Test fun timedEventsAndInvalidRecurringOrRemindingVariantsAreIneligible() {
        assertNull(DateOnlyResetPolicy.reset(base.copy(
            eventTime = LocalTime.of(12, 30), eventZone = ZoneId.of("Europe/Madrid")), today))
        assertNull(DateOnlyResetPolicy.reset(base.copy(eventZone = ZoneId.of("UTC")), today))
        assertNull(DateOnlyResetPolicy.reset(base.copy(repeatRule = RepeatRule.MONTHLY), today))
        assertNull(DateOnlyResetPolicy.reset(base.copy(reminder = ReminderOption.ONE_DAY), today))
    }

    @Test fun dateBoundaryUsesTheCurrentProvidedDate() {
        val source = base.copy(date = LocalDate.of(2026, 11, 8))
        assertEquals(LocalDate.of(2026, 11, 7), DateOnlyResetPolicy.reset(source, today)?.date)
        assertNull(DateOnlyResetPolicy.reset(source, today.plusDays(1)))
    }
}
