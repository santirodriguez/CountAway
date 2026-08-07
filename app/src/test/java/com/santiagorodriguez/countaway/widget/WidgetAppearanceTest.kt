package com.santiagorodriguez.countaway.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetAppearanceTest {
    @Test
    fun storageKeysRoundTrip() {
        WidgetAppearance.entries.forEach { appearance ->
            assertEquals(appearance, WidgetAppearance.fromStorageKey(appearance.storageKey))
        }
    }

    @Test
    fun missingOrUnknownValuesFallBackToSystem() {
        assertEquals(WidgetAppearance.SYSTEM, WidgetAppearance.fromStorageKey(null))
        assertEquals(WidgetAppearance.SYSTEM, WidgetAppearance.fromStorageKey("future_theme"))
    }
}
