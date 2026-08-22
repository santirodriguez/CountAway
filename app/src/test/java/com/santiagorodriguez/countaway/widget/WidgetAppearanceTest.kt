package com.santiagorodriguez.countaway.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun explicitAppearanceDoesNotDependOnSystemTheme() {
        assertFalse(WidgetAppearance.LIGHT.resolveDark(systemDark = false))
        assertFalse(WidgetAppearance.LIGHT.resolveDark(systemDark = true))
        assertTrue(WidgetAppearance.DARK.resolveDark(systemDark = false))
        assertTrue(WidgetAppearance.DARK.resolveDark(systemDark = true))
    }

    @Test
    fun systemAppearanceTracksSystemTheme() {
        assertFalse(WidgetAppearance.SYSTEM.resolveDark(systemDark = false))
        assertTrue(WidgetAppearance.SYSTEM.resolveDark(systemDark = true))
    }
}
