package com.santiagorodriguez.countaway.notification

import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.ReminderOption
import com.santiagorodriguez.countaway.model.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ArrivalRecurringNotificationPolicyTest {
    private val today = LocalDate.of(2026, 9, 16)

    @Test
    fun yearlyReminderUsesTheNextOccurrence() {
        val event = event(LocalDate.of(2020, 9, 19), ReminderOption.THREE_DAYS)

        assertEquals(today, ArrivalNotificationPolicy.scheduledDate(event, today))
        assertTrue(ArrivalNotificationPolicy.isDue(event, today, null))
    }

    @Test
    fun missedYearlyReminderSkipsThisOccurrenceAndSchedulesNextYear() {
        val event = event(LocalDate.of(2020, 9, 18), ReminderOption.THREE_DAYS)

        assertEquals(
            LocalDate.of(2027, 9, 15),
            ArrivalNotificationPolicy.scheduledDate(event, today),
        )
        assertFalse(ArrivalNotificationPolicy.isDue(event, today, null))
    }

    @Test
    fun deliveredYearlyReminderRollsForwardInsteadOfDisappearing() {
        val event = event(LocalDate.of(2020, 9, 16), ReminderOption.ON_DAY)

        assertEquals(
            LocalDate.of(2027, 9, 16),
            ArrivalNotificationPolicy.nextPendingDate(listOf(event), today) { _, scheduledDate ->
                scheduledDate == today
            },
        )
    }

    @Test
    fun exhaustedYearlyDeliveryAdvancesToTheNextOccurrence() {
        val event = event(LocalDate.of(2020, 9, 16), ReminderOption.ON_DAY)

        assertEquals(
            LocalDate.of(2027, 9, 16),
            ArrivalNotificationPolicy.nextPendingDate(
                events = listOf(event),
                today = today,
                wasDelivered = { _, _ -> false },
                canAttempt = { _, scheduledDate -> scheduledDate != today },
            ),
        )
    }

    @Test
    fun leapDayReminderUsesFebruary28InNonLeapYear2100() {
        val event = event(LocalDate.of(2024, 2, 29), ReminderOption.SEVEN_DAYS)

        assertEquals(
            LocalDate.of(2100, 2, 21),
            ArrivalNotificationPolicy.scheduledDate(event, LocalDate.of(2100, 2, 20)),
        )
    }

    @Test
    fun changingRepeatRuleResetsDeliveryState() {
        val previous = event(LocalDate.of(2026, 9, 16), ReminderOption.ON_DAY)
        val updated = previous.copy(repeatRule = RepeatRule.NONE)

        assertTrue(ArrivalNotificationPolicy.shouldResetDeliveryState(previous, updated))
    }

    private fun event(date: LocalDate, reminder: ReminderOption): CountdownEvent = CountdownEvent(
        id = "event",
        title = "Event",
        date = date,
        type = EventType.EVENT,
        reminder = reminder,
        createdAt = Instant.EPOCH,
        repeatRule = RepeatRule.YEARLY,
    )
}
