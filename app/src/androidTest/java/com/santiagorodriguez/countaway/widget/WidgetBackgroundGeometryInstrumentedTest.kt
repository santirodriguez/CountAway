package com.santiagorodriguez.countaway.widget

import android.content.res.Configuration
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetBackgroundGeometryInstrumentedTest {
    @Test fun cappedBitmapKeepsRoundedCornersInLogicalDp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(target.resources.configuration).apply { densityDpi = 480 }
        val context = target.createConfigurationContext(config)
        for (background in listOf(WidgetBackground.CLASSIC, WidgetBackground.HORIZON,
                WidgetBackground.MONOGRAM)) {
            for (dark in listOf(false, true)) {
                val bitmap = WidgetBackgroundRenderer.render(
                    context, background, dark, 240, 160,
                )
                assertEquals(360, bitmap.width)
                assertEquals(240, bitmap.height)
                val firstSolidX = (0 until bitmap.width).firstOrNull {
                    Color.alpha(bitmap.getPixel(it, 2)) >= 128
                } ?: throw AssertionError("No visible outline for $background dark=$dark")
                // 20dp at 3x downsampled to 1.5x: corner radius about 30px.
                // The old unscaled drawable used a radius near 60px here.
                assertTrue("$background dark=$dark corner starts at $firstSolidX",
                    firstSolidX in 12..35)
                bitmap.recycle()
            }
        }
    }
}
