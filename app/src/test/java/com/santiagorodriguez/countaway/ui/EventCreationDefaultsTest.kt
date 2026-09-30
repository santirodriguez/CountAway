package com.santiagorodriguez.countaway.ui

import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.EventType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class EventCreationDefaultsTest {
    private val today = LocalDate.of(2026, 9, 30)
    private val chosenDate = LocalDate.of(2024, 2, 29)

    @Test fun newUntouchedDatesUseTodayForUpAndTomorrowForDown() {
        assertEquals(today, EventCreationDefaults.dateForMode(chosenDate, CountMode.COUNT_UP, today, true, false))
        assertEquals(today.plusDays(1), EventCreationDefaults.dateForMode(chosenDate, CountMode.COUNT_DOWN, today, true, false))
    }

    @Test fun existingOrChosenDatesNeverMoveOnModeChanges() {
        CountMode.entries.forEach { mode ->
            assertEquals(chosenDate, EventCreationDefaults.dateForMode(chosenDate, mode, today, false, false))
            assertEquals(chosenDate, EventCreationDefaults.dateForMode(chosenDate, mode, today, false, true))
            assertEquals(chosenDate, EventCreationDefaults.dateForMode(chosenDate, mode, today, true, true))
        }
    }

    @Test fun implicitCountUpChoiceIsNeutralAndCountdownDefaultIsPreserved() {
        assertEquals(EventType.EVENT, EventCreationDefaults.typeForMode(EventType.TRIP, CountMode.COUNT_UP, true, false))
        assertEquals(EventType.TRIP, EventCreationDefaults.typeForMode(EventType.EVENT, CountMode.COUNT_DOWN, true, false))
    }

    @Test fun existingAndExplicitCategoriesAreNotReinterpreted() {
        EventType.entries.forEach { type ->
            CountMode.entries.forEach { mode ->
                assertEquals(type, EventCreationDefaults.typeForMode(type, mode, false, false))
                assertEquals(type, EventCreationDefaults.typeForMode(type, mode, true, true))
            }
        }
    }
}
