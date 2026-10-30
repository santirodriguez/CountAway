package com.santiagorodriguez.countaway.widget

import org.junit.Assert.*
import org.junit.Test

class WidgetBatchUpdateTest {
    @Test fun failingWidgetDoesNotAbortLaterUpdates() {
        val attempted = mutableListOf<Int>()
        val failures = mutableListOf<Pair<Int, Class<*>>>()
        WidgetBatchUpdate.forEach(
            ids = intArrayOf(1, 2, 3, 4),
            update = { id ->
                attempted += id
                if (id == 2) throw IllegalArgumentException("invalid host id")
                if (id == 3) throw SecurityException("host disappeared")
            },
            onFailure = { id, error -> failures += id to error.javaClass },
        )
        assertEquals(listOf(1, 2, 3, 4), attempted)
        assertEquals(listOf(2 to IllegalArgumentException::class.java,
            3 to SecurityException::class.java), failures)
    }

    @Test fun fatalVmErrorsAreNotSuppressed() {
        val failure = AssertionError("fatal rendering failure")
        try {
            WidgetBatchUpdate.forEach(
                ids = intArrayOf(4, 5),
                update = { throw failure },
                onFailure = { _, _ -> fail("fatal errors must not be swallowed") },
            )
            fail("Expected AssertionError")
        } catch (actual: AssertionError) {
            assertSame(failure, actual)
        }
    }
}
