package com.santiagorodriguez.countaway.ui

import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.ReminderOption
import com.santiagorodriguez.countaway.notification.ArrivalNotificationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ReminderEditorPolicyTest {
    private val today = LocalDate.of(2026, 8, 23)

    @Test
    fun unchangedHistoricalReminderCanStillBeSaved() {
        val existing = event(today.plusDays(1), ReminderOption.THREE_DAYS)

        assertFalse(
            ArrivalNotificationPolicy.isSchedulePossible(existing.date, existing.reminder, today),
        )
        assertTrue(
            ReminderEditorPolicy.canSave(
                existingEvent = existing,
                selectedDate = existing.date,
                selectedReminder = existing.reminder,
                today = today,
            ),
        )
    }

    @Test
    fun modifiedImpossibleReminderScheduleIsRejected() {
        val existing = event(today.plusDays(10), ReminderOption.SEVEN_DAYS)

        assertFalse(
            ReminderEditorPolicy.canSave(
                existingEvent = existing,
                selectedDate = today.plusDays(1),
                selectedReminder = ReminderOption.SEVEN_DAYS,
                today = today,
            ),
        )
    }

    @Test
    fun newEventOnlyOffersReminderDatesThatHaveNotPassed() {
        assertEquals(
            listOf(
                ReminderOption.OFF,
                ReminderOption.ON_DAY,
                ReminderOption.ONE_DAY,
            ),
            ReminderEditorPolicy.availableOptions(
                existingEvent = null,
                selectedDate = today.plusDays(2),
                selectedReminder = ReminderOption.OFF,
                today = today,
            ),
        )
    }

    @Test
    fun allReminderChoicesRemainAvailableWhenDateIsFarEnoughAway() {
        assertEquals(
            ReminderOption.entries.toList(),
            ReminderEditorPolicy.availableOptions(
                existingEvent = null,
                selectedDate = today.plusDays(7),
                selectedReminder = ReminderOption.OFF,
                today = today,
            ),
        )
    }

    @Test
    fun currentImpossibleSelectionRemainsVisibleUntilUserChangesIt() {
        assertEquals(
            listOf(
                ReminderOption.OFF,
                ReminderOption.ON_DAY,
                ReminderOption.ONE_DAY,
                ReminderOption.SEVEN_DAYS,
            ),
            ReminderEditorPolicy.availableOptions(
                existingEvent = null,
                selectedDate = today.plusDays(2),
                selectedReminder = ReminderOption.SEVEN_DAYS,
                today = today,
            ),
        )
    }

    @Test
    fun impossibleSelectionStopsBeforeNotificationSetup() {
        val effect = ReminderEditorPolicy.selectionEffect(
            currentReminder = ReminderOption.OFF,
            nextReminder = ReminderOption.SEVEN_DAYS,
            existingEvent = null,
            selectedDate = today.plusDays(1),
            today = today,
        )

        assertEquals(ReminderSelectionEffect.SHOW_SCHEDULE_UNAVAILABLE, effect)
    }

    @Test
    fun initializationSelectionCallbackHasNoSideEffects() {
        val existing = event(today.plusDays(7), ReminderOption.SEVEN_DAYS)

        val effect = ReminderEditorPolicy.selectionEffect(
            currentReminder = existing.reminder,
            nextReminder = existing.reminder,
            existingEvent = existing,
            selectedDate = existing.date,
            today = today,
        )

        assertEquals(ReminderSelectionEffect.NONE, effect)
    }

    @Test
    fun validChangedSelectionChecksNotificationAvailability() {
        val effect = ReminderEditorPolicy.selectionEffect(
            currentReminder = ReminderOption.OFF,
            nextReminder = ReminderOption.THREE_DAYS,
            existingEvent = null,
            selectedDate = today.plusDays(3),
            today = today,
        )

        assertEquals(ReminderSelectionEffect.CHECK_NOTIFICATIONS, effect)
    }

    private fun event(date: LocalDate, reminder: ReminderOption): CountdownEvent = CountdownEvent(
        id = "event",
        title = "Event",
        date = date,
        type = EventType.EVENT,
        reminder = reminder,
        createdAt = Instant.EPOCH,
    )
}
