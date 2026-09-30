package com.santiagorodriguez.countaway.countdown

import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.RepeatRule
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class CountUpState {
    ELAPSED,
    STARTS_IN,
}

data class EventCountValue(
    val mode: CountMode,
    val displayDate: LocalDate,
    val magnitude: Long,
    val countdownStatus: CountdownStatus? = null,
    val countUpState: CountUpState? = null,
)

object EventCountResolver {
    fun resolve(event: CountdownEvent, today: LocalDate): EventCountValue =
        resolve(event.date, event.repeatRule, event.countMode, today)

    fun resolve(date: LocalDate, repeatRule: RepeatRule, mode: CountMode, today: LocalDate): EventCountValue =
        when (mode) {
            CountMode.COUNT_DOWN -> {
                val displayDate = CountdownOccurrenceResolver.displayDate(date, repeatRule, today)
                val countdown = CountdownCalculator.value(today, displayDate)
                EventCountValue(
                    mode = CountMode.COUNT_DOWN,
                    displayDate = displayDate,
                    magnitude = if (countdown.status == CountdownStatus.DONE) {
                        countdown.elapsedDays
                    } else {
                        countdown.days
                    },
                    countdownStatus = countdown.status,
                )
            }
            CountMode.COUNT_UP -> {
                val elapsed = ChronoUnit.DAYS.between(date, today)
                EventCountValue(
                    mode = CountMode.COUNT_UP,
                    displayDate = date,
                    magnitude = if (elapsed >= 0L) elapsed else -elapsed,
                    countUpState = if (elapsed >= 0L) CountUpState.ELAPSED else CountUpState.STARTS_IN,
                )
            }
        }
}
