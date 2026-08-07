package com.santiagorodriguez.countaway.notification

import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
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
    fun dueEventIsDeliveredOnlyOnceForItsDate() {
        val event = event("one", today, notify = true)
        assertTrue(ArrivalNotificationPolicy.isDue(event, today, null))
        assertFalse(ArrivalNotificationPolicy.isDue(event, today, today))
    }

    @Test
    fun optOutAndOtherDatesAreNotDue() {
        assertFalse(ArrivalNotificationPolicy.isDue(event("off", today, notify = false), today, null))
        assertFalse(ArrivalNotificationPolicy.isDue(event("future", today.plusDays(1), notify = true), today, null))
    }

    @Test
    fun nextPendingDateSkipsPastAndDeliveredEvents() {
        val events = listOf(
            event("past", today.minusDays(1), notify = true),
            event("delivered", today, notify = true),
            event("next", today.plusDays(2), notify = true),
        )
        val next = ArrivalNotificationPolicy.nextPendingDate(events, today) { it.id == "delivered" }
        assertEquals(today.plusDays(2), next)
    }

    @Test
    fun noEnabledEventsMeansNoSchedule() {
        val events = listOf(event("off", today.plusDays(2), notify = false))
        assertNull(ArrivalNotificationPolicy.nextPendingDate(events, today) { false })
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

    private fun event(id: String, date: LocalDate, notify: Boolean): CountdownEvent = CountdownEvent(
        id = id,
        title = id,
        date = date,
        type = EventType.EVENT,
        notifyOnArrival = notify,
        createdAt = Instant.EPOCH,
    )
}
