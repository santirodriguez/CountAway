package com.santiagorodriguez.countaway.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetPinRequestSnapshotTest {
    @Test
    fun callbackDataRoundTripsEventAndStyle() {
        val expected = WidgetPinRequestSnapshot.create(
            eventId = "event / with spaces",
            style = WidgetStyleSelection(
                appearance = WidgetAppearance.LIGHT,
                background = WidgetBackground.MONOGRAM,
            ),
            requestToken = "request-token",
        )

        assertEquals(expected, WidgetPinRequestSnapshot.fromCallbackData(expected.callbackData()))
    }

    @Test
    fun repeatedRequestsForSameEventHaveDistinctTokensAndIdentities() {
        val style = WidgetStyleSelection(
            appearance = WidgetAppearance.DARK,
            background = WidgetBackground.FOREST,
        )
        val first = WidgetPinRequestSnapshot.create("same-event", style)
        val second = WidgetPinRequestSnapshot.create("same-event", style)

        assertNotEquals(first.requestToken, second.requestToken)
        assertNotEquals(first.callbackData(), second.callbackData())
    }

    @Test
    fun callbackSnapshotKeepsStyleSelectedAtRequestTime() {
        val requestedStyle = WidgetStyleSelection(
            appearance = WidgetAppearance.LIGHT,
            background = WidgetBackground.MONOGRAM,
        )
        val encoded = WidgetPinRequestSnapshot.create(
            eventId = "event",
            style = requestedStyle,
            requestToken = "stable-request",
        ).callbackData()

        assertEquals(
            requestedStyle,
            WidgetPinRequestSnapshot.fromCallbackData(encoded)?.style,
        )
    }

    @Test
    fun invalidOrIncompleteCallbackDataIsRejected() {
        assertNull(WidgetPinRequestSnapshot.fromCallbackData(null))
        assertNull(WidgetPinRequestSnapshot.fromCallbackData("countaway://widget/other/token"))
        assertNull(
            WidgetPinRequestSnapshot.fromCallbackData(
                "countaway://widget/pin/token?event=e&appearance=future&background=classic",
            ),
        )
        assertNull(
            WidgetPinRequestSnapshot.fromCallbackData(
                "countaway://widget/pin/token?event=e&appearance=system&background=future",
            ),
        )
    }
}
