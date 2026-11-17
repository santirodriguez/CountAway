package com.santiagorodriguez.countaway.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.os.Build
import android.util.SizeF
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
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


    @Test fun responsivePreviewUsesARealValidatedProviderSize() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 57)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 127)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 51)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 102)
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,
                arrayListOf(SizeF(Float.NaN, 90f), SizeF(57.4f, 102.2f),
                    SizeF(127.2f, 51.1f), SizeF(57.4f, 102.2f)))
        }
        assertEquals(WidgetPreviewDimensions(57, 102),
            WidgetPreviewGeometry.dimensions(options, false, false))
        assertEquals(WidgetPreviewDimensions(127, 51),
            WidgetPreviewGeometry.dimensions(options, true, false))
        options.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,
            arrayListOf(SizeF(-1f, 100f)))
        assertEquals(WidgetPreviewDimensions(57, 102),
            WidgetPreviewGeometry.dimensions(options, false, false))
    }

    @Test fun captureNativePickerSamplesForReview() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (scale in listOf(1f, 2f)) {
                val configuration = android.content.res.Configuration(
                    instrumentation.targetContext.resources.configuration).apply { fontScale = scale }
                val context = instrumentation.targetContext.createConfigurationContext(configuration)
                for ((name, layout, width, height) in listOf(
                    PickerSample("days", com.santiagorodriguez.countaway.R.layout.widget_countdown_preview, 90, 90),
                    PickerSample("time", com.santiagorodriguez.countaway.R.layout.widget_time_preview, 150, 50))) {
                    val root = android.view.LayoutInflater.from(context).inflate(layout, null)
                    val density = context.resources.displayMetrics.density
                    val w = (width * density).toInt()
                    val h = (height * density).toInt()
                    root.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, w, h)
                    val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                    root.draw(android.graphics.Canvas(bitmap))
                    val file = java.io.File(context.filesDir, "layout-evidence/p3d-picker-$name-f$scale.png")
                    file.parentFile!!.mkdirs()
                    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                    assertTrue(file.length() > 0)
                }
            }
        }
    }

    private data class PickerSample(val name: String, val layout: Int, val width: Int, val height: Int)
}
