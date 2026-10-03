package com.santiagorodriguez.countaway.data

import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.ReminderOption
import com.santiagorodriguez.countaway.model.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CountdownStorageCountModeTest {
    @Test
    fun schemas1Through6DefaultToCountdown() {
        (CountdownStorageSchema.LEGACY_VERSION..CountdownStorageSchema.EXPANDED_RECURRENCE_VERSION)
            .forEach { version ->
                assertEquals(CountMode.COUNT_DOWN, CountdownStorageSchema.countModeFor(version, null))
            }
    }

    @Test
    fun schema7UsesStrictStableCountModeKeys() {
        assertEquals(
            CountMode.COUNT_DOWN,
            CountdownStorageSchema.countModeFor(
                CountdownStorageSchema.COUNT_MODE_VERSION,
                CountMode.COUNT_DOWN.storageKey,
            ),
        )
        assertEquals(
            CountMode.COUNT_UP,
            CountdownStorageSchema.countModeFor(
                CountdownStorageSchema.COUNT_MODE_VERSION,
                CountMode.COUNT_UP.storageKey,
            ),
        )
        assertNull(
            CountdownStorageSchema.countModeFor(
                CountdownStorageSchema.COUNT_MODE_VERSION,
                "unsupported",
            ),
        )
        assertNull(
            CountdownStorageSchema.countModeFor(
                CountdownStorageSchema.COUNT_MODE_VERSION,
                null,
            ),
        )
    }

    @Test
    fun countUpValidationRejectsReminderOrRecurrenceInsteadOfNormalizing() {
        listOf(
            event("reminder").copy(reminder = ReminderOption.ON_DAY),
            event("repeat").copy(repeatRule = RepeatRule.YEARLY),
        ).forEach { invalid ->
            val error = assertThrows(CountdownDataException::class.java) {
                CountdownValidation.validateStoredEvents(listOf(invalid))
            }
            assertEquals(CountdownDataProblem.CORRUPT, error.problem)
        }
    }

    @Test
    fun validCountUpPassesStorageValidation() {
        CountdownValidation.validateStoredEvents(listOf(event("valid")))
    }

    private fun event(id: String) = CountdownEvent(
        id = id,
        title = id,
        date = LocalDate.of(2026, 9, 29),
        type = EventType.EVENT,
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
        countMode = CountMode.COUNT_UP,
    )
}
