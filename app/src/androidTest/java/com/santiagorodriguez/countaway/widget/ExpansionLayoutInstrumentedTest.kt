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
            for (scale in listOf(1f, 1.3f, 2f)) for (size in WidgetPreviewSizing.orderedSizes) {
                val config = Configuration(instrumentation.targetContext.resources.configuration).apply { fontScale = scale }
                val context = instrumentation.targetContext.createConfigurationContext(config)
                val dimensions = WidgetPreviewSizing.representative(size)
                val today = java.time.LocalDate.of(2026, 9, 28)
                val event = com.santiagorodriguez.countaway.model.CountdownEvent("layout", "A long countdown title",
                    today.plusDays(123), com.santiagorodriguez.countaway.model.EventType.CUSTOM,
                    createdAt = java.time.Instant.EPOCH)
                val views = android.widget.RemoteViews(context.packageName, WidgetLayoutResolver.layoutRes(size))
                WidgetRemoteViewsPresentation.applyEvent(context, views, WidgetEventContentFactory.from(event, today), size, scale)
                val root = views.apply(context, android.widget.FrameLayout(context))
                val density = context.resources.displayMetrics.density
                root.measure(View.MeasureSpec.makeMeasureSpec((dimensions.widthDp * density).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((dimensions.heightDp * density).toInt(), View.MeasureSpec.EXACTLY))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                val count = root.findViewById<android.widget.TextView>(R.id.widgetCount)
                val needed = count.layout?.height ?: 0
                val rect = android.graphics.Rect(0, 0, count.width, count.height)
                (root as android.view.ViewGroup).offsetDescendantRectToMyCoords(count, rect)
                val summary = "$size scale=$scale count=${count.width}x${count.height} needed=$needed rect=$rect root=${root.width}x${root.height}"
                android.util.Log.i("LayoutMeasurement", summary)
                if (count.height < needed || needed == 0 || rect.top < 0 || rect.bottom > root.height) problems += summary
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                val file = File(context.filesDir, "layout-evidence/widget-$size-$scale.png")
                file.parentFile!!.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
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
