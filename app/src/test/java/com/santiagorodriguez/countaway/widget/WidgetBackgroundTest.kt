package com.santiagorodriguez.countaway.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetBackgroundTest {
    @Test
    fun storageKeysRoundTripAndClassicIsFallback() {
        WidgetBackground.entries.forEach { background ->
            assertEquals(background, WidgetBackground.fromStorageKey(background.storageKey))
        }
        assertEquals(WidgetBackground.CLASSIC, WidgetBackground.fromStorageKey(null))
        assertEquals(WidgetBackground.CLASSIC, WidgetBackground.fromStorageKey("unknown"))
    }
}
