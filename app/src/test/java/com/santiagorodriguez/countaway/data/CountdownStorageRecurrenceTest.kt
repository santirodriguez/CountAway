package com.santiagorodriguez.countaway.data

import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CountdownStorageRecurrenceTest {
    @Test
    fun releasedSchemasBeforeVersion5MigrateToNonRepeatingEvents() {
        listOf(
            CountdownStorageSchema.LEGACY_VERSION,
            CountdownStorageSchema.PREVIOUS_VERSION,
            CountdownStorageSchema.NOTIFICATION_VERSION,
            CountdownStorageSchema.REMINDER_VERSION,
        ).forEach { schemaVersion ->
            assertEquals(
                RepeatRule.NONE,
                CountdownStorageSchema.repeatRuleFor(schemaVersion, rawStorageKey = null),
            )
        }
    }

    @Test
    fun currentSchemaUsesStableRecurrenceStorageKeys() {
        assertEquals(5, CountdownStorageSchema.CURRENT_VERSION)
        assertEquals("none", RepeatRule.NONE.storageKey)
        assertEquals("weekly", RepeatRule.WEEKLY.storageKey)
        assertEquals("monthly", RepeatRule.MONTHLY.storageKey)
        assertEquals("yearly", RepeatRule.YEARLY.storageKey)

        RepeatRule.entries.forEach { repeatRule ->
            assertEquals(
                repeatRule,
                CountdownStorageSchema.repeatRuleFor(
                    CountdownStorageSchema.CURRENT_VERSION,
                    repeatRule.storageKey,
                ),
            )
        }
        assertNull(
            CountdownStorageSchema.repeatRuleFor(
                CountdownStorageSchema.CURRENT_VERSION,
                "unsupported",
            ),
        )
    }

    @Test
    fun weeklyAndMonthlyRoundTripInsideSchema5() {
        val events = listOf(
            event("weekly", RepeatRule.WEEKLY),
            event("monthly", RepeatRule.MONTHLY),
        )

        val payload = CountdownStorageCodec.encode(events)
        val decoded = CountdownStorageCodec.decodeForImport(payload)

        assertEquals(events, decoded)
        assertEquals(true, payload.contains("\"schemaVersion\":5"))
        assertEquals(true, payload.contains("\"repeatRule\":\"weekly\""))
        assertEquals(true, payload.contains("\"repeatRule\":\"monthly\""))
    }

    private fun event(id: String, repeatRule: RepeatRule) = CountdownEvent(
        id = id,
        title = id,
        date = LocalDate.of(2026, 10, 31),
        type = EventType.EVENT,
        createdAt = Instant.parse("2026-09-23T00:00:00Z"),
        repeatRule = repeatRule,
    )
}
