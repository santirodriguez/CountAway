package com.santiagorodriguez.countaway.notification

import com.santiagorodriguez.countaway.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ArrivalTimedReminderTest {
    private val ny = ZoneId.of("America/New_York")
    private val ba = ZoneId.of("America/Argentina/Buenos_Aires")

    @Test fun civilOffsetsSurviveDst() {
        val event = event(LocalDate.of(2026, 3, 8), ReminderOption.ONE_DAY)
        val now = ZonedDateTime.of(2026, 3, 6, 12, 0, 0, 0, ba)
        val date = ArrivalNotificationPolicy.scheduledDate(event, now)!!
        assertEquals(LocalDate.of(2026, 3, 7), date)
        val reminder = ArrivalReminderTiming.eventTriggerTime(now, date, event.eventTime!!, ny)
        assertEquals(LocalTime.of(18, 30), reminder.toLocalTime())
        assertEquals(23L, Duration.between(reminder, event.date.atTime(event.eventTime).atZone(ny)).toHours())
    }

    @Test fun eventZoneOwnsCivilDayAndMidnight() {
        val now = ZonedDateTime.of(2026, 8, 7, 20, 30, 0, 0, ba)
        val event = event(LocalDate.of(2026, 8, 8), ReminderOption.ON_DAY)
            .copy(eventZone = ZoneId.of("Asia/Tokyo"))
        assertEquals(LocalDate.of(2026, 8, 8), ArrivalNotificationPolicy.localToday(event, now))
        assertEquals(event.date, ArrivalNotificationPolicy.scheduledDate(event, now))
        assertNull(ArrivalNotificationPolicy.scheduledDate(
            event.copy(date = LocalDate.of(2026, 8, 7)), now))
    }

    @Test fun dstGapAndOverlapFollowJavaTimeChoice() {
        val now = ZonedDateTime.of(2026, 3, 7, 11, 0, 0, 0, ny)
        assertEquals(LocalTime.of(3, 30), ArrivalReminderTiming.eventTriggerTime(now,
            LocalDate.of(2026, 3, 8), LocalTime.of(2, 30), ny).toLocalTime())
        assertEquals(ZoneOffset.ofHours(-4), ArrivalReminderTiming.eventTriggerTime(now,
            LocalDate.of(2026, 11, 1), LocalTime.of(1, 30), ny).offset)
    }

    @Test fun timeAndZoneChangesResetDeliveryWithoutResettingTitleChanges() {
        val e = event(LocalDate.of(2026, 8, 8), ReminderOption.ON_DAY)
        assertTrue(ArrivalNotificationPolicy.shouldResetDeliveryState(e,
            e.copy(eventTime = LocalTime.of(19, 30))))
        assertTrue(ArrivalNotificationPolicy.shouldResetDeliveryState(e,
            e.copy(eventZone = ba)))
        assertFalse(ArrivalNotificationPolicy.shouldResetDeliveryState(e, e.copy(title = "Changed")))
        assertNull(ArrivalNotificationPolicy.scheduledDate(e.copy(countMode = CountMode.COUNT_UP),
            ZonedDateTime.now()))
    }

    private fun event(date: LocalDate, reminder: ReminderOption) = CountdownEvent(
        id = "timed", title = "Timed", date = date, type = EventType.EVENT,
        reminder = reminder, createdAt = Instant.EPOCH,
        eventTime = LocalTime.of(18, 30), eventZone = ny)
}
