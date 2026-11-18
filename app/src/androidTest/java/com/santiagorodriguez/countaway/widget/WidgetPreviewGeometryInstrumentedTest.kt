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
                arrayListOf(SizeF(0f, 90f), SizeF(59.6f, 105.2f),
                    SizeF(127.2f, 51.1f), SizeF(59.6f, 105.2f)))
        }
        assertEquals(WidgetPreviewDimensions(60, 105),
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
                    if (name == "time") {
                        val count = root.findViewById<android.widget.TextView>(
                            com.santiagorodriguez.countaway.R.id.timeWidgetPickerPreviewCount)
                        val layout = requireNotNull(count.layout)
                        val availableWidth = count.width - count.totalPaddingLeft - count.totalPaddingRight
                        val availableHeight = count.height - count.compoundPaddingTop - count.compoundPaddingBottom
                        assertTrue("Timer exceeds available height at font scale $scale",
                            layout.height <= availableHeight)
                        assertTrue("Timer exceeds available width at font scale $scale",
                            layout.getLineWidth(0) <= availableWidth)
                    }
                }
            }
        }
    }

    @Test fun placeholderRestoresTitleSizeAfterFittedEvent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val config = android.content.res.Configuration(
                instrumentation.targetContext.resources.configuration).apply { fontScale = 1f }
            val context = instrumentation.targetContext.createConfigurationContext(config)
            val container = android.widget.FrameLayout(context)
            val frame = android.widget.FrameLayout(context)
            container.addView(frame)
            val controller = WidgetPreviewController(context, container, frame)
            controller.renderStyle(WidgetStyleSelection(WidgetAppearance.DARK, WidgetBackground.CLASSIC),
                WidgetPreviewDimensions(90, 90))
            val event = com.santiagorodriguez.countaway.model.CountdownEvent(
                "preview-reset", "ExtraordinarilyLongSingleWord",
                java.time.LocalDate.of(2026, 11, 20),
                com.santiagorodriguez.countaway.model.EventType.EVENT,
                createdAt = java.time.Instant.EPOCH)
            controller.renderEvent(WidgetEventContentFactory.from(event, java.time.LocalDate.of(2026, 11, 16)))
            val fittedTitle = frame.findViewById<android.widget.TextView>(
                com.santiagorodriguez.countaway.R.id.widgetTitle)
            val normalPixels = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_SP, WidgetCompactTitleFitter.normalSizeSp,
                context.resources.displayMetrics)
            assertTrue("Fixture must actually reduce the title", fittedTitle.textSize < normalPixels)
            controller.renderPlaceholder("Choose an event", "Tap to configure")
            val title = frame.findViewById<android.widget.TextView>(
                com.santiagorodriguez.countaway.R.id.widgetTitle)
            val expected = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_SP, WidgetCompactTitleFitter.normalSizeSp,
                context.resources.displayMetrics)
            assertEquals(expected, title.textSize, 0.01f)
        }
    }

    @Test fun daysPreviewMatchesProductionAcrossLocalesSizesAndLargeFonts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val today = java.time.LocalDate.of(2026, 11, 16)
            for (language in listOf("en", "es", "ca")) for (scale in listOf(1f, 2f)) {
                val config = android.content.res.Configuration(
                    instrumentation.targetContext.resources.configuration).apply {
                    fontScale = scale
                    setLocale(java.util.Locale.forLanguageTag(language))
                }
                val context = instrumentation.targetContext.createConfigurationContext(config)
                for (dimensions in listOf(WidgetPreviewDimensions(57, 102),
                    WidgetPreviewDimensions(90, 90), WidgetPreviewDimensions(180, 50),
                    WidgetPreviewDimensions(240, 180))) {
                    val style = WidgetStyleSelection(WidgetAppearance.DARK, WidgetBackground.MONOGRAM)
                    val event = com.santiagorodriguez.countaway.model.CountdownEvent(
                        "preview-parity", "Nacimiento",
                        today.minusDays(11949), com.santiagorodriguez.countaway.model.EventType.EVENT,
                        createdAt = java.time.Instant.EPOCH)
                    val frame = android.widget.FrameLayout(context)
                    val container = android.widget.FrameLayout(context).apply { addView(frame) }
                    val controller = WidgetPreviewController(context, container, frame)
                    controller.renderStyle(style, dimensions)
                    controller.renderEvent(WidgetEventContentFactory.from(event, today))
                    val production = WidgetPreviewFactory.remoteViews(context, event, style, today, dimensions)
                        .apply(context, android.widget.FrameLayout(context))
                    for (id in listOf(com.santiagorodriguez.countaway.R.id.widgetTitle,
                        com.santiagorodriguez.countaway.R.id.widgetCount,
                        com.santiagorodriguez.countaway.R.id.widgetUnit,
                        com.santiagorodriguez.countaway.R.id.widgetDate)) {
                        val previewText = frame.findViewById<android.widget.TextView>(id)
                        val productionText = production.findViewById<android.widget.TextView>(id)
                        val tag = "$language font=$scale size=$dimensions id=$id"
                        assertEquals(tag, productionText.text.toString(), previewText.text.toString())
                        assertEquals(tag, productionText.visibility, previewText.visibility)
                        assertEquals(tag, productionText.maxLines, previewText.maxLines)
                        assertEquals(tag, productionText.currentTextColor, previewText.currentTextColor)
                        if (id == com.santiagorodriguez.countaway.R.id.widgetTitle)
                            assertEquals(tag, productionText.textSize, previewText.textSize, 0.01f)
                    }
                    val icon = com.santiagorodriguez.countaway.R.id.widgetIcon
                    assertEquals(production.findViewById<android.view.View>(icon).visibility,
                        frame.findViewById<android.view.View>(icon).visibility)
                }
            }
        }
    }

    private data class PickerSample(val name: String, val layout: Int, val width: Int, val height: Int)
}
