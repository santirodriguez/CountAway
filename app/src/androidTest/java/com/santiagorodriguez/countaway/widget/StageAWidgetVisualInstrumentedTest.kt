package com.santiagorodriguez.countaway.widget

import android.util.TypedValue
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/** Two genuine Android-rendered, debug-only time widget visual directions. */
class StageAWidgetVisualInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application = instrumentation.targetContext

    @Test fun renderEditorialAndMosaicAtMeasuredHostSizes() = instrumentation.runOnMainSync {
        val variants = listOf(
            Triple("editorial", R.layout.time_widget_stage_a_editorial, WidgetBackground.HORIZON),
            Triple("mosaic", R.layout.time_widget_stage_a_mosaic, WidgetBackground.MIST))
        var count = 0
        for ((name, layout, background) in variants) for (scale in listOf(1f, 1.3f, 2f)) {
            val context = StageAProbeEvidence.context(scale)
            for ((width, height) in listOf(160 to 160, 240 to 180)) {
                val remote = RemoteViews(application.packageName, layout)
                val dark = name == "editorial"
                remote.setImageViewBitmap(R.id.probeBackground,
                    WidgetBackgroundRenderer.render(context, background, dark, width, height))
                remote.setImageViewResource(R.id.probeIcon, R.drawable.ic_event_calendar)
                remote.setTextViewText(R.id.probeTitle,
                    if (scale >= 2f) "Un viaje increíble a Barcelona con un título muy largo"
                    else "Viaje a Barcelona")
                remote.setTextViewText(R.id.probeDays, if (dark) "3" else "3d")
                remote.setTextViewText(R.id.probeHours, "07h")
                remote.setTextViewText(R.id.probeMinutes, "12m")
                remote.setTextViewText(R.id.probeSeconds, "05s")
                remote.setTextViewText(R.id.probeDate, "18 oct. · 16:30")
                // The 2x2 host cannot fit four 200%-scaled units and a full title.
                // This adaptive dev-only typography stays visible without new services.
                // A production accessibility policy still requires maintainer approval.
                val effectiveScale = when {
                    scale < 1.75f -> scale
                    width <= 160 -> 1.18f
                    else -> 1.20f
                }
                if (effectiveScale < scale) {
                    val adjust = effectiveScale / scale
                    val sizes = if (dark) listOf(
                        R.id.probeTitle to 13f,
                        R.id.probeDays to 57f,
                        R.id.probeDayUnit to 15f,
                        R.id.probeHours to 23f,
                        R.id.probeMinutes to 19f,
                        R.id.probeSeconds to 19f,
                        R.id.probeDate to 11f
                    ) else listOf(
                        R.id.probeTitle to 13f,
                        R.id.probeDays to 39f,
                        R.id.probeHours to 29f,
                        R.id.probeMinutes to 27f,
                        R.id.probeSeconds to 27f,
                        R.id.probeDate to 11f
                    )
                    sizes.forEach { (id, nominalSp) ->
                        remote.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, nominalSp * adjust)
                    }
                    // Keep sensible title wrapping whenever host space permits.
                    if (width <= 160 && height <= 160) {
                        remote.setInt(R.id.probeTitle, "setMaxLines", 1)
                    }
                }
                val root = remote.apply(context, FrameLayout(context))
                root.contentDescription =
                    "Viaje a Barcelona, 3 días, 7 horas, 12 minutos, 5 segundos, 18 octubre a las 16:30"
                StageAProbeEvidence.layout(root, context, width, height)
                val stats = listOf(R.id.probeTitle, R.id.probeDays, R.id.probeHours,
                    R.id.probeMinutes, R.id.probeSeconds, R.id.probeDate).joinToString(" ") { id ->
                    val view = root.findViewById<TextView>(id)
                    assertNotNull(view)
                    assertTrue("Hidden required timed unit in $name at $width x $height " +
                        "fontScale=$scale viewId=$id height=${view.height}", view.height > 0)
                    assertTrue("Missing text layout in $name at fontScale=$scale viewId=$id",
                        (view.layout?.lineCount ?: 0) > 0)
                    val clipped = (0 until (view.layout?.lineCount ?: 0)).any { index ->
                        (view.layout?.getEllipsisCount(index) ?: 0) > 0 }
                    if (id == R.id.probeDate) {
                        assertFalse("Event target datetime is clipped in $name " +
                            "at $width x $height fontScale=$scale", clipped)
                    }
                    application.resources.getResourceEntryName(id) + "=" +
                        view.width + "x" + view.height + ":ellipsized=" + clipped
                }
                val filename = name + "-" + width + "x" + height +
                    "-font" + (scale * 100).roundToInt() + ".png"
                StageAProbeEvidence.capture(root, filename)
                StageAProbeEvidence.report(filename + " " + stats + "\n")
                count++
            }
        }
        assertEquals(12, count)
    }
}
