package com.santiagorodriguez.countaway.countdown

import com.santiagorodriguez.countaway.model.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CountdownOccurrenceResolverTest {
    @Test
    fun oneShotDatesRemainAnchoredEvenAfterTheyPass() {
        val anchor = LocalDate.of(2026, 9, 10)
        val today = LocalDate.of(2026, 9, 16)

        assertEquals(anchor, CountdownOccurrenceResolver.displayDate(anchor, RepeatRule.NONE, today))
        assertNull(CountdownOccurrenceResolver.nextOccurrenceOnOrAfter(anchor, RepeatRule.NONE, today))
    }

    @Test
    fun yearlyDatesUseTheCurrentOccurrenceWhenItHasNotPassed() {
        val anchor = LocalDate.of(2020, 11, 12)
        val today = LocalDate.of(2026, 9, 16)

        assertEquals(
            LocalDate.of(2026, 11, 12),
            CountdownOccurrenceResolver.displayDate(anchor, RepeatRule.YEARLY, today),
        )
    }

    @Test
    fun yearlyDatesRollForwardAfterTheCurrentOccurrence() {
        val anchor = LocalDate.of(2020, 9, 10)
        val today = LocalDate.of(2026, 9, 16)

        assertEquals(
            LocalDate.of(2027, 9, 10),
            CountdownOccurrenceResolver.displayDate(anchor, RepeatRule.YEARLY, today),
        )
    }

    @Test
    fun yearlyDatesNeverBackdateBeforeTheirAnchor() {
        val anchor = LocalDate.of(2028, 5, 10)
        val today = LocalDate.of(2026, 9, 16)

        assertEquals(anchor, CountdownOccurrenceResolver.displayDate(anchor, RepeatRule.YEARLY, today))
    }

    @Test
    fun leapDayFallsBackToFebruary28AndReturnsToFebruary29() {
        val anchor = LocalDate.of(2024, 2, 29)

        assertEquals(
            LocalDate.of(2025, 2, 28),
            CountdownOccurrenceResolver.displayDate(anchor, RepeatRule.YEARLY, LocalDate.of(2025, 2, 1)),
        )
        assertEquals(
            LocalDate.of(2028, 2, 29),
            CountdownOccurrenceResolver.displayDate(anchor, RepeatRule.YEARLY, LocalDate.of(2028, 2, 1)),
        )
    }

    @Test
    fun leapDayMovesToTheFollowingYearOnceFebruary28HasPassed() {
        val anchor = LocalDate.of(2024, 2, 29)

        assertEquals(
            LocalDate.of(2026, 2, 28),
            CountdownOccurrenceResolver.displayDate(anchor, RepeatRule.YEARLY, LocalDate.of(2025, 3, 1)),
        )
    }
}
