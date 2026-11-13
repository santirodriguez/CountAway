package com.santiagorodriguez.countaway.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetHostGeometryTest {
    @Test fun orientationExtremaArePairedInsteadOfMixed() {
        val classic = WidgetHostGeometry.legacy(
            WidgetLegacyOptions(57, 127, 51, 102),
            WidgetLegacyDefaults(56, 120, 50, 102),
        )
        assertEquals(WidgetPreviewDimensions(57, 102), classic.portrait)
        assertEquals(WidgetPreviewDimensions(127, 51), classic.landscape)
        val timed = WidgetHostGeometry.legacy(
            WidgetLegacyOptions(130, 269, 51, 102),
            WidgetLegacyDefaults(150, 269, 50, 102),
        )
        assertEquals(WidgetPreviewDimensions(130, 102), timed.portrait)
        assertEquals(WidgetPreviewDimensions(269, 51), timed.landscape)
    }

    @Test fun emptyAndInconsistentLegacyOptionsUseSafeDefaults() {
        val defaults = WidgetLegacyDefaults(56, 120, 50, 102)
        val expected = WidgetOrientationDimensions(
            WidgetPreviewDimensions(56, 102), WidgetPreviewDimensions(120, 50),
        )
        assertEquals(expected, WidgetHostGeometry.legacy(
            WidgetLegacyOptions(null, null, null, null), defaults,
        ))
        assertEquals(expected, WidgetHostGeometry.legacy(
            WidgetLegacyOptions(127, 57, 102, 51), defaults,
        ))
    }

    @Test fun fractionalResponsiveSizesArePreservedAndMalformedOnesDropped() {
        val first = WidgetHostDimensions(57.25f, 102.75f)
        val second = WidgetHostDimensions(127.5f, 51.25f)
        assertEquals(listOf(first, second), WidgetHostGeometry.responsive(listOf(
            WidgetHostDimensions(Float.NaN, 80f),
            WidgetHostDimensions(Float.POSITIVE_INFINITY, 50f),
            WidgetHostDimensions(0f, 80f),
            first, first, second,
            WidgetHostDimensions(9_999f, 9_999f),
        ), 2.625f))
    }

    @Test fun variantAndBitmapBudgetsBoundPayloadAndRetainExtremes() {
        val sizes = (1..70).map { WidgetHostDimensions(70f + it * 3, 45f + it * 4) }
        val selected = WidgetHostGeometry.responsive(sizes, 3f)
        assertTrue(selected.size <= 16)
        assertFalse(selected.isEmpty())
        assertTrue(selected.contains(sizes.first()))
        assertTrue(selected.contains(sizes.last()))
        assertEquals(selected.distinct(), selected)
        assertTrue(WidgetHostGeometry.responsive(sizes, Float.NaN).isEmpty())
    }
}
