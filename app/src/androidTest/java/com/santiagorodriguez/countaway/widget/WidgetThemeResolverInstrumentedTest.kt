package com.santiagorodriguez.countaway.widget

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetThemeResolverInstrumentedTest {
    @Test
    fun everyWidgetBackgroundRendersInLightAndDarkModes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        WidgetBackground.entries.forEach { background ->
            listOf(false, true).forEach { dark ->
                val bitmap = WidgetBackgroundRenderer.render(
                    context = context,
                    background = background,
                    dark = dark,
                    widthDp = 180,
                    heightDp = 110,
                )
                assertTrue(bitmap.width > 0)
                assertTrue(bitmap.height > 0)
            }
        }
    }

    @Test
    fun systemAppearanceReadsApplicationSystemModeInsteadOfCallerOverride() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val actualSystemDark = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val forcedConfiguration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (actualSystemDark) {
                    Configuration.UI_MODE_NIGHT_NO
                } else {
                    Configuration.UI_MODE_NIGHT_YES
                }
        }
        val forcedContext = context.createConfigurationContext(forcedConfiguration)

        assertEquals(
            actualSystemDark,
            WidgetThemeResolver.resolve(forcedContext, WidgetAppearance.SYSTEM).dark,
        )
    }
}
