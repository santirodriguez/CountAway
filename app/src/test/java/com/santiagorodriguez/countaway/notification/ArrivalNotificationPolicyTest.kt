package com.santiagorodriguez.countaway.notification

import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.ReminderOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ArrivalNotificationPolicyTest {
    private val today = LocalDate.of(2026, 8, 7)

    @Test
    fun arrivalReminderIsDeliveredOnlyOnceForItsScheduledDate() {
        val event = event("one", today, ReminderOption.ON_DAY)
        assertTrue(ArrivalNotificationPolicy.isDue(event, today, null))
        assertFalse(ArrivalNotificationPolicy.isDue(event, today, today))
    }

    @Test
    fun advanceReminderUsesSelectedOffset() {
        val event = event("advance", today.plusDays(3), ReminderOption.THREE_DAYS)
        assertEquals(today, ArrivalNotificationPolicy.scheduledDate(event))
        assertTrue(ArrivalNotificationPolicy.isDue(event, today, null))
    }

    @Test
    fun disabledAndOtherDatesAreNotDue() {
        assertFalse(ArrivalNotificationPolicy.isDue(event("off", today, ReminderOption.OFF), today, null))
        assertFalse(
            ArrivalNotificationPolicy.isDue(
                event("future", today.plusDays(1), ReminderOption.ON_DAY),
                today,
                null,
            ),
        )
    }

    @Test
    fun nextPendingDateSkipsPastAndDeliveredReminders() {
        val events = listOf(
            event("past", today, ReminderOption.ONE_DAY),
            event("delivered", today, ReminderOption.ON_DAY),
            event("next", today.plusDays(3), ReminderOption.ONE_DAY),
        )
        val next = ArrivalNotificationPolicy.nextPendingDate(events, today) { event, scheduledDate ->
            event.id == "delivered" && scheduledDate == today
        }
        assertEquals(today.plusDays(2), next)
    }

    @Test
    fun noEnabledEventsMeansNoSchedule() {
        val events = listOf(event("off", today.plusDays(2), ReminderOption.OFF))
        assertNull(ArrivalNotificationPolicy.nextPendingDate(events, today) { _, _ -> false })
    }

    @Test
    fun triggerUsesNineAmOrNearNowWhenNineHasPassed() {
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        val before = ZonedDateTime.of(2026, 8, 7, 8, 0, 0, 0, zone)
        val after = ZonedDateTime.of(2026, 8, 7, 10, 0, 0, 0, zone)

        assertEquals(
            ZonedDateTime.of(2026, 8, 7, 9, 0, 0, 0, zone),
            ArrivalNotificationScheduler.triggerTime(before, today),
        )
        assertEquals(after.plusSeconds(10), ArrivalNotificationScheduler.triggerTime(after, today))
    }

    private fun event(id: String, date: LocalDate, reminder: ReminderOption): CountdownEvent = CountdownEvent(
        id = id,
        title = id,
        date = date,
        type = EventType.EVENT,
        reminder = reminder,
        createdAt = Instant.EPOCH,
    )
}
