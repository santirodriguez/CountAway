package com.santiagorodriguez.countaway.countdown

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CountdownCalculatorTest {
    @Test
    fun sameDayReturnsZeroAndToday() {
        val date = LocalDate.of(2026, 8, 7)
        val value = CountdownCalculator.value(date, date)
        assertEquals(0, value.days)
        assertEquals(CountdownStatus.TODAY, value.status)
    }

    @Test
    fun tomorrowReturnsOneAndTomorrow() {
        val today = LocalDate.of(2026, 8, 7)
        val value = CountdownCalculator.value(today, today.plusDays(1))
        assertEquals(1, value.days)
        assertEquals(CountdownStatus.TOMORROW, value.status)
    }

    @Test
    fun futureDateUsesFutureStatus() {
        val today = LocalDate.of(2026, 8, 7)
        val value = CountdownCalculator.value(today, today.plusDays(17))
        assertEquals(17, value.days)
        assertEquals(CountdownStatus.FUTURE, value.status)
    }

    @Test
    fun yesterdayReturnsMinusOneAndDone() {
        val today = LocalDate.of(2026, 8, 7)
        val value = CountdownCalculator.value(today, today.minusDays(1))
        assertEquals(-1, value.days)
        assertEquals(CountdownStatus.DONE, value.status)
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
