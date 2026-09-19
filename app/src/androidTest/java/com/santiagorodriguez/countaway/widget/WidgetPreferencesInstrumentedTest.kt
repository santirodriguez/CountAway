package com.santiagorodriguez.countaway.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetPreferencesInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun hostRestoreRemapsConfigurationAndClearsOldId() {
        val preferences = WidgetPreferences(context)
        val oldId = 730_001
        val newId = 730_002
        preferences.remove(oldId)
        preferences.remove(newId)

        try {
            preferences.save(
                appWidgetId = oldId,
                eventId = "event-restored",
                appearance = WidgetAppearance.DARK,
                background = WidgetBackground.FOREST,
                eventSelection = WidgetEventSelection.FIXED,
            )

            preferences.remap(oldId, newId)

            assertNull(preferences.get(oldId))
            assertEquals(
                WidgetConfiguration(
                    eventId = "event-restored",
                    appearance = WidgetAppearance.DARK,
                    background = WidgetBackground.FOREST,
                    eventSelection = WidgetEventSelection.FIXED,
                ),
                preferences.get(newId),
            )
        } finally {
            preferences.remove(oldId)
            preferences.remove(newId)
        }
    }
}
