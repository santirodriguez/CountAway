package com.santiagorodriguez.countaway.widget

import android.graphics.Rect
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import org.junit.Assert.*
import org.junit.Test

/** Stage A typography and host-size probe; no running countdown. */
class StageAEditorialPolishInstrumentedTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun editorialUsesAvailableTitleSpaceWithoutClippingNumbers() =
        instrumentation.runOnMainSync {
            val cases = listOf(
                Triple(240, 180, 1f),
                Triple(240, 180, 1.3f),
                Triple(240, 180, 2f),
                Triple(280, 210, 1.3f),
                Triple(280, 210, 2f),
                Triple(320, 250, 2f)
            )
            for ((width, height, fontScale) in cases) {
                val context = StageAProbeEvidence.context(fontScale)
                val remote = RemoteViews(app.packageName,
                    R.layout.time_widget_stage_a_editorial_wide)
                remote.setImageViewBitmap(R.id.probeBackground,
                    WidgetBackgroundRenderer.render(context,
                        WidgetBackground.HORIZON, true, width, height))
                val title = "Un viaje extraordinariamente importante a Barcelona"
                remote.setTextViewText(R.id.probeTitle, title)
                val allowedTitleLines = if (width <= 240 && fontScale >= 1.8f) 1 else 2
                remote.setInt(R.id.probeTitle, "setMaxLines", allowedTitleLines)
                val ids = listOf(R.id.probeDays, R.id.probeDayUnit,
                    R.id.probeHours, R.id.probeMinutes, R.id.probeSeconds, R.id.probeDate)
                val values = listOf("3", "d", "07h", "12m", "05s", "18 oct. · 16:30")
                ids.zip(values).forEach { (id, value) -> remote.setTextViewText(id, value) }
                val root = remote.apply(context, FrameLayout(context)) as FrameLayout
                root.contentDescription =
                    "$title, 3 days, 7 hours, 12 minutes, 5 seconds, October 18 at 16:30"
                StageAProbeEvidence.layout(root, context, width, height)
                val scenario = "editorial-polished-" + width + "x" + height +
                    "-font" + (fontScale * 100).toInt()
                StageAProbeEvidence.capture(root, scenario + ".png")
                val titleView = root.findViewById<TextView>(R.id.probeTitle)
                assertNotNull(titleView)
                assertTrue(scenario + ": missing title", titleView.height > 0)
                val titleLineCount = titleView.layout?.lineCount ?: 0
                if (allowedTitleLines == 2) {
                    assertEquals(scenario + ": available second title line not used",
                        2, titleLineCount)
                }
                StageAProbeEvidence.report(scenario + " title=" + titleLineCount +
                    "/" + allowedTitleLines + "\n")
                val titleRect = Rect(0, 0, titleView.width, titleView.height)
                root.offsetDescendantRectToMyCoords(titleView, titleRect)
                val inkRects = ids.map { id ->
                    val view = root.findViewById<TextView>(id)
                    assertNotNull(view)
                    val layout = view.layout
                    assertNotNull(layout)
                    assertEquals(scenario + ": wrapped time unit " + id,
                        1, layout!!.lineCount)
                    assertEquals(scenario + ": ellipsized time unit " + id,
                        0, layout.getEllipsisCount(0))
                    val ink = Rect()
                    val value = view.text.toString()
                    view.paint.getTextBounds(value, 0, value.length, ink)
                    ink.offset(view.totalPaddingLeft + layout.getLineLeft(0).toInt(),
                        view.baseline)
                    assertTrue(scenario + ": glyph clipped by TextView " + value,
                        Rect(0, 0, view.width, view.height).contains(ink))
                    root.offsetDescendantRectToMyCoords(view, ink)
                    assertTrue(scenario + ": glyph outside widget " + value,
                        Rect(0, 0, root.width, root.height).contains(ink))
                    var parent = view.parent
                    while (parent is ViewGroup && parent !== root) {
                        if (parent.clipChildren) {
                            val bounds = Rect(0, 0, parent.width, parent.height)
                            root.offsetDescendantRectToMyCoords(parent, bounds)
                            assertTrue(scenario + ": parent clips " + value +
                                " ink=" + ink + " parent=" + bounds, bounds.contains(ink))
                        }
                        parent = parent.parent
                    }
                    assertFalse(scenario + ": time glyph overlaps the title " + value,
                        Rect.intersects(ink, titleRect))
                    StageAProbeEvidence.report(scenario + " " + value +
                        " rect=" + ink + "\n")
                    ink
                }
                for (first in inkRects.indices) for (other in first + 1 until inkRects.size) {
                    assertFalse(scenario + ": overlapping time/date glyphs",
                        Rect.intersects(inkRects[first], inkRects[other]))
                }
            }
        }
}
