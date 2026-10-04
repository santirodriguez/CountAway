package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventIcon
import com.santiagorodriguez.countaway.model.EventType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class HomePresentationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val today = LocalDate.of(2026, 9, 30)

    @Test fun visibleStatusesHaveShortIndependentLocalizedExpectations() = instrumentation.runOnMainSync {
        val expected = mapOf(
            "en" to listOf("In 84 days", "3 days", "2 days", "1 day", "Today", "20 days ago", "In 24 days", "+0 days", "+15 days"),
            "es" to listOf("Faltan 84 días", "3 días", "2 días", "1 día", "Hoy", "Hace 20 días", "En 24 días", "+0 días", "+15 días"),
            "ca" to listOf("Falten 84 dies", "3 dies", "2 dies", "1 dia", "Avui", "Fa 20 dies", "D’aquí a 24 dies", "+0 dies", "+15 dies"),
        )
        for ((language, labels) in expected) {
            val context = localized(language, 1f, false)
            val adapter = adapter(context)
            val fixtures = fixtures().take(9)
            adapter.submit(fixtures, today)
            val parent = FrameLayout(context)
            fixtures.indices.forEach { index ->
                val row = adapter.getView(index, null, parent)
                assertEquals("$language/$index", labels[index], row.findViewById<TextView>(R.id.eventStatus).text.toString())
                val meta = row.findViewById<TextView>(R.id.eventMeta).text.toString()
                if (fixtures[index].countMode == CountMode.COUNT_UP) {
                    assertFalse(meta.contains("elapsed"))
                    assertFalse(meta.contains("transcurrid"))
                    assertFalse(meta.contains("transcorregut"))
                }
            }
            val singular = listOf(event(-1), event(-1, CountMode.COUNT_UP), event(1, CountMode.COUNT_UP))
            val singularExpected = when (language) {
                "es" -> listOf("Hace 1 día", "+1 día", "En 1 día")
                "ca" -> listOf("Fa 1 dia", "+1 dia", "D’aquí a 1 dia")
                else -> listOf("1 day ago", "+1 day", "In 1 day")
            }
            adapter.submit(singular, today)
            singular.indices.forEach { index ->
                assertEquals(singularExpected[index], adapter.getView(index, null, parent)
                    .findViewById<TextView>(R.id.eventStatus).text.toString())
            }
        }
        val context = localized("en", 1f, false)
        val adapter = adapter(context)
        adapter.submit(listOf(event(-1763, CountMode.COUNT_UP)), today)
        assertEquals("+1,763 days", adapter.getView(0, null, FrameLayout(context))
            .findViewById<TextView>(R.id.eventStatus).text.toString())
    }

    @Test fun mixedHomeUsesNaturalHeightAndKeepsWordsAndMenuAcrossLayouts() = instrumentation.runOnMainSync {
        val problems = mutableListOf<String>()
        var checked = 0
        for (language in listOf("en", "es", "ca")) for (scale in listOf(1f, 1.3f, 2f)) {
            for (dark in listOf(false, true)) for (width in listOf(320, 411)) {
                val context = localized(language, scale, dark)
                val adapter = adapter(context)
                val events = fixtures()
                adapter.submit(events, today)
                val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                for (index in events.indices) {
                    val row = adapter.getView(index, null, list)
                    list.addView(row)
                    measure(context, row, width)
                    try {
                        for (id in listOf(R.id.eventTitle, R.id.eventMeta, R.id.eventStatus)) assertText(row.findViewById(id))
                        val menu = row.findViewById<View>(R.id.eventActions)
                        val tile = row.findViewById<View>(R.id.eventCountTile)
                        assertEquals("Visible action rail width changed", dp(context, 20), menu.width)
                        assertTrue("Action rail lost its 48dp vertical target", menu.height >= dp(context, 48))
                        assertEquals("Every menu must reach the trailing edge", row.width, menu.right)
                        assertEquals((tile.top + tile.bottom) / 2f, (menu.top + menu.bottom) / 2f, 1.1f)
                        val menuBounds = Rect(menu.left, menu.top, menu.right, menu.bottom)
                        for (id in listOf(R.id.eventTitle, R.id.eventMeta, R.id.eventStatus, R.id.eventIcon, R.id.eventArrival)) {
                            val content = row.findViewById<View>(id)
                            if (content.visibility == View.VISIBLE && content.width > 0) {
                                assertTrue("Content must clear the visible action rail", content.right <= menu.left - dp(context, 2) + 1)
                                assertFalse(Rect.intersects(menuBounds, Rect(content.left, content.top, content.right, content.bottom)))
                            }
                        }
                        assertTrue(menu.bottom <= row.height - row.paddingBottom)
                        assertEquals(events[index].title, row.findViewById<TextView>(R.id.eventTitle).text.toString())
                        checked++
                    } catch (error: AssertionError) {
                        problems += "$language/$scale/$dark/$width/$index: ${error.message}"
                    }
                }
                measure(context, list, width)
                capture(list, "home-mixed-$language-$scale-$dark-$width")
            }
        }
        File(instrumentation.targetContext.filesDir, "layout-evidence/home-presentation-matrix.txt").apply {
            parentFile!!.mkdirs()
            writeText("checked=$checked\nexpected=360\nfailures=${problems.size}\n" + problems.joinToString("\n"))
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        assertEquals(360, checked)
    }

    @Test fun mixedModesUseTinyLocalizedSectionHeadersWithoutRepeatingPerCardArrows() = instrumentation.runOnMainSync {
        val expected = mapOf(
            "en" to listOf("UPCOMING ↓", "COUNT UP ↑", "PAST ↓"),
            "es" to listOf("PRÓXIMOS ↓", "TRANSCURRIDOS ↑", "PASADOS ↓"),
            "ca" to listOf("PROPERS ↓", "TRANSCORREGUTS ↑", "PASSATS ↓"),
        )
        for ((language, labels) in expected) {
            val context = localized(language, 1f, false)
            val adapter = adapter(context)
            val events = listOf(event(5), event(-7, CountMode.COUNT_UP), event(-3))
            val sorted = com.santiagorodriguez.countaway.countdown.CountdownEventOrder.sortedForDisplay(events, today)
            adapter.submit(sorted, today)
            val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            sorted.indices.forEach { index ->
                val row = adapter.getView(index, null, list)
                list.addView(row)
                measure(context, row, 411)
                val header = row.findViewById<TextView>(R.id.eventSectionLabel)
                assertEquals(View.VISIBLE, header.visibility)
                assertEquals(labels[index], header.text.toString())
                assertTrue("Section header became visually heavy", header.textSize <= 9f * context.resources.displayMetrics.scaledDensity + 0.6f)
                assertTrue("Section cap exceeded compact budget", row.paddingTop <= dp(context, 30))
            }
            measure(context, list, 411)
            capture(list, "home-sections-$language")
        }

        val context = localized("en", 1f, false)
        val adapter = adapter(context)
        adapter.submit(listOf(event(5), event(12)), today)
        val row = adapter.getView(0, null, FrameLayout(context))
        measure(context, row, 411)
        assertEquals(View.GONE, row.findViewById<View>(R.id.eventSectionLabel).visibility)
        assertEquals(dp(context, 10), row.paddingTop)
    }

    @Test fun referenceLengthBirthdayTitleStaysOnOneLineAtNormalPhoneWidths() = instrumentation.runOnMainSync {
        val context = localized("en", 1f, false)
        val adapter = adapter(context)
        val event = CountdownEvent(
            id = "reference-birthday",
            title = "Sophia's birthday 🥳",
            date = today.plusDays(82),
            type = EventType.BIRTHDAY,
            icon = EventIcon.CAKE,
            createdAt = Instant.EPOCH,
        )
        adapter.submit(listOf(event), today)
        for (width in listOf(360, 411)) {
            val row = adapter.getView(0, null, FrameLayout(context))
            measure(context, row, width)
            val title = row.findViewById<TextView>(R.id.eventTitle)
            assertEquals("Reference-length title wrapped at ${width}dp", 1, title.lineCount)
            assertText(title)
            capture(row, "home-reference-birthday-$width")
        }
    }

    @Test fun arrivalIllustrationsAreActuallyLaidOutAndClearWhenTheyShould() = instrumentation.runOnMainSync {
        val context = localized("en", 1f, false)
        val adapter = adapter(context)
        val parent = FrameLayout(context)
        val events = listOf(event(3), event(2), event(1), event(0), event(84), event(-15, CountMode.COUNT_UP))
        adapter.submit(events, today)
        events.indices.forEach { index ->
            val row = adapter.getView(index, null, parent)
            measure(context, row, 411)
            val illustration = row.findViewById<ImageView>(R.id.eventArrival)
            if (index <= 3) {
                assertEquals(View.VISIBLE, illustration.visibility)
                assertTrue("Arrival illustration was requested but not laid out", illustration.width >= dp(context, 18))
                assertTrue("Arrival illustration was requested but not laid out", illustration.height >= dp(context, 18))
                val status = row.findViewById<View>(R.id.eventStatus)
                assertTrue("Arrival illustration overlaps the status", illustration.bottom <= status.top)
            } else {
                assertEquals(View.GONE, illustration.visibility)
            }
        }

        val largeTextContext = localized("en", 2f, false)
        val largeTextAdapter = adapter(largeTextContext)
        largeTextAdapter.submit(listOf(event(3)), today)
        val largeTextRow = largeTextAdapter.getView(0, null, FrameLayout(largeTextContext))
        measure(largeTextContext, largeTextRow, 411)
        assertEquals(View.GONE, largeTextRow.findViewById<View>(R.id.eventArrival).visibility)
    }

    @Test fun shortCardsStayCompactWithoutSmallerFonts() = instrumentation.runOnMainSync {
        val context = localized("en", 1f, false)
        val adapter = adapter(context)
        adapter.submit(listOf(event(84).copy(title = "Trip")), today)
        val row = adapter.getView(0, null, FrameLayout(context))
        measure(context, row, 411)
        assertTrue("Short card grew beyond the compact budget", row.height <= dp(context, 104))
        assertEquals(17f * context.resources.displayMetrics.scaledDensity,
            row.findViewById<TextView>(R.id.eventTitle).textSize, 0.6f)
        val statusSize = row.findViewById<TextView>(R.id.eventStatus).textSize
        val metrics = context.resources.displayMetrics
        assertTrue(statusSize >= TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, metrics) - 0.6f)
        assertTrue(statusSize <= TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, metrics) + 0.6f)
    }

    @Test fun recycledRowsKeepModeAndTextWhenWidthChanges() = instrumentation.runOnMainSync {
        val context = localized("en", 2f, false)
        val adapter = adapter(context)
        val parent = FrameLayout(context)
        adapter.submit(listOf(event(0)), today)
        val row = adapter.getView(0, null, parent)
        measure(context, row, 320)
        adapter.submit(listOf(event(-15, CountMode.COUNT_UP)), today)
        assertSame(row, adapter.getView(0, row, parent))
        measure(context, row, 411)
        assertEquals("+15 days", row.findViewById<TextView>(R.id.eventStatus).text.toString())
        assertNull(row.foreground)
        assertText(row.findViewById(R.id.eventTitle))
        assertText(row.findViewById(R.id.eventStatus))
    }

    private fun adapter(context: Context) = CountdownEventAdapter(context, {}, {}, { _, _ -> })
    private fun fixtures() = listOf(event(84), event(3), event(2), event(1), event(0), event(-20),
        event(24, CountMode.COUNT_UP), event(0, CountMode.COUNT_UP), event(-15, CountMode.COUNT_UP),
        event(-1763, CountMode.COUNT_UP).copy(title = "Reading together for our next project"))
    private fun event(offset: Int, mode: CountMode = CountMode.COUNT_DOWN) = CountdownEvent(
        id = "$mode-$offset", title = if (mode == CountMode.COUNT_UP) "Reading" else "Our next trip",
        date = today.plusDays(offset.toLong()), type = EventType.EVENT, icon = EventIcon.CALENDAR,
        createdAt = Instant.EPOCH, countMode = mode,
    )

    private fun assertText(view: TextView) {
        val layout = requireNotNull(view.layout)
        assertTrue("Text height clipped: ${view.text}", layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
        for (line in 0 until layout.lineCount) {
            assertEquals("Ellipsis: ${view.text}", 0, layout.getEllipsisCount(line))
            assertTrue("Text width clipped: ${view.text}", layout.getLineMax(line) <=
                view.width - view.compoundPaddingLeft - view.compoundPaddingRight + 1)
            val end = layout.getLineEnd(line)
            if (line < layout.lineCount - 1 && end in 1 until view.text.length) {
                assertFalse("Word split: ${view.text}", view.text[end - 1].isLetterOrDigit() && view.text[end].isLetterOrDigit())
            }
        }
    }

    private fun localized(language: String, scale: Float, dark: Boolean): Context {
        val base = instrumentation.targetContext
        val config = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
            fontScale = scale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return ContextThemeWrapper(base.createConfigurationContext(config), base.applicationInfo.theme)
    }
    private fun measure(context: Context, view: View, width: Int) {
        repeat(3) {
            view.forceLayout()
            view.measure(View.MeasureSpec.makeMeasureSpec(dp(context, width), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
    }
    private fun capture(view: View, name: String) {
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(image))
            File(instrumentation.targetContext.filesDir, "layout-evidence/$name.png").apply {
                parentFile!!.mkdirs()
                outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally { image.recycle() }
    }
    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
}
