package com.santiagorodriguez.countaway.countdown

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object CountdownCalculator {
    fun daysUntil(today: LocalDate, target: LocalDate): Long =
        ChronoUnit.DAYS.between(today, target)
}
