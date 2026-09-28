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
}
