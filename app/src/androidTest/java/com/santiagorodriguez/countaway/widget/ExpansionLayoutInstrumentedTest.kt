package com.santiagorodriguez.countaway.widget

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Locale

class ExpansionLayoutInstrumentedTest {
    @Test fun singleScrollingListRecyclesThousandEventsAndReachesFooterAtLargeFonts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for ((width, scale, language) in listOf(Triple(320, 2f, "ca"), Triple(360, 1.3f, "es"), Triple(640, 1f, "en"))) {
                val config = Configuration(instrumentation.targetContext.resources.configuration).apply {
                    fontScale = scale
                    setLocale(Locale.forLanguageTag(language))
                }
                val context = ContextThemeWrapper(instrumentation.targetContext.createConfigurationContext(config), R.style.Theme_CountAway)
                val inflater = LayoutInflater.from(context)
                val root = inflater.inflate(R.layout.activity_widget_config, null)
                val list = root.findViewById<ListView>(R.id.widgetEventList)
                list.setItemsCanFocus(true)
                val header = inflater.inflate(R.layout.widget_config_header, list, false)
                val footer = inflater.inflate(R.layout.widget_config_footer, list, false)
                list.addHeaderView(header, null, false)
                list.addFooterView(footer, null, false)
                list.adapter = ArrayAdapter(context, R.layout.item_widget_event, android.R.id.text1,
                    (0..1000).map { if (it == 0) "Next countdown" else "Event $it — September 28, 2026" })
                fun layout() {
                    val density = context.resources.displayMetrics.density
                    root.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec((640 * density).toInt(), View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                }
                layout()
                assertTrue("Rows must remain recycled", list.childCount < 50)
                list.setItemChecked(1001, true)
                list.setSelection(1001)
                layout()
                assertEquals(1001, list.checkedItemPosition)
                assertTrue(list.lastVisiblePosition >= 1001)
                list.setSelection(1002)
                layout()
                assertTrue("Footer must be reachable", footer.parent != null)
                assertTrue("Save target remains present", footer.findViewById<View>(R.id.widgetSaveButton).measuredHeight > 0)
                val out = context.filesDir.absolutePath
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                val file = File(out, "layout-evidence/config-$width-$scale-$language.png")
                file.parentFile!!.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }
    @Test fun productionWidgetCountIsMeasuredAtLargeFonts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val problems = mutableListOf<String>()
            for (scale in listOf(1f, 1.3f, 2f)) for (size in WidgetPreviewSizing.orderedSizes) for (days in listOf(-6L, 0L, 1L, 123L)) {
                val config = Configuration(instrumentation.targetContext.resources.configuration).apply { fontScale = scale }
                val context = instrumentation.targetContext.createConfigurationContext(config)
                val dimensions = WidgetPreviewSizing.representative(size)
                val today = java.time.LocalDate.of(2026, 9, 28)
                val event = com.santiagorodriguez.countaway.model.CountdownEvent("layout", "A deliberately very long countdown title for measured layout",
                    today.plusDays(days), com.santiagorodriguez.countaway.model.EventType.CUSTOM,
                    createdAt = java.time.Instant.EPOCH)
                val views = android.widget.RemoteViews(context.packageName, WidgetLayoutResolver.layoutRes(size))
                WidgetRemoteViewsPresentation.applyEvent(context, views, WidgetEventContentFactory.from(event, today), size, scale)
                val root = views.apply(context, android.widget.FrameLayout(context))
                val density = context.resources.displayMetrics.density
                root.measure(View.MeasureSpec.makeMeasureSpec((dimensions.widthDp * density).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((dimensions.heightDp * density).toInt(), View.MeasureSpec.EXACTLY))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                repeat(3) {
                    if (root.isLayoutRequested) {
                        root.measure(View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(root.height, View.MeasureSpec.EXACTLY))
                        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                    }
                }
                val count = root.findViewById<android.widget.TextView>(R.id.widgetCount)
                val needed = count.layout?.height ?: 0
                val rect = android.graphics.Rect(0, 0, count.width, count.height)
                (root as android.view.ViewGroup).offsetDescendantRectToMyCoords(count, rect)
                val summary = "$size scale=$scale days=$days count=${count.width}x${count.height} needed=$needed rect=$rect root=${root.width}x${root.height}"
                android.util.Log.i("LayoutMeasurement", summary)
                if (count.height < needed || needed == 0 || rect.top < 0 || rect.bottom > root.height) problems += summary
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                val file = File(context.filesDir, "layout-evidence/widget-$size-$scale-$days.png")
                file.parentFile!!.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            assertTrue(problems.joinToString("\n"), problems.isEmpty())
        }
    }

    @Test fun placeholderCountsFitAndMatchPreviewPresentation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val problems = mutableListOf<String>()
            var legacyClipped = 0
            for (language in listOf("en", "es", "ca")) for (scale in listOf(1f, 1.3f, 2f))
                for (size in WidgetPreviewSizing.orderedSizes) for (error in listOf(false, true)) {
                val config = Configuration(instrumentation.targetContext.resources.configuration).apply {
                    fontScale = scale
                    setLocale(Locale.forLanguageTag(language))
                }
                val context = instrumentation.targetContext.createConfigurationContext(config)
                val dimensions = WidgetPreviewSizing.representative(size)
                val title = context.getString(if (error) R.string.widget_data_newer_version else R.string.widget_no_upcoming)
                val action = context.getString(if (error) R.string.widget_open_app else R.string.widget_tap_to_configure)
                val marker = if (error) "!" else "—"
                val density = context.resources.displayMetrics.density
                for (legacy in listOf(true, false)) {
                    val views = android.widget.RemoteViews(context.packageName, WidgetLayoutResolver.layoutRes(size))
                    if (legacy) {
                        // Reproduce the previous production placeholder actions on unchanged XML.
                        views.setImageViewResource(R.id.widgetIcon, R.drawable.ic_event_calendar)
                        views.setTextViewText(R.id.widgetTitle, title)
                        views.setTextViewText(R.id.widgetCount, marker)
                        views.setTextViewText(R.id.widgetUnit, action)
                        views.setTextViewText(R.id.widgetDate, "")
                        views.setTextViewText(R.id.widgetMilestone, "")
                        views.setViewVisibility(R.id.widgetMilestone, View.GONE)
                    } else {
                        WidgetRemoteViewsPresentation.applyPlaceholder(views, size, scale, title, action, marker)
                    }
                    val root = views.apply(context, android.widget.FrameLayout(context))
                    fun layout() {
                        root.measure(View.MeasureSpec.makeMeasureSpec((dimensions.widthDp * density).toInt(), View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec((dimensions.heightDp * density).toInt(), View.MeasureSpec.EXACTLY))
                        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                    }
                    layout()
                    repeat(3) { if (root.isLayoutRequested) layout() }
                    val count = root.findViewById<android.widget.TextView>(R.id.widgetCount)
                    val needed = count.layout?.height ?: 0
                    val rect = android.graphics.Rect(0, 0, count.width, count.height)
                    (root as android.view.ViewGroup).offsetDescendantRectToMyCoords(count, rect)
                    val summary = "placeholder legacy=$legacy $language $size scale=$scale error=$error height=${count.height} needed=$needed rect=$rect"
                    android.util.Log.i("LayoutMeasurement", summary)
                    val clipped = needed == 0 || count.height < needed || rect.top < 0 || rect.bottom > root.height
                    if (legacy) {
                        if (clipped) legacyClipped++
                    } else {
                        if (clipped) problems += summary
                        val container = android.widget.FrameLayout(context)
                        val frame = android.widget.FrameLayout(context)
                        container.addView(frame)
                        val preview = WidgetPreviewController(context, container, frame)
                        preview.renderStyle(WidgetStyleSelection(WidgetAppearance.LIGHT, WidgetBackground.CLASSIC), dimensions)
                        preview.renderPlaceholder(title, action, marker)
                        for (id in listOf(R.id.widgetTitle, R.id.widgetIcon, R.id.widgetUnit, R.id.widgetDate, R.id.widgetMilestone)) {
                            assertEquals("Preview/production visibility: $summary id=$id",
                                frame.findViewById<View>(id).visibility, root.findViewById<View>(id).visibility)
                        }
                        assertEquals(frame.findViewById<android.widget.TextView>(R.id.widgetTitle).maxLines,
                            root.findViewById<android.widget.TextView>(R.id.widgetTitle).maxLines)
                        if (language == "en") {
                            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                            root.draw(Canvas(bitmap))
                            val file = File(context.filesDir, "layout-evidence/placeholder-$size-$scale-$error.png")
                            file.parentFile!!.mkdirs()
                            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            bitmap.recycle()
                        }
                    }
                }
            }
            android.util.Log.i("LayoutMeasurement", "Legacy placeholder clipped cases=$legacyClipped")
            assertTrue("Previous placeholder clipping must be reproduced", legacyClipped > 0)
            assertTrue(problems.joinToString("\n"), problems.isEmpty())
        }
    }

