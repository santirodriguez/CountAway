package com.santiagorodriguez.countaway.countdown

import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import java.time.LocalDate
import java.time.Instant
import java.time.ZonedDateTime

object CountdownEventOrder {
    fun sortedForDisplay(
        events: List<CountdownEvent>,
        today: LocalDate,
        now: ZonedDateTime? = null,
    ): List<CountdownEvent> {
        if (now != null && events.any { it.eventTime != null }) {
            return sortedIncludingTimes(events, today, now)
        }
        val countdowns = events
            .asSequence()
            .filter { event -> event.countMode == CountMode.COUNT_DOWN }
            .map { event -> event to CountdownOccurrenceResolver.displayDate(event, today) }
            .toList()

        val activeCountdowns = countdowns
            .filter { (_, displayDate) -> !displayDate.isBefore(today) }
            .sortedWith(
                compareBy<Pair<CountdownEvent, LocalDate>> { (_, displayDate) -> displayDate }
                    .thenBy { (event, _) -> event.createdAt },
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
                    .thenByDescending { (event, _) -> event.createdAt },
            )
            .map { (event, _) -> event }

        return activeCountdowns + countUps + pastCountdowns
    }
    private data class CountdownSlot(
        val event: CountdownEvent,
        val occurrenceDate: LocalDate,
        val occurrenceInstant: Instant,
        val active: Boolean,
    )

    /** Time-aware order is used only when some events explicitly opt into HH:mm. */
    private fun sortedIncludingTimes(
        events: List<CountdownEvent>,
        today: LocalDate,
        now: ZonedDateTime,
    ): List<CountdownEvent> {
        val countdowns = events.asSequence()
            .filter { it.countMode == CountMode.COUNT_DOWN }
            .map { event ->
                val timed = TimedEventResolver.resolve(event, now)
                val date = timed?.occurrenceDate
                    ?: CountdownOccurrenceResolver.displayDate(event, today)
                CountdownSlot(
                    event = event,
                    occurrenceDate = date,
                    occurrenceInstant = timed?.target?.toInstant()
                        ?: date.atStartOfDay(now.zone).toInstant(),
                    active = timed?.direction?.let { it != TimedCountDirection.ELAPSED }
                        ?: !date.isBefore(today),
                )
            }
            .toList()

        val active = countdowns.filter { it.active }.sortedWith(
            compareBy<CountdownSlot> { it.occurrenceInstant }
                .thenBy { it.event.createdAt },
        ).map { it.event }

        val countUps = events
            .filter { it.countMode == CountMode.COUNT_UP }
            .sortedWith(
                compareByDescending<CountdownEvent> { it.date }
                    .thenByDescending { it.createdAt }
                    .thenBy { it.id },
            )

        val past = countdowns.filterNot { it.active }.sortedWith(
            compareByDescending<CountdownSlot> { it.occurrenceInstant }
                .thenByDescending { it.event.createdAt },
        ).map { it.event }

        return active + countUps + past
    }

}
