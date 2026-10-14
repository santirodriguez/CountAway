package com.santiagorodriguez.countaway.widget

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LargeTextSurfaceDiagnosticsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val actions = intArrayOf(R.id.home_event_widget, R.id.home_event_edit,
        R.id.home_event_duplicate, R.id.home_event_share, R.id.home_event_delete)

    @Test fun contextualActionTextIsMeasuredAtCompactWidthsAndLargeFonts() = instrumentation.runOnMainSync {
        val failures = mutableListOf<String>()
        for (language in listOf("en", "es", "ca")) for (scale in listOf(1f, 1.3f, 2f)) {
            val original = instrumentation.targetContext
            val config = Configuration(original.resources.configuration).apply {
                fontScale = scale
                screenWidthDp = 320
                setLocale(Locale.forLanguageTag(language))
            }
            val context = ContextThemeWrapper(original.createConfigurationContext(config), R.style.Theme_CountAway)
            val root = LayoutInflater.from(context).inflate(R.layout.event_actions_popup, null) as ScrollView
            val density = context.resources.displayMetrics.density
            val widthPx = (236 * density).toInt()
            val heightPx = (640 * density).toInt()
            root.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.AT_MOST))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            val content = root.getChildAt(0)
            for (id in actions) {
                val row = root.findViewById<LinearLayout>(id)
                val text = row.getChildAt(1) as TextView
                val layout = text.layout
                val description = "$language font=$scale action=${context.resources.getResourceEntryName(id)} " +
                    "row=${row.width}x${row.height} text=${text.width}x${text.height} lines=${layout?.lineCount}"
                val lineBottom = if (layout == null || layout.lineCount == 0) 0 else layout.getLineBottom(layout.lineCount - 1)
                if (layout == null || lineBottom > text.height - text.paddingTop - text.paddingBottom + 1 ||
                    text.top < 0 || text.bottom > row.height || row.height < (48 * density).toInt() ||
                    row.contentDescription.isNullOrBlank()) failures += description
            }
            val suffix = "actions-$language-$scale"
            val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(image))
            val file = File(context.filesDir, "layout-evidence/$suffix.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
            assertTrue("Contextual actions must remain scrollable", content.height > 0 && content.parent == root)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
