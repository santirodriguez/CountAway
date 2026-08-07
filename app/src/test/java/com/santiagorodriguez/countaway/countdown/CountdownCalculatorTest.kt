package com.santiagorodriguez.countaway.countdown

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CountdownCalculatorTest {
    @Test
    fun sameDayReturnsZero() {
        val date = LocalDate.of(2026, 8, 7)
        assertEquals(0, CountdownCalculator.daysUntil(date, date))
    }

    @Test
    fun tomorrowReturnsOne() {
        val today = LocalDate.of(2026, 8, 7)
        assertEquals(1, CountdownCalculator.daysUntil(today, today.plusDays(1)))
    }

    @Test
    fun yesterdayReturnsMinusOne() {
        val today = LocalDate.of(2026, 8, 7)
        assertEquals(-1, CountdownCalculator.daysUntil(today, today.minusDays(1)))
    }

    @Test
    fun leapDayIsHandledByCalendarMath() {
        val today = LocalDate.of(2028, 2, 28)
        val target = LocalDate.of(2028, 3, 1)
        assertEquals(2, CountdownCalculator.daysUntil(today, target))
    }

    @Test
    fun yearBoundaryIsHandled() {
        val today = LocalDate.of(2026, 12, 31)
        val target = LocalDate.of(2027, 1, 1)
        assertEquals(1, CountdownCalculator.daysUntil(today, target))
    }
}
