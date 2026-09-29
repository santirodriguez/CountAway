package com.santiagorodriguez.countaway.countdown

import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import java.time.LocalDate

object CountdownEventOrder {
    fun sortedForDisplay(events: List<CountdownEvent>, today: LocalDate): List<CountdownEvent> {
        val countdowns = events
            .asSequence()
            .filter { event -> event.countMode == CountMode.COUNT_DOWN }
            .map { event -> event to CountdownOccurrenceResolver.displayDate(event, today) }
            .toList()

        val activeCountdowns = countdowns
            .filter { (_, displayDate) -> !displayDate.isBefore(today) }
            .sortedWith(
                compareBy<Pair<CountdownEvent, LocalDate>> { (_, displayDate) -> displayDate }
                    .thenBy { (event, _) -> event.createdAt }
                    .thenBy { (event, _) -> event.id },
            )
            .map { (event, _) -> event }

        val countUps = events
            .filter { event -> event.countMode == CountMode.COUNT_UP }
            .sortedWith(
                compareByDescending<CountdownEvent> { event -> event.date }
                    .thenByDescending { event -> event.createdAt }
                    .thenBy { event -> event.id },
            )

        val pastCountdowns = countdowns
            .filter { (_, displayDate) -> displayDate.isBefore(today) }
            .sortedWith(
                compareByDescending<Pair<CountdownEvent, LocalDate>> { (_, displayDate) -> displayDate }
                    .thenByDescending { (event, _) -> event.createdAt }
                    .thenBy { (event, _) -> event.id },
            )
            .map { (event, _) -> event }

        return activeCountdowns + countUps + pastCountdowns
    }
}
