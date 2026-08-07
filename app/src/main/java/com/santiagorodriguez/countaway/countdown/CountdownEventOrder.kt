package com.santiagorodriguez.countaway.countdown

import com.santiagorodriguez.countaway.model.CountdownEvent
import java.time.LocalDate

object CountdownEventOrder {
    fun sortedForDisplay(events: List<CountdownEvent>, today: LocalDate): List<CountdownEvent> {
        val active = events
            .filter { !it.date.isBefore(today) }
            .sortedWith(compareBy<CountdownEvent> { it.date }.thenBy { it.createdAt })

        val past = events
            .filter { it.date.isBefore(today) }
            .sortedWith(compareByDescending<CountdownEvent> { it.date }.thenByDescending { it.createdAt })

        return active + past
    }
}
