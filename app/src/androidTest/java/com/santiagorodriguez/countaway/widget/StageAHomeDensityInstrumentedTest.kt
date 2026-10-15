package com.santiagorodriguez.countaway.widget

import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.ui.HomeEventRow
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/** Prototype Home presentation using the current actual HomeEventRow class. */
class StageAHomeDensityInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun compareCompactTimedRowsWithLongTitles() = instrumentation.runOnMainSync {
        val titles = listOf("Viaje a Barcelona",
            "Reunión importante de trabajo con un nombre exageradamente largo pero intacto",
            "Cumpleaños de Sofía",
            "Un titulo sinpalabrasconunasecuenciaexageradamentelargaparamedir",
            "Evento de ejemplo")
        for (scale in listOf(1f, 2f)) {
            val context = StageAProbeEvidence.context(scale)
            val rows = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(context.getColor(R.color.background))
            }
            titles.forEach { title ->
                val row = LayoutInflater.from(context)
                    .inflate(R.layout.item_countdown, rows, false) as HomeEventRow
                row.findViewById<TextView>(R.id.eventTitle).apply {
                    val cap = if (scale > 1.5f) 1.25f / scale else 1f
                    if (cap < 1f) setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * cap)
                    val maxToken = 140f * context.resources.displayMetrics.density
                    text = title.split(' ').joinToString(" ") { token ->
                        if (paint.measureText(token) > maxToken)
                            TextUtils.ellipsize(token, paint, maxToken, TextUtils.TruncateAt.END).toString()
                        else token
                    }
                    contentDescription = title
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
                row.findViewById<TextView>(R.id.eventMeta).apply {
                    if (scale > 1.5f) setTextSize(
                        TypedValue.COMPLEX_UNIT_SP, 13f * 1.25f / scale)
                    text = "18 oct. · 16:30\n7h · 12m · 05s"
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
                row.findViewById<TextView>(R.id.eventStatus).text = "3"
                row.findViewById<ImageView>(R.id.eventIcon)
                    .setImageResource(R.drawable.ic_event_calendar)
                rows.addView(row, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            val pixels = (320 * context.resources.displayMetrics.density).roundToInt()
            rows.measure(View.MeasureSpec.makeMeasureSpec(pixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            rows.layout(0, 0, pixels, rows.measuredHeight)
            StageAProbeEvidence.capture(rows,
                "home-320dp-font" + (scale * 100).roundToInt() + ".png")
            val measuredDp = (0 until rows.childCount).map {
                (rows.getChildAt(it).height / context.resources.displayMetrics.density).roundToInt()
            }
            StageAProbeEvidence.report("home_font=" + scale +
                " row_heights_dp=" + measuredDp + "\n")
            assertTrue(rows.height > 0)
            val maximumDp = if (scale > 1.5f) 175 else 135
            assertTrue("Home rows exceeded compactness budget at fontScale=$scale: $measuredDp",
                measuredDp.all { it <= maximumDp })
        }
    }
}
