package com.santiagorodriguez.countaway.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountdownStorageSchemaTest {
    @Test
    fun allReleasedSchemasRemainSupported() {
        assertTrue(CountdownStorageSchema.isSupported(CountdownStorageSchema.LEGACY_VERSION))
        assertTrue(CountdownStorageSchema.isSupported(CountdownStorageSchema.PREVIOUS_VERSION))
        assertTrue(CountdownStorageSchema.isSupported(CountdownStorageSchema.NOTIFICATION_VERSION))
        assertTrue(CountdownStorageSchema.isSupported(CountdownStorageSchema.CURRENT_VERSION))
    }

    @Test
    fun missingOrFutureSchemasAreRejected() {
        assertFalse(CountdownStorageSchema.isSupported(-1))
        assertFalse(CountdownStorageSchema.isSupported(CountdownStorageSchema.CURRENT_VERSION + 1))
    }
}
