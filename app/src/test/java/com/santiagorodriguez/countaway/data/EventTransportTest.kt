package com.santiagorodriguez.countaway.data

import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class EventTransportTest {
    private fun event(id: String, title: String = "Existing") = CountdownEvent(
        id = id, title = title, date = LocalDate.of(2027, 1, 1), type = EventType.EVENT,
        createdAt = Instant.EPOCH,
    )

    @Test fun smallLegacyIdsKeepTheirExistingContract() {
        val id = "legacy-short-id"
        assertEquals(id, EventTransport.wireId(id))
        assertEquals(id, EventTransport.resolve(listOf(event(id)), id)?.id)
    }

    @Test fun largeHistoricalIdsHaveStableBoundedReferences() {
        val long = "historical-" + "A".repeat(1_100_000)
        val ref = EventTransport.wireId(long)
        assertTrue(EventTransport.isReference(ref))
        assertEquals(74, ref.length)
        assertEquals(ref, EventTransport.wireId(long))
        assertEquals(long, EventTransport.resolve(listOf(event(long)), ref)?.id)
        assertNull(EventTransport.resolve(listOf(event(long)), EventTransport.wireId(long + "!")))
        assertNull(EventTransport.resolve(emptyList(), ref))
    }

    @Test fun orderingAndTitleChangesCannotSwitchTheSelectedId() {
        val a = event("X".repeat(2000), "before")
        val b = event("Y".repeat(2000))
        val reference = EventTransport.wireId(a.id)
        assertEquals(a.id, EventTransport.resolve(listOf(b, a.copy(title = "after")), reference)?.id)
        assertEquals(a.id, EventTransport.resolve(listOf(a, b), reference)?.id)
    }

    @Test fun forgedOrAmbiguousIdentifiersFailClosed() {
        val a = event("X".repeat(1500))
        val ref = EventTransport.wireId(a.id)
        val collidingRawId = event(ref)
        assertNull(EventTransport.resolve(listOf(a, collidingRawId), ref))
        assertNull(EventTransport.resolve(listOf(a, a.copy(title = "duplicate")), ref))
        assertNull(EventTransport.resolve(listOf(a), "sha256-v1:invalid"))
    }

    @Test fun previewBoundsOnlyVisibleText() {
        val huge = "A".repeat(4000)
        assertEquals(512, EventTransport.preview(huge).length)
        assertEquals("Ordinary", EventTransport.preview("Ordinary"))
        assertEquals(4000, huge.length)
    }
}
