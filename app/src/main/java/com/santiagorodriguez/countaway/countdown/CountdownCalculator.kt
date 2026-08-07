package com.santiagorodriguez.countaway.countdown

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class CountdownStatus {
    FUTURE,
    TOMORROW,
    TODAY,
    DONE,
}

data class CountdownValue(
    val days: Long,
    val status: CountdownStatus,
)

object CountdownCalculator {
    fun daysUntil(today: LocalDate, target: LocalDate): Long =
        ChronoUnit.DAYS.between(today, target)

    fun value(today: LocalDate, target: LocalDate): CountdownValue {
        val days = daysUntil(today, target)
        val status = when {
            days > 1 -> CountdownStatus.FUTURE
            days == 1L -> CountdownStatus.TOMORROW
            days == 0L -> CountdownStatus.TODAY
            else -> CountdownStatus.DONE
        }
        return CountdownValue(days = days, status = status)
    }
}
