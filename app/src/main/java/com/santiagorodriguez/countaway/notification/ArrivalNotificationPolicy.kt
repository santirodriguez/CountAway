package com.santiagorodriguez.countaway.notification

import com.santiagorodriguez.countaway.countdown.CountdownOccurrenceResolver
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.ReminderOption
import com.santiagorodriguez.countaway.model.RepeatRule
import java.time.LocalDate

object ArrivalNotificationPolicy {
    fun scheduledDate(event: CountdownEvent): LocalDate? =
        scheduledDate(event.date, event.reminder)

    fun scheduledDate(event: CountdownEvent, today: LocalDate): LocalDate? =
        nextScheduledDate(event.date, event.repeatRule, event.reminder, today)

    fun scheduledDate(targetDate: LocalDate, reminder: ReminderOption): LocalDate? =
        reminder.daysBefore?.let { days -> targetDate.minusDays(days.toLong()) }

    fun isSchedulePossible(targetDate: LocalDate, reminder: ReminderOption, today: LocalDate): Boolean =
        isSchedulePossible(targetDate, reminder, RepeatRule.NONE, today)

    fun isSchedulePossible(
        targetDate: LocalDate,
        reminder: ReminderOption,
        repeatRule: RepeatRule,
        today: LocalDate,
    ): Boolean {
        if (reminder == ReminderOption.OFF) return true
        return nextScheduledDate(targetDate, repeatRule, reminder, today) != null
    }

    fun isDue(event: CountdownEvent, today: LocalDate, deliveredForDate: LocalDate?): Boolean {
        val scheduledDate = scheduledDate(event, today) ?: return false
        return scheduledDate == today && deliveredForDate != scheduledDate
    }

    fun nextPendingDate(
        events: List<CountdownEvent>,
        today: LocalDate,
        wasDelivered: (CountdownEvent, LocalDate) -> Boolean,
    ): LocalDate? = nextPendingDate(
        events = events,
        today = today,
        wasDelivered = wasDelivered,
        canAttempt = { _, _ -> true },
    )

    fun nextPendingDate(
        events: List<CountdownEvent>,
        today: LocalDate,
        wasDelivered: (CountdownEvent, LocalDate) -> Boolean,
        canAttempt: (CountdownEvent, LocalDate) -> Boolean,
    ): LocalDate? = events.asSequence()
        .mapNotNull { event -> nextPendingDate(event, today, wasDelivered, canAttempt) }
        .minOrNull()

    fun shouldResetDeliveryState(previous: CountdownEvent?, updated: CountdownEvent): Boolean =
        previous == null ||
            previous.date != updated.date ||
            previous.reminder != updated.reminder ||
            previous.repeatRule != updated.repeatRule

    private fun nextPendingDate(
        event: CountdownEvent,
        today: LocalDate,
        wasDelivered: (CountdownEvent, LocalDate) -> Boolean,
        canAttempt: (CountdownEvent, LocalDate) -> Boolean,
    ): LocalDate? {
        val first = scheduledDate(event, today) ?: return null
        if (!wasDelivered(event, first) && canAttempt(event, first)) return first
        if (event.repeatRule != RepeatRule.YEARLY) return null
        return scheduledDate(event, first.plusDays(1))
    }

    private fun nextScheduledDate(
        targetDate: LocalDate,
        repeatRule: RepeatRule,
        reminder: ReminderOption,
        today: LocalDate,
    ): LocalDate? {
        val daysBefore = reminder.daysBefore ?: return null
        val occurrenceThreshold = today.plusDays(daysBefore.toLong())
        val occurrence = CountdownOccurrenceResolver.nextOccurrenceOnOrAfter(
            targetDate,
            repeatRule,
            occurrenceThreshold,
        ) ?: return null
        return occurrence.minusDays(daysBefore.toLong())
    }
}
