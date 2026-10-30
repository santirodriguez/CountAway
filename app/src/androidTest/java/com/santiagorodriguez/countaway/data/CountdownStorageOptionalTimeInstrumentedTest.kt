package com.santiagorodriguez.countaway.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class CountdownStorageOptionalTimeInstrumentedTest {
    private val original = CountdownEvent(
        id = "legacy-id", title = "Original", date = LocalDate.of(2026, 10, 18),
        type = EventType.EVENT, createdAt = Instant.parse("2026-01-01T00:00:00Z"))
    private val timed = original.copy(
        id = "timed", eventTime = LocalTime.of(16, 30),
        eventZone = ZoneId.of("America/Argentina/Buenos_Aires"))

    @Test fun schema8RoundTripsWithExplicitTimeOnlyWhenPresent() {
        val root = JSONObject(CountdownStorageCodec.encode(listOf(original, timed)))
        assertEquals(8, root.getInt("schemaVersion"))
        val events = root.getJSONArray("events")
        assertFalse(events.getJSONObject(0).has("eventTime"))
        assertFalse(events.getJSONObject(0).has("eventZone"))
        assertEquals("16:30", events.getJSONObject(1).getString("eventTime"))
        assertEquals("America/Argentina/Buenos_Aires",
            events.getJSONObject(1).getString("eventZone"))
        assertEquals(listOf(original, timed), CountdownStorageCodec.decodeForImport(root.toString()))
    }

    @Test fun schema7RemainsReadableWithoutAnInventedTime() {
        val json = JSONObject(CountdownStorageCodec.encode(listOf(original)))
        json.put("schemaVersion", 7)
        assertEquals(original, CountdownStorageCodec.decode(json.toString()).single())
    }

    @Test fun invalidPairsAndTimeFormatsAreRejected() {
        val cases = listOf<(JSONObject) -> Unit>(
            { it.remove("eventTime") }, { it.remove("eventZone") },
            { it.put("eventTime", "16:30:59") },
            { it.put("eventTime", "29:00") },
            { it.put("eventTime", JSONObject.NULL) },
            { it.put("eventZone", "Not/AZone") },
        )
        for (mutate in cases) {
            val json = JSONObject(CountdownStorageCodec.encode(listOf(timed)))
            mutate(json.getJSONArray("events").getJSONObject(0))
            try {
                CountdownStorageCodec.decodeForImport(json.toString())
                fail("Invalid timed event must not import")
            } catch (error: CountdownDataException) {
                assertEquals(CountdownDataProblem.CORRUPT, error.problem)
            }
        }
    }

    @Test fun timedFieldsChangeRevisionAndPreserveHistoricalTitles() {
        assertNotEquals(EventRevision.of(original), EventRevision.of(timed))
        assertNotEquals(EventRevision.of(timed),
            EventRevision.of(timed.copy(eventTime = LocalTime.of(17, 0))))
        val historical = timed.copy(title = "X".repeat(1024))
        assertEquals(listOf(historical),
            CountdownStorageCodec.decodeForImport(CountdownStorageCodec.encode(listOf(historical))))
    }
}
