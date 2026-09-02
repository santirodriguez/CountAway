package com.santiagorodriguez.countaway.ui

import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.ReminderOption
import com.santiagorodriguez.countaway.notification.ArrivalNotificationPolicy
import java.time.LocalDate

internal enum class ReminderSelectionEffect {
    NONE,
    SHOW_SCHEDULE_UNAVAILABLE,
    CHECK_NOTIFICATIONS,
}

internal object ReminderEditorPolicy {
    fun availableOptions(
        existingEvent: CountdownEvent?,
        selectedDate: LocalDate,
        selectedReminder: ReminderOption,
        today: LocalDate,
    ): List<ReminderOption> = ReminderOption.entries.filter { reminder ->
        reminder == ReminderOption.OFF ||
            reminder == selectedReminder ||
            isUnchanged(existingEvent, selectedDate, reminder) ||
            ArrivalNotificationPolicy.isSchedulePossible(selectedDate, reminder, today)
    }

    fun canSave(
        existingEvent: CountdownEvent?,
        selectedDate: LocalDate,
        selectedReminder: ReminderOption,
        today: LocalDate,
    ): Boolean =
        isUnchanged(existingEvent, selectedDate, selectedReminder) ||
            ArrivalNotificationPolicy.isSchedulePossible(selectedDate, selectedReminder, today)

    fun selectionEffect(
        currentReminder: ReminderOption,
        nextReminder: ReminderOption,
        existingEvent: CountdownEvent?,
        selectedDate: LocalDate,
        today: LocalDate,
    ): ReminderSelectionEffect {
        if (nextReminder == currentReminder || nextReminder == ReminderOption.OFF) {
            return ReminderSelectionEffect.NONE
        }
        return effectFor(existingEvent, selectedDate, nextReminder, today)
    }

    fun dateChangeEffect(
        existingEvent: CountdownEvent?,
        selectedDate: LocalDate,
        selectedReminder: ReminderOption,
        today: LocalDate,
    ): ReminderSelectionEffect {
        if (selectedReminder == ReminderOption.OFF) return ReminderSelectionEffect.NONE
        return effectFor(existingEvent, selectedDate, selectedReminder, today)
    }

    private fun effectFor(
        existingEvent: CountdownEvent?,
        selectedDate: LocalDate,
        selectedReminder: ReminderOption,
        today: LocalDate,
    ): ReminderSelectionEffect {
        if (!ArrivalNotificationPolicy.isSchedulePossible(selectedDate, selectedReminder, today)) {
            return if (isUnchanged(existingEvent, selectedDate, selectedReminder)) {
                ReminderSelectionEffect.NONE
            } else {
                ReminderSelectionEffect.SHOW_SCHEDULE_UNAVAILABLE
            }
        }
        return ReminderSelectionEffect.CHECK_NOTIFICATIONS
    }

    private fun isUnchanged(
        existingEvent: CountdownEvent?,
        selectedDate: LocalDate,
        selectedReminder: ReminderOption,
    ): Boolean = existingEvent != null &&
        existingEvent.date == selectedDate &&
        existingEvent.reminder == selectedReminder
}
