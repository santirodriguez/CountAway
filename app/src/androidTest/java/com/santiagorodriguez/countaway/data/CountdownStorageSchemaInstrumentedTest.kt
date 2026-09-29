package com.santiagorodriguez.countaway.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.RepeatRule
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CountdownStorageSchemaInstrumentedTest {
    @Test
    fun expandedRecurrenceWritesSchema6AndRoundTrips() {
        val event = CountdownEvent(
            id = "monthly-event",
            title = "Monthly",
            date = LocalDate.of(2026, 1, 31),
            type = EventType.EVENT,
            createdAt = Instant.parse("2026-09-23T00:00:00Z"),
            repeatRule = RepeatRule.MONTHLY,
        )

        val payload = CountdownStorageCodec.encode(listOf(event))

        assertEquals(6, JSONObject(payload).getInt("schemaVersion"))
        assertEquals(listOf(event), CountdownStorageCodec.decode(payload))
    }
}
