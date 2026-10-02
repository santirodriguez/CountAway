package com.santiagorodriguez.countaway.ui

import android.content.res.Configuration
import android.graphics.Rect
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class HomeCountTileInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val today = LocalDate.of(2026, 10, 2)

    @Test fun everyStatusUsesTheSameTileSizeAndTrailingAlignment() = instrumentation.runOnMainSync {
        val base = instrumentation.targetContext
        for (language in listOf("en", "es", "ca")) for (scale in listOf(1f, 1.3f, 2f)) {
            for (dark in listOf(false, true)) for (widthDp in listOf(320, 360, 411)) {
                val config = Configuration(base.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(language))
                    fontScale = scale
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                val context = ContextThemeWrapper(base.createConfigurationContext(config), base.applicationInfo.theme)
                val adapter = CountdownEventAdapter(context, {}, {}, { _, _ -> })
                val events = listOf(event(22), event(0), event(-1), event(22, CountMode.COUNT_UP),
                    event(-17, CountMode.COUNT_UP), event(-1765, CountMode.COUNT_UP),
                    event(-46000, CountMode.COUNT_UP), event(82).copy(title = "Family birthday"),
                    event(22).copy(title = "A long event title that needs another line"))
                adapter.submit(events, today)
                var expectedSize: Pair<Int, Int>? = null
                var expectedRight: Int? = null
                for (index in events.indices) {
                    val row = adapter.getView(index, null, FrameLayout(context))
                    val width = (widthDp * context.resources.displayMetrics.density).roundToInt()
                    repeat(2) {
                        row.forceLayout()
                        row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                        row.layout(0, 0, row.measuredWidth, row.measuredHeight)
                    }
                    val tile = row.findViewById<View>(R.id.eventCountTile)
                    val status = row.findViewById<TextView>(R.id.eventStatus)
                    val menu = row.findViewById<View>(R.id.eventActions)
                    val size = tile.width to tile.height
                    if (expectedSize == null) { expectedSize = size; expectedRight = tile.right }
                    assertEquals("Tile size depends on the event", expectedSize, size)
                    assertEquals(requireNotNull(expectedRight).toInt(), tile.right)
                    assertTrue(tile.right <= menu.left)
                    assertEquals((tile.top + tile.bottom) / 2f, (menu.top + menu.bottom) / 2f, 1.1f)
                    assertTrue(Rect(tile.left, tile.top, tile.right, tile.bottom)
                        .contains(Rect(status.left, status.top, status.right, status.bottom)))
                    assertEquals(events[index].title, row.findViewById<TextView>(R.id.eventTitle).text.toString())
                    val transformed = status.layout.text
                    assertEquals(status.text.length, transformed.length)
                    assertEquals(status.text.toString(), transformed.toString().replace('\n', ' '))
                    assertTrue(transformed is Spanned)
                    assertTrue(status.layout.height <= status.height - status.compoundPaddingTop - status.compoundPaddingBottom)
                    for (line in 0 until status.lineCount) {
                        assertEquals(0, status.layout.getEllipsisCount(line))
                        assertTrue(status.layout.getLineMax(line) <= status.width - status.compoundPaddingLeft - status.compoundPaddingRight + 1f)
                    }
                }
            }
        }
    }

    @Test fun localizedDirectionAndSignsRemainExplicitInTheRenderedLines() = instrumentation.runOnMainSync {
        val cases = mapOf(
            "22 days" to "22\ndays", "+17 days" to "+17\ndays", "1 day ago" to "1\nday ago",
            "In 22 days" to "In\n22\ndays", "+1,765 days" to "+1,765\ndays", "Today" to "Today",
            "Hace 1 día" to "Hace\n1\ndía", "En 22 días" to "En\n22\ndías",
            "Fa 1 dia" to "Fa\n1\ndia", "D’aquí a 22 dies" to "D’aquí a\n22\ndies",
            "+1.765 dies" to "+1.765\ndies", "Hoy" to "Hoy", "Avui" to "Avui",
        )
        val view = HomeCountTextView(instrumentation.targetContext)
        view.prepareForWidth(400)
        for ((source, expected) in cases) {
            view.text = source
            val visual = view.transformationMethod.getTransformation(view.text, view) as Spanned
            assertEquals(expected, visual.toString())
            assertEquals(source, view.text.toString())
            assertTrue(visual.getSpans(0, visual.length, AbsoluteSizeSpan::class.java).isNotEmpty())
        }
    }

    private fun event(offset: Int, mode: CountMode = CountMode.COUNT_DOWN) = CountdownEvent(
        "$offset-$mode", "Trip", today.plusDays(offset.toLong()), EventType.EVENT,
        createdAt = Instant.EPOCH, countMode = mode,
    )
}
