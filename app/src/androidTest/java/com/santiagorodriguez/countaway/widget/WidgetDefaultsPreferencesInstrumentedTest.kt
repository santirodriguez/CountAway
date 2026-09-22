package com.santiagorodriguez.countaway.widget

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetDefaultsPreferencesInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val rawPreferences =
        context.getSharedPreferences("countaway_widget_defaults", Context.MODE_PRIVATE)

    @Test
    fun missingDefaultsUseFactoryStyle() {
        rawPreferences.edit().clear().commit()
        try {
            assertEquals(WidgetStyleSelection.FACTORY, WidgetDefaultsPreferences(context).get())
        } finally {
            rawPreferences.edit().clear().commit()
        }
    }

    @Test
    fun defaultsPersistUsingStableStorageKeys() {
        rawPreferences.edit().clear().commit()
        val expected = WidgetStyleSelection(
            appearance = WidgetAppearance.LIGHT,
            background = WidgetBackground.MONOGRAM,
        )

        try {
            WidgetDefaultsPreferences(context).save(expected)

            assertEquals(expected, WidgetDefaultsPreferences(context).get())
            assertEquals("light", rawPreferences.getString("appearance", null))
            assertEquals("monogram", rawPreferences.getString("background", null))
        } finally {
            rawPreferences.edit().clear().commit()
        }
    }

    @Test
    fun unknownOrMissingFieldsFallBackIndependently() {
        rawPreferences.edit()
            .clear()
            .putString("appearance", "future_appearance")
            .putString("background", WidgetBackground.FOREST.storageKey)
            .commit()

        try {
            assertEquals(
                WidgetStyleSelection(
                    appearance = WidgetAppearance.SYSTEM,
                    background = WidgetBackground.FOREST,
                ),
                WidgetDefaultsPreferences(context).get(),
            )

            rawPreferences.edit()
                .putString("appearance", WidgetAppearance.DARK.storageKey)
                .remove("background")
                .commit()

            assertEquals(
                WidgetStyleSelection(
                    appearance = WidgetAppearance.DARK,
                    background = WidgetBackground.CLASSIC,
                ),
                WidgetDefaultsPreferences(context).get(),
            )
        } finally {
            rawPreferences.edit().clear().commit()
        }
    }

    @Test
    fun changingOrResettingDefaultsDoesNotModifyExistingWidgetConfiguration() {
        rawPreferences.edit().clear().commit()
        val widgetId = 740_001
        val widgetPreferences = WidgetPreferences(context)
        widgetPreferences.remove(widgetId)
        val expectedWidget = WidgetConfiguration(
            eventId = "existing-event",
            appearance = WidgetAppearance.DARK,
            background = WidgetBackground.FOREST,
            eventSelection = WidgetEventSelection.FIXED,
        )

        try {
            widgetPreferences.save(
                appWidgetId = widgetId,
                eventId = expectedWidget.eventId,
                appearance = expectedWidget.appearance,
                background = expectedWidget.background,
                eventSelection = expectedWidget.eventSelection,
            )

            val defaults = WidgetDefaultsPreferences(context)
            defaults.save(
                WidgetStyleSelection(
                    appearance = WidgetAppearance.LIGHT,
                    background = WidgetBackground.MONOGRAM,
                ),
            )
            defaults.save(WidgetStyleSelection.FACTORY)

            assertEquals(expectedWidget, widgetPreferences.get(widgetId))
        } finally {
            rawPreferences.edit().clear().commit()
            widgetPreferences.remove(widgetId)
        }
    }
}
