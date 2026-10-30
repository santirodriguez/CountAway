package com.santiagorodriguez.countaway.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ArrivalNotificationIdentityTest {
    @Test
    fun collidingStringHashesStillProduceDistinctNotificationTags() {
        val first = "Aa"
        val second = "BB"

        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(
            ArrivalNotificationIdentity.tag(first),
            ArrivalNotificationIdentity.tag(second),
        )
    }
    @Test
    fun largeHistoricalIdUsesBoundedNotificationIdentity() {
        val longId = "historical-" + "N".repeat(1_100_000)
        val first = ArrivalNotificationIdentity.tag(longId)
        assertEquals(74, first.length)
        assertEquals(first, ArrivalNotificationIdentity.tag(longId))
        assertNotEquals(first, ArrivalNotificationIdentity.tag(longId + "!"))
        assertEquals("legacy", ArrivalNotificationIdentity.tag("legacy"))
    }

}
