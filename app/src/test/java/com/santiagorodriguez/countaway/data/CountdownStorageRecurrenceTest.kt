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
        assertEquals("none", RepeatRule.NONE.storageKey)
        assertEquals("yearly", RepeatRule.YEARLY.storageKey)
        assertEquals(
            RepeatRule.YEARLY,
            CountdownStorageSchema.repeatRuleFor(
                CountdownStorageSchema.CURRENT_VERSION,
                RepeatRule.YEARLY.storageKey,
            ),
        )
        assertNull(
            CountdownStorageSchema.repeatRuleFor(
                CountdownStorageSchema.CURRENT_VERSION,
                "unsupported",
            ),
        )
    }
}
