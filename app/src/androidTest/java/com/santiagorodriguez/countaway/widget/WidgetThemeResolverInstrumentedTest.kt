package com.santiagorodriguez.countaway.widget

import android.content.res.Configuration
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetThemeResolverInstrumentedTest {
    @Test
    fun everyWidgetBackgroundRendersInLightAndDarkAcrossAllSizeBuckets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        WidgetBackground.entries.forEach { background ->
            listOf(false, true).forEach { dark ->
                WidgetPreviewSizing.orderedSizes.forEach { size ->
                    val dimensions = WidgetPreviewSizing.representative(size)
                    val bitmap = WidgetBackgroundRenderer.render(
                        context = context,
                        background = background,
                        dark = dark,
                        widthDp = dimensions.widthDp,
                        heightDp = dimensions.heightDp,
                    )
                    assertTrue(bitmap.width > 0)
                    assertTrue(bitmap.height > 0)
                }
            }
        }
    }

    @Test
    fun renderedInteriorMaintainsTextContrastForEveryStyleAndAppearance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        WidgetBackground.entries.forEach { background ->
            listOf(false, true).forEach { dark ->
                val palette = WidgetPaletteResolver.resolve(background, dark)
                val foregrounds = listOf(
                    palette.primaryTextColor,
                    palette.secondaryTextColor,
                    palette.accentTextColor,
                )
                WidgetPreviewSizing.orderedSizes.forEach { size ->
                    val dimensions = WidgetPreviewSizing.representative(size)
                    val bitmap = WidgetBackgroundRenderer.render(
                        context = context,
                        background = background,
                        dark = dark,
                        widthDp = dimensions.widthDp,
                        heightDp = dimensions.heightDp,
                    )
                    val pixelsPerDp = minOf(
                        bitmap.width.toFloat() / dimensions.widthDp,
                        bitmap.height.toFloat() / dimensions.heightDp,
                    )
                    val cornerSafeInset = (21f * pixelsPerDp).roundToInt().coerceAtLeast(1)
                    val xStart = cornerSafeInset.coerceAtMost(bitmap.width / 2)
                    val xEnd = (bitmap.width - 1 - cornerSafeInset).coerceAtLeast(bitmap.width / 2)
                    val yStart = cornerSafeInset.coerceAtMost(bitmap.height / 2)
                    val yEnd = (bitmap.height - 1 - cornerSafeInset).coerceAtLeast(bitmap.height / 2)
                    val xStep = maxOf(1, (xEnd - xStart) / 16)
                    val yStep = maxOf(1, (yEnd - yStart) / 12)

                    var y = yStart
                    while (y <= yEnd) {
                        var x = xStart
                        while (x <= xEnd) {
                            val pixel = bitmap.getPixel(x, y)
                            if (Color.alpha(pixel) > 0) {
                                foregrounds.forEach { foreground ->
                                    val ratio = contrast(foreground, pixel)
                                    assertTrue(
                                        "$background dark=$dark size=$size x=$x y=$y contrast=$ratio",
                                        ratio >= 4.5,
                                    )
                                }
                            }
                            x += xStep
                        }
                        y += yStep
                    }
                }
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
            WidgetThemeResolver.resolve(
                forcedContext,
                WidgetAppearance.SYSTEM,
                WidgetBackground.MONOGRAM,
            ).dark,
        )
    }

    private fun contrast(first: Int, second: Int): Double {
        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)
        return (max(firstLuminance, secondLuminance) + 0.05) /
            (min(firstLuminance, secondLuminance) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color shr shift) and 0xFF) / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }

        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
