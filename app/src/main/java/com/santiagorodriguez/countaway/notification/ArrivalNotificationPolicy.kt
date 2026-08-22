package com.santiagorodriguez.countaway.notification

import com.santiagorodriguez.countaway.model.CountdownEvent
import java.time.LocalDate

object ArrivalNotificationPolicy {
    fun scheduledDate(event: CountdownEvent): LocalDate? =
        event.reminder.daysBefore?.let { days -> event.date.minusDays(days.toLong()) }

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
