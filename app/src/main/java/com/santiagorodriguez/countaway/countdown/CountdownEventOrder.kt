package com.santiagorodriguez.countaway.countdown

import com.santiagorodriguez.countaway.model.CountdownEvent
import java.time.LocalDate

object CountdownEventOrder {
    fun sortedForDisplay(events: List<CountdownEvent>, today: LocalDate): List<CountdownEvent> {
        val datedEvents = events.map { event ->
            event to CountdownOccurrenceResolver.displayDate(event, today)
        }

        val active = datedEvents
            .filter { (_, displayDate) -> !displayDate.isBefore(today) }
            .sortedWith(
                compareBy<Pair<CountdownEvent, LocalDate>> { (_, displayDate) -> displayDate }
                    .thenBy { (event, _) -> event.createdAt },
            )
            .map { (event, _) -> event }

        val past = datedEvents
            .filter { (_, displayDate) -> displayDate.isBefore(today) }
            .sortedWith(
                compareByDescending<Pair<CountdownEvent, LocalDate>> { (_, displayDate) -> displayDate }
                    .thenByDescending { (event, _) -> event.createdAt },
            )
            .map { (event, _) -> event }

        return active + past
    }
}
