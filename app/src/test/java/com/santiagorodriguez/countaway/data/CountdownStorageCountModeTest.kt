package com.santiagorodriguez.countaway.data

import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.ReminderOption
import com.santiagorodriguez.countaway.model.RepeatRule
import org.junit.Assert.assertEquals
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
    fun schema6RecurrenceFixtureRemainsReadableAndDefaultsToCountdown() {
        val event = CountdownStorageCodec.decode(SCHEMA_6_FIXTURE).single()

        assertEquals(RepeatRule.MONTHLY, event.repeatRule)
        assertEquals(ReminderOption.THREE_DAYS, event.reminder)
        assertEquals(CountMode.COUNT_DOWN, event.countMode)
    }

    @Test
    fun schema7MixedModesRoundTripLosslessly() {
        val events = listOf(
            event("down", CountMode.COUNT_DOWN).copy(
                reminder = ReminderOption.ONE_DAY,
                repeatRule = RepeatRule.WEEKLY,
            ),
            event("up", CountMode.COUNT_UP),
        )

        assertEquals(events, CountdownStorageCodec.decode(CountdownStorageCodec.encode(events)))
    }

    @Test
    fun schema7RequiresKnownStringCountMode() {
        listOf(
            SCHEMA_7_MISSING_MODE,
            SCHEMA_7_UNKNOWN_MODE,
            SCHEMA_7_NON_STRING_MODE,
        ).forEach { payload ->
            val error = assertThrows(CountdownDataException::class.java) {
                CountdownStorageCodec.decode(payload)
            }
            assertEquals(CountdownDataProblem.CORRUPT, error.problem)
        }
    }

    @Test
    fun countUpRejectsReminderOrRecurrenceInsteadOfSilentlyNormalizing() {
        listOf(
            event("reminder", CountMode.COUNT_UP).copy(reminder = ReminderOption.ON_DAY),
            event("repeat", CountMode.COUNT_UP).copy(repeatRule = RepeatRule.YEARLY),
        ).forEach { invalid ->
            val error = assertThrows(CountdownDataException::class.java) {
                CountdownStorageCodec.encode(listOf(invalid))
            }
            assertEquals(CountdownDataProblem.CORRUPT, error.problem)
        }

        val error = assertThrows(CountdownDataException::class.java) {
            CountdownStorageCodec.decode(SCHEMA_7_INVALID_COMBINATION)
        }
        assertEquals(CountdownDataProblem.CORRUPT, error.problem)
    }

    private fun event(id: String, mode: CountMode) = CountdownEvent(
        id = id,
        title = id,
        date = LocalDate.of(2026, 9, 29),
        type = EventType.EVENT,
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
        countMode = mode,
    )

    private companion object {
        val SCHEMA_6_FIXTURE = """
            {
              "schemaVersion": 6,
              "events": [{
                "id": "schema-6",
                "title": "Monthly",
                "date": "2026-10-31",
                "type": "event",
                "iconKey": "calendar",
                "reminderKey": "three_days",
                "repeatRule": "monthly",
                "createdAt": "2026-09-01T00:00:00Z"
              }]
            }
        """.trimIndent()

        val SCHEMA_7_MISSING_MODE = SCHEMA_6_FIXTURE
            .replace(""schemaVersion": 6", ""schemaVersion": 7")
        val SCHEMA_7_UNKNOWN_MODE = SCHEMA_7_MISSING_MODE
            .replace(""createdAt"", ""countMode": "sideways",\n                "createdAt"")
        val SCHEMA_7_NON_STRING_MODE = SCHEMA_7_MISSING_MODE
            .replace(""createdAt"", ""countMode": 7,\n                "createdAt"")
        val SCHEMA_7_INVALID_COMBINATION = SCHEMA_7_MISSING_MODE
            .replace(""createdAt"", ""countMode": "count_up",\n                "createdAt"")
    }
}
