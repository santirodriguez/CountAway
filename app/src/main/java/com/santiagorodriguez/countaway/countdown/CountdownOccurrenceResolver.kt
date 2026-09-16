package com.santiagorodriguez.countaway.countdown

import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.RepeatRule
import java.time.LocalDate
import java.time.YearMonth

object CountdownOccurrenceResolver {
    fun displayDate(event: CountdownEvent, today: LocalDate): LocalDate =
        displayDate(event.date, event.repeatRule, today)

    fun displayDate(anchorDate: LocalDate, repeatRule: RepeatRule, today: LocalDate): LocalDate =
        when (repeatRule) {
            RepeatRule.NONE -> anchorDate
            RepeatRule.YEARLY -> checkNotNull(nextOccurrenceOnOrAfter(anchorDate, repeatRule, today))
        }

    fun nextOccurrenceOnOrAfter(event: CountdownEvent, threshold: LocalDate): LocalDate? =
        nextOccurrenceOnOrAfter(event.date, event.repeatRule, threshold)

    fun nextOccurrenceOnOrAfter(
        anchorDate: LocalDate,
        repeatRule: RepeatRule,
        threshold: LocalDate,
    ): LocalDate? = when (repeatRule) {
        RepeatRule.NONE -> anchorDate.takeUnless { it.isBefore(threshold) }
        RepeatRule.YEARLY -> nextYearlyOccurrenceOnOrAfter(anchorDate, threshold)
    }

    private fun nextYearlyOccurrenceOnOrAfter(anchorDate: LocalDate, threshold: LocalDate): LocalDate {
        if (!anchorDate.isBefore(threshold)) return anchorDate

        var year = maxOf(anchorDate.year, threshold.year)
        var candidate = yearlyDate(anchorDate, year)
        if (candidate.isBefore(anchorDate)) {
            candidate = anchorDate
        }
        if (candidate.isBefore(threshold)) {
            year += 1
            candidate = yearlyDate(anchorDate, year)
        }
        return candidate
    }

    private fun yearlyDate(anchorDate: LocalDate, year: Int): LocalDate {
        val month = YearMonth.of(year, anchorDate.month)
        val day = anchorDate.dayOfMonth.coerceAtMost(month.lengthOfMonth())
        return LocalDate.of(year, anchorDate.month, day)
    }
}
