package com.santiagorodriguez.countaway.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test

class WidgetPreviewGeometryInstrumentedTest {
    @Test fun previewsKeepOrientationPairsAndDefaultSpans() {
        assertEquals(WidgetPreviewDimensions(90, 90),
            WidgetPreviewGeometry.dimensions(Bundle(), false, false))
        assertEquals(WidgetPreviewDimensions(150, 50),
            WidgetPreviewGeometry.dimensions(Bundle(), false, true))
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 57)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 127)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 51)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 102)
        }
        assertEquals(WidgetPreviewDimensions(57, 102),
            WidgetPreviewGeometry.dimensions(options, false, false))
        assertEquals(WidgetPreviewDimensions(127, 51),
            WidgetPreviewGeometry.dimensions(options, true, false))
    }

}
