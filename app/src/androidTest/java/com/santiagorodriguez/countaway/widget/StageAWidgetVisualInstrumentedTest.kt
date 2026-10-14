package com.santiagorodriguez.countaway.widget

import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
                val root = remote.apply(context, FrameLayout(context))
                root.contentDescription =
                    "Viaje a Barcelona, 3 días, 7 horas, 12 minutos, 5 segundos, 18 octubre a las 16:30"
                StageAProbeEvidence.layout(root, context, width, height)
                val stats = listOf(R.id.probeTitle, R.id.probeDays, R.id.probeHours,
                    R.id.probeMinutes, R.id.probeSeconds, R.id.probeDate).joinToString(" ") { id ->
                    val view = root.findViewById<TextView>(id)
                    assertNotNull(view)
                    val clipped = (0 until (view.layout?.lineCount ?: 0)).any { index ->
                        (view.layout?.getEllipsisCount(index) ?: 0) > 0 }
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
