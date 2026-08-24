package com.santiagorodriguez.countaway.widget

import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownStatus
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventIcon
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.ReminderOption
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetEventContentTest {
    private val today = LocalDate.of(2026, 8, 23)

    @Test
    fun eventContentUsesSavedTitleIconAndCountdown() {
        val event = event(
            id = "custom",
            title = "Visa renewal",
            date = today.plusDays(12),
            type = EventType.CUSTOM,
            icon = EventIcon.FLAG,
        )

        val content = WidgetEventContentFactory.from(event, today)

        assertEquals(R.drawable.ic_event_flag, content.iconRes)
        assertEquals("Visa renewal", content.title)
        assertEquals("12", content.countText)
        assertEquals(R.string.widget_days_left, content.unitRes)
        assertEquals(CountdownStatus.FUTURE, content.status)
    }

    @Test
    fun eventContentMatchesTomorrowTodayAndDoneStates() {
        val tomorrow = WidgetEventContentFactory.from(event("tomorrow", "Tomorrow", today.plusDays(1)), today)
        assertEquals("1", tomorrow.countText)
        assertEquals(R.string.status_tomorrow, tomorrow.unitRes)
        assertEquals(CountdownStatus.TOMORROW, tomorrow.status)

        val sameDay = WidgetEventContentFactory.from(event("today", "Today", today), today)
        assertEquals("0", sameDay.countText)
        assertEquals(R.string.status_today, sameDay.unitRes)
        assertEquals(CountdownStatus.TODAY, sameDay.status)

        val done = WidgetEventContentFactory.from(event("done", "Done", today.minusDays(1)), today)
        assertEquals("✓", done.countText)
        assertNull(done.unitRes)
        assertEquals(CountdownStatus.DONE, done.status)
    }

    @Test
    fun fixedSelectionResolvesTheSavedEvent() {
        val first = event("first", "First", today.plusDays(5))
        val second = event("second", "Second", today.plusDays(8))

        assertEquals(
            second,
            WidgetEventResolver.resolve(
                selection = WidgetEventSelection.FIXED,
                eventId = "second",
                events = listOf(first, second),
                today = today,
            ),
        )
        assertNull(
            WidgetEventResolver.resolve(
                selection = WidgetEventSelection.FIXED,
                eventId = "missing",
                events = listOf(first, second),
                today = today,
            ),
        )
    }

    @Test
    fun nextSelectionSkipsPastEventsAndUsesNearestUpcomingEvent() {
        val past = event("past", "Past", today.minusDays(1))
        val later = event("later", "Later", today.plusDays(10))
        val nearest = event("nearest", "Nearest", today.plusDays(2))

        assertEquals(
            nearest,
            WidgetEventResolver.resolve(
                selection = WidgetEventSelection.NEXT,
                eventId = null,
                events = listOf(later, past, nearest),
                today = today,
            ),
        )
        assertNull(
            WidgetEventResolver.resolve(
                selection = WidgetEventSelection.NEXT,
                eventId = null,
                events = listOf(past),
                today = today,
            ),
        )
    }

    private fun event(
        id: String,
        title: String,
        date: LocalDate,
        type: EventType = EventType.EVENT,
        icon: EventIcon = EventIcon.CALENDAR,
    ) = CountdownEvent(
        id = id,
        title = title,
        date = date,
        type = type,
        icon = icon,
        reminder = ReminderOption.OFF,
        createdAt = Instant.EPOCH,
    )
}
