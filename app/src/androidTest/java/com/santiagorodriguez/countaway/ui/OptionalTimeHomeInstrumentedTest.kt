package com.santiagorodriguez.countaway.ui

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class OptionalTimeHomeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val base = CountdownEvent(
        id = "date-only", title = "A very long event title about an upcoming trip with more words",
        date = LocalDate.of(2026, 10, 18), type = EventType.TRIP,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"))

    @Test fun timedRowIsCompactAndRecycledDateOnlyRowDoesNotGainTime() =
        instrumentation.runOnMainSync {
            val timed = base.copy(
                id = "timed", eventTime = LocalTime.of(16, 30),
                eventZone = ZoneId.of("America/Argentina/Buenos_Aires"))
            val adapter = CountdownEventAdapter(context)
            adapter.submit(listOf(timed, base), LocalDate.of(2026, 10, 14))
            val parent = FrameLayout(context)
            val row = adapter.getView(0, null, parent) as HomeEventRow
            val detail = row.findViewById<TextView>(R.id.eventTimeDetail)
            assertEquals(View.VISIBLE, detail.visibility)
            assertTrue(detail.text.toString().contains("h"))
            assertTrue(detail.text.toString().contains("m"))
            assertTrue(detail.text.toString().contains("s"))
            val width = (320f * context.resources.displayMetrics.density).toInt()
            row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            row.layout(0, 0, width, row.measuredHeight)
            assertTrue("Timed row unexpectedly tall", row.height <=
                (175f * context.resources.displayMetrics.density).toInt())
            assertTrue(row.findViewById<View>(R.id.eventCountTile).width > 0)
            adapter.getView(1, row, parent)
            assertEquals(View.GONE, row.findViewById<View>(R.id.eventTimeDetail).visibility)
        }
}
