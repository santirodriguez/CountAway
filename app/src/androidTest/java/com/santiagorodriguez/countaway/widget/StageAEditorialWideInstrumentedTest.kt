package com.santiagorodriguez.countaway.widget

import android.graphics.Rect
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import org.junit.Assert.*
import org.junit.Test

/** Debug-only real Android glyph and size checks. Not a ticking clock. */
class StageAEditorialWideInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext

    @Test fun wideEditorialKeepsAllFourUnitsReadable() = instrumentation.runOnMainSync {
        val cases = listOf(Triple(240, 180, 1f), Triple(240, 180, 1.3f),
            Triple(240, 180, 2f), Triple(280, 210, 1.3f),
            Triple(280, 210, 2f), Triple(320, 250, 2f))
        for ((width, height, scale) in cases) {
            val context = StageAProbeEvidence.context(scale)
            val remote = RemoteViews(app.packageName, R.layout.time_widget_stage_a_editorial_wide)
            remote.setImageViewBitmap(R.id.probeBackground,
                WidgetBackgroundRenderer.render(context, WidgetBackground.HORIZON,
                    true, width, height))
            val title = "Un viaje extraordinariamente importante a Barcelona"
            remote.setTextViewText(R.id.probeTitle, title)
            val ids = listOf(R.id.probeDays, R.id.probeDayUnit,
                R.id.probeHours, R.id.probeMinutes, R.id.probeSeconds, R.id.probeDate)
            val labels = listOf("3", "d", "07h", "12m", "05s", "18 oct. · 16:30")
            ids.zip(labels).forEach { (id, label) -> remote.setTextViewText(id, label) }
            val root = remote.apply(context, FrameLayout(context)) as FrameLayout
            root.contentDescription = title + ", 3 days, 7 hours, 12 minutes, 5 seconds"
            StageAProbeEvidence.layout(root, context, width, height)
            val name = "editorial-wide-" + width + "x" + height + "-font" + (scale * 100).toInt()
            StageAProbeEvidence.capture(root, name + ".png")
            val rects = ids.map { id ->
                val tv = root.findViewById<TextView>(id)
                assertTrue(tv.width > 0 && tv.height > 0)
                val lines = tv.layout!!
                assertEquals(1, lines.lineCount)
                assertEquals(0, lines.getEllipsisCount(0))
                val ink = Rect()
                val value = tv.text.toString()
                tv.paint.getTextBounds(value, 0, value.length, ink)
                ink.offset(tv.totalPaddingLeft + lines.getLineLeft(0).toInt(), tv.baseline)
                assertTrue(name + ": clipped " + value + " " + ink,
                    Rect(0, 0, tv.width, tv.height).contains(ink))
                root.offsetDescendantRectToMyCoords(tv, ink)
                assertTrue(name + ": outside widget " + value,
                    ink.left >= 0 && ink.top >= 0 &&
                        ink.right <= root.width && ink.bottom <= root.height)
                StageAProbeEvidence.report(name + " " + value + " " + ink + "\n")
                ink
            }
            for (a in rects.indices) for (b in a + 1 until rects.size) {
                assertFalse(name + ": overlapping glyphs", Rect.intersects(rects[a], rects[b]))
            }
        }
    }
}
