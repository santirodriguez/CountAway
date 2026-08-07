package com.santiagorodriguez.countaway.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventTypeTest {
    @Test
    fun storageKeysAreStableAndUnique() {
        val expected = mapOf(
            EventType.TRIP to "trip",
            EventType.FIRST_FLIGHT to "first_flight",
            EventType.EXAM to "exam",
            EventType.PARTY to "party",
            EventType.BIRTHDAY to "birthday",
            EventType.EVENT to "event",
            EventType.CUSTOM to "custom",
        )

        assertEquals(expected, EventType.values().associateWith { it.storageKey })
        assertEquals(EventType.values().size, EventType.values().map { it.storageKey }.toSet().size)
    }

    @Test
    fun storageKeyLookupHandlesKnownAndUnknownValues() {
        assertEquals(EventType.FIRST_FLIGHT, EventType.fromStorageKey("first_flight"))
        assertNull(EventType.fromStorageKey("something_new"))
    }

    @Test
    fun legacyNamesRemainReadableForSchemaOne() {
        assertEquals(EventType.BIRTHDAY, EventType.fromLegacyName("BIRTHDAY"))
        assertNull(EventType.fromLegacyName("birthday"))
        assertTrue(EventType.values().all { EventType.fromLegacyName(it.name) == it })
    }
}
