package com.santiagorodriguez.countaway.ui

import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.ReminderOption
import com.santiagorodriguez.countaway.model.RepeatRule
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
        selectedRepeatRule: RepeatRule = RepeatRule.NONE,
    ): List<ReminderOption> = ReminderOption.entries.filter { reminder ->
        reminder == ReminderOption.OFF ||
            reminder == selectedReminder ||
            isUnchanged(existingEvent, selectedDate, reminder, selectedRepeatRule) ||
            ArrivalNotificationPolicy.isSchedulePossible(
                selectedDate,
                reminder,
                selectedRepeatRule,
                today,
            )
    }

    fun canSave(
        existingEvent: CountdownEvent?,
        selectedDate: LocalDate,
        selectedReminder: ReminderOption,
        today: LocalDate,
        selectedRepeatRule: RepeatRule = RepeatRule.NONE,
    ): Boolean =
        isUnchanged(existingEvent, selectedDate, selectedReminder, selectedRepeatRule) ||
            ArrivalNotificationPolicy.isSchedulePossible(
                selectedDate,
                selectedReminder,
                selectedRepeatRule,
                today,
            )

    fun selectionEffect(
        currentReminder: ReminderOption,
        nextReminder: ReminderOption,
        existingEvent: CountdownEvent?,
        selectedDate: LocalDate,
        today: LocalDate,
        selectedRepeatRule: RepeatRule = RepeatRule.NONE,
    ): ReminderSelectionEffect {
        if (nextReminder == currentReminder || nextReminder == ReminderOption.OFF) {
            return ReminderSelectionEffect.NONE
        }
        return effectFor(existingEvent, selectedDate, nextReminder, selectedRepeatRule, today)
    }

    fun dateChangeEffect(
        existingEvent: CountdownEvent?,
        selectedDate: LocalDate,
        selectedReminder: ReminderOption,
        today: LocalDate,
        selectedRepeatRule: RepeatRule = RepeatRule.NONE,
    ): ReminderSelectionEffect {
        if (selectedReminder == ReminderOption.OFF) return ReminderSelectionEffect.NONE
        return effectFor(existingEvent, selectedDate, selectedReminder, selectedRepeatRule, today)
    }

    fun repeatChangeEffect(
        existingEvent: CountdownEvent?,
        selectedDate: LocalDate,
        selectedReminder: ReminderOption,
        selectedRepeatRule: RepeatRule,
        today: LocalDate,
    ): ReminderSelectionEffect {
        if (selectedReminder == ReminderOption.OFF) return ReminderSelectionEffect.NONE
        return effectFor(existingEvent, selectedDate, selectedReminder, selectedRepeatRule, today)
    }

    private fun effectFor(
        existingEvent: CountdownEvent?,
        selectedDate: LocalDate,
        selectedReminder: ReminderOption,
        selectedRepeatRule: RepeatRule,
        today: LocalDate,
    ): ReminderSelectionEffect {
        if (
            !ArrivalNotificationPolicy.isSchedulePossible(
                selectedDate,
                selectedReminder,
                selectedRepeatRule,
                today,
            )
        ) {
            return if (isUnchanged(existingEvent, selectedDate, selectedReminder, selectedRepeatRule)) {
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
        selectedRepeatRule: RepeatRule,
    ): Boolean = existingEvent != null &&
        existingEvent.date == selectedDate &&
        existingEvent.reminder == selectedReminder &&
        existingEvent.repeatRule == selectedRepeatRule
}