    @Test fun editorSpinnerMeasurementsAtNarrowWidthAndLargeText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val config = Configuration(instrumentation.targetContext.resources.configuration).apply {
                fontScale = 2f
                setLocale(Locale.forLanguageTag("ca"))
            }
            val context = ContextThemeWrapper(instrumentation.targetContext.createConfigurationContext(config), R.style.Theme_CountAway)
            val root = LayoutInflater.from(context).inflate(R.layout.activity_editor, null)
            for ((id, label) in listOf(R.id.repeatSpinner to R.string.repeat_monthly,
                    R.id.reminderSpinner to R.string.reminder_seven_days)) {
                val spinner = root.findViewById<android.widget.Spinner>(id)
                spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, listOf(context.getString(label)))
            }
            val density = context.resources.displayMetrics.density
            root.measure(View.MeasureSpec.makeMeasureSpec((320 * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((640 * density).toInt(), View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            for (id in listOf(R.id.repeatSpinner, R.id.reminderSpinner)) {
                val spinner = root.findViewById<android.widget.Spinner>(id)
                val text = spinner.selectedView as android.widget.TextView
                val summary = "spinner=$id frame=${spinner.width}x${spinner.height} text=${text.text} textFrame=${text.width}x${text.height} needed=${text.layout.height} ellipsis=${text.layout.getEllipsisCount(0)}"
                android.util.Log.i("LayoutMeasurement", summary)
                assertTrue(summary, text.height >= text.layout.height)
                assertEquals(summary, 0, text.layout.getEllipsisCount(0))
            }
        }
    }

}
