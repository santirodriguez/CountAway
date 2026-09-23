package com.santiagorodriguez.countaway.data

import com.santiagorodriguez.countaway.model.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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

}
