package com.santiagorodriguez.countaway.data

import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventIcon
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.ReminderOption
import com.santiagorodriguez.countaway.model.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CountdownStorageRecurrenceTest {
    @Test
    fun version4DataMigratesToNonRepeatingEvents() {
        val payload = """
            {
              "schemaVersion": 4,
              "events": [
                {
                  "id": "legacy-v4",
                  "title": "Existing countdown",
                  "date": "2026-12-01",
                  "type": "event",
                  "iconKey": "calendar",
                  "reminderKey": "three_days",
                  "createdAt": "2026-08-23T12:00:00Z"
                }
              ]
            }
        """.trimIndent()

        val decoded = CountdownStorageCodec.decode(payload).single()

        assertEquals(RepeatRule.NONE, decoded.repeatRule)
        assertEquals(ReminderOption.THREE_DAYS, decoded.reminder)
    }

    @Test
    fun currentSchemaRoundTripsYearlyRecurrence() {
        val event = CountdownEvent(
            id = "yearly",
            title = "Birthday",
            date = LocalDate.of(2020, 11, 12),
            type = EventType.BIRTHDAY,
            icon = EventIcon.CAKE,
            reminder = ReminderOption.SEVEN_DAYS,
            createdAt = Instant.parse("2026-08-23T12:00:00Z"),
            repeatRule = RepeatRule.YEARLY,
        )

        val encoded = CountdownStorageCodec.encode(listOf(event))
        val decoded = CountdownStorageCodec.decode(encoded).single()

        assertTrue(encoded.contains("\"schemaVersion\":${CountdownStorageSchema.CURRENT_VERSION}"))
        assertTrue(encoded.contains("\"repeatRule\":\"yearly\""))
        assertEquals(event, decoded)
    }
}
