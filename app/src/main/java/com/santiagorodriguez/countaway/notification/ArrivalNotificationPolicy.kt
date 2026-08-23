package com.santiagorodriguez.countaway.notification

import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.ReminderOption
import java.time.LocalDate

object ArrivalNotificationPolicy {
    fun scheduledDate(event: CountdownEvent): LocalDate? =
        scheduledDate(event.date, event.reminder)

    fun scheduledDate(targetDate: LocalDate, reminder: ReminderOption): LocalDate? =
        reminder.daysBefore?.let { days -> targetDate.minusDays(days.toLong()) }

    fun isSchedulePossible(targetDate: LocalDate, reminder: ReminderOption, today: LocalDate): Boolean {
        val scheduledDate = scheduledDate(targetDate, reminder) ?: return true
        return !scheduledDate.isBefore(today)
    }

    fun isDue(event: CountdownEvent, today: LocalDate, deliveredForDate: LocalDate?): Boolean {
        val scheduledDate = scheduledDate(event) ?: return false
        return scheduledDate == today && deliveredForDate != scheduledDate
    }

    fun nextPendingDate(
        events: List<CountdownEvent>,
        today: LocalDate,
        wasDelivered: (CountdownEvent, LocalDate) -> Boolean,
    ): LocalDate? = events.asSequence()
        .mapNotNull { event -> scheduledDate(event)?.let { date -> event to date } }
        .filter { (_, date) -> !date.isBefore(today) }
        .filterNot { (event, date) -> wasDelivered(event, date) }
        .map { (_, date) -> date }
        .minOrNull()

    fun shouldResetDeliveryState(previous: CountdownEvent?, updated: CountdownEvent): Boolean =
        previous == null || previous.date != updated.date || previous.reminder != updated.reminder
}
