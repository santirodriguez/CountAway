package com.santiagorodriguez.countaway.notification

import com.santiagorodriguez.countaway.model.CountdownEvent
import java.time.LocalDate

object ArrivalNotificationPolicy {
    fun isDue(event: CountdownEvent, today: LocalDate, deliveredForDate: LocalDate?): Boolean =
        event.notifyOnArrival && event.date == today && deliveredForDate != event.date

    fun nextPendingDate(
        events: List<CountdownEvent>,
        today: LocalDate,
        wasDelivered: (CountdownEvent) -> Boolean,
    ): LocalDate? = events.asSequence()
        .filter { it.notifyOnArrival }
        .filter { !it.date.isBefore(today) }
        .filterNot(wasDelivered)
        .map { it.date }
        .minOrNull()
}
