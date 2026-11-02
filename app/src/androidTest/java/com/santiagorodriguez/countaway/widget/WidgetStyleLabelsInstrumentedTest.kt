package com.santiagorodriguez.countaway.widget

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class WidgetStyleLabelsInstrumentedTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun allBackgroundAndAppearanceNamesAreLocalizedAndNeverUseStorageKeys() {
        for ((tag, expectedRidge) in listOf(
            "en" to "Ridge", "es" to "Cresta", "ca" to "Carena",
        )) {
            val context = app.createConfigurationContext(
                Configuration(app.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(tag))
                })
            val backgrounds = WidgetBackground.entries.map {
                context.getString(WidgetStyleLabels.background(it))
            }
            val appearances = WidgetAppearance.entries.map {
                context.getString(WidgetStyleLabels.appearance(it))
            }
            assertEquals("$tag backgrounds", 9, backgrounds.size)
            assertEquals("$tag unique backgrounds", 9, backgrounds.distinct().size)
            assertEquals("$tag appearances", 3, appearances.size)
            assertEquals("$tag unique appearances", 3, appearances.distinct().size)
            assertEquals(expectedRidge, context.getString(
                WidgetStyleLabels.background(WidgetBackground.MONOGRAM)))
            for ((index, item) in WidgetBackground.entries.withIndex()) {
                assertNotEquals("$tag background $item exposes key", item.storageKey, backgrounds[index])
            }
            val previewSummary = context.getString(R.string.widget_style_summary,
                backgrounds.last(), appearances.first())
            assertTrue(previewSummary.contains(expectedRidge))
            assertFalse(previewSummary.contains(WidgetBackground.MONOGRAM.storageKey))
        }
    }
}
