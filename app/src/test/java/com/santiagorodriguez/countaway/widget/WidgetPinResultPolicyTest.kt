package com.santiagorodriguez.countaway.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPinResultPolicyTest {
    @Test
    fun validUnconfiguredWidgetCanReceiveSnapshotWithoutVisibilityGate() {
        assertTrue(
            WidgetPinResultPolicy.shouldApply(
                validWidgetId = true,
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
                validWidgetId = true,
                eventExists = true,
                existing = existing,
            ),
        )
    }

    @Test
    fun invalidWidgetOrDeletedEventIsRejected() {
        assertFalse(
            WidgetPinResultPolicy.shouldApply(
                validWidgetId = false,
                eventExists = true,
                existing = null,
            ),
        )
        assertFalse(
            WidgetPinResultPolicy.shouldApply(
                validWidgetId = true,
                eventExists = false,
                existing = null,
            ),
        )
    }

    @Test
    fun matchingExistingConfigurationIsRecognizedAsIdempotent() {
        val snapshot = WidgetPinRequestSnapshot.create(
            eventId = "event-a",
            style = WidgetStyleSelection(
                appearance = WidgetAppearance.LIGHT,
                background = WidgetBackground.HORIZON,
            ),
            requestToken = "token",
        )
        val existing = WidgetConfiguration(
            eventId = "event-a",
            appearance = WidgetAppearance.LIGHT,
            background = WidgetBackground.HORIZON,
            eventSelection = WidgetEventSelection.FIXED,
        )

        assertTrue(WidgetPinResultPolicy.matchesSnapshot(existing, snapshot))
    }
}
