package com.santiagorodriguez.countaway.countdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class CountdownTimeTest {
    @Test
    fun snapshotUsesInjectedClockAndZoneAsOneTemporalOperation() {
        val zone = ZoneId.of("America/New_York")
        val clock = Clock.fixed(Instant.parse("2026-12-31T23:30:00Z"), zone)

        val snapshot = CountdownTime.snapshot(clock)

        assertEquals(zone, snapshot.zone)
        assertEquals(ZonedDateTime.now(clock), snapshot.now)
        assertEquals(snapshot.now.toLocalDate(), snapshot.today)
    }

    @Test
    fun nextDateInvalidationTracksCivilMidnightAcrossDst() {
        val zone = ZoneId.of("America/New_York")
        val beforeSpringForward = CountdownTimeSnapshot(
            ZonedDateTime.of(2026, 3, 7, 23, 59, 0, 0, zone),
        )
        val beforeFallBack = CountdownTimeSnapshot(
            ZonedDateTime.of(2026, 10, 31, 23, 59, 0, 0, zone),
        )

        assertTrue(
            CountdownTemporalInvalidationPolicy.delayUntilNextDateBoundaryMillis(beforeSpringForward) in
                60_000L..121_000L,
        )
        assertTrue(
            CountdownTemporalInvalidationPolicy.delayUntilNextDateBoundaryMillis(beforeFallBack) in
                60_000L..121_000L,
        )
    }

    @Test
    fun oppositeSideTimezonesCanProduceDifferentCivilDatesForSameInstant() {
        val instant = Instant.parse("2026-01-01T00:30:00Z")

        val newYork = CountdownTime.snapshot(Clock.fixed(instant, ZoneId.of("America/New_York")))
        val kiritimati = CountdownTime.snapshot(Clock.fixed(instant, ZoneId.of("Pacific/Kiritimati")))

        assertEquals("2025-12-31", newYork.today.toString())
        assertEquals("2026-01-01", kiritimati.today.toString())
    }
}
