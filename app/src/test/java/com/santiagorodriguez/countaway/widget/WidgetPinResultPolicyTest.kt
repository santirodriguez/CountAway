package com.santiagorodriguez.countaway.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPinResultPolicyTest {
    @Test
    fun confirmedUnconfiguredOwnedWidgetCanReceiveSnapshot() {
        assertTrue(
            WidgetPinResultPolicy.shouldApply(
                owned = true,
                eventExists = true,
                existing = null,
            ),
        )
    }

    @Test
    fun lateOrDuplicateCallbackCannotOverwriteExistingConfiguration() {
        val existing = WidgetConfiguration(
            eventId = "manually-configured",
            appearance = WidgetAppearance.DARK,
            background = WidgetBackground.FOREST,
            eventSelection = WidgetEventSelection.FIXED,
        )

        assertFalse(
            WidgetPinResultPolicy.shouldApply(
                owned = true,
                eventExists = true,
                existing = existing,
            ),
        )
    }

    @Test
    fun removedWidgetOrDeletedEventIsRejected() {
        assertFalse(
            WidgetPinResultPolicy.shouldApply(
                owned = false,
                eventExists = true,
                existing = null,
            ),
        )
        assertFalse(
            WidgetPinResultPolicy.shouldApply(
                owned = true,
                eventExists = false,
                existing = null,
            ),
        )
    }
}
