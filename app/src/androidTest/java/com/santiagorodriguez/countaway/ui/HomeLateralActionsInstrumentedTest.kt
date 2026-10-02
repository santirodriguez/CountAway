package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
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
class HomeLateralActionsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val today = LocalDate.of(2026, 9, 30)
    private val textIds = listOf(R.id.eventTitle, R.id.eventMeta, R.id.eventStatus)
    private val contentIds = textIds + listOf(R.id.eventIcon, R.id.eventArrival)

    @Test fun everyCardKeepsOneSmallEdgeActionAndCompleteReadableContent() = instrumentation.runOnMainSync {
        var compactCount = 0
        var expandedCount = 0
        val problems = mutableListOf<String>()
        for (language in listOf("en", "es", "ca")) for (scale in listOf(1f, 1.3f, 2f)) {
            for (dark in listOf(false, true)) for (width in listOf(320, 360, 411)) for ((index, event) in fixtures().withIndex()) {
                val context = localized(language, scale, dark)
                val before = baseline(context, event)
                val after = current(context, event)
                measure(context, before, width)
                measure(context, after, width)
                val case = "$language-$scale-$dark-$width-$index"
                try {
                    // The prior layout remains a content/type-size reference, not
                    // a requirement to retain its superseded below-count menu.
                    for (id in textIds) {
                        val old = before.findViewById<TextView>(id)
                        val now = after.findViewById<TextView>(id)
                        assertEquals(old.text.toString(), now.text.toString())
                        assertEquals("Typography changed", old.textSize, now.textSize, 0.001f)
                        assertText(now)
                    }
                    assertEdgeTarget(context, after)
                    val status = after.findViewById<View>(R.id.eventStatus)
                    if (status.top >= after.findViewById<View>(R.id.eventMeta).top) expandedCount++ else compactCount++
                } catch (error: AssertionError) {
                    problems += "$case: ${error.message}"
                    capture(after, "home-micro-failure-$case")
                }
                if ((width == 411 && scale == 1f) || (width == 320 && scale == 2f && index >= 5)) {
                    capture(after, "home-micro-$case")
                }
            }
        }
        File(instrumentation.targetContext.filesDir, "layout-evidence/home-lateral-matrix.txt").apply {
            parentFile!!.mkdirs()
            writeText("checked=${compactCount + expandedCount}\nexpected=486\ncompact=$compactCount\nexpanded=$expandedCount\nfailures=${problems.size}\n" + problems.joinToString("\n"))
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        assertEquals(486, compactCount + expandedCount)
        assertTrue("Compact content was never exercised", compactCount > 0)
        assertTrue("Expanded content was never exercised", expandedCount > 0)
    }

    @Test fun recyclingRtlResizeAndActionlessRowsMatchFreshLayout() = instrumentation.runOnMainSync {
        for (scale in listOf(1f, 2f)) {
            val context = localized("ca", scale)
            val parent = FrameLayout(context)
            val withActions = CountdownEventAdapter(context, {}, {}, { _, _ -> })
            val withoutActions = CountdownEventAdapter(context)
            var recycled: View? = null
            for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
                for (width in listOf(600, 320, 411, 320)) for (event in listOf(fixtures().first(), fixtures().last())) {
                    for (adapter in listOf(withActions, withoutActions, withActions)) {
                        adapter.submit(listOf(event), today)
                        val row = adapter.getView(0, recycled, parent)
                        recycled = row
                        val fresh = adapter.getView(0, null, parent)
                        row.layoutDirection = direction
                        fresh.layoutDirection = direction
                        measure(context, row, width)
                        measure(context, fresh, width)
                        assertEquals(fresh.height, row.height)
                        for (id in contentIds + R.id.eventActions + R.id.eventCountTile) {
                            assertEquals(bounds(fresh.findViewById(id)), bounds(row.findViewById(id)))
                        }
                        textIds.forEach { assertText(row.findViewById(it)) }
                        if (adapter === withActions) {
                            assertEdgeTarget(context, row)
                        } else {
                            val menu = row.findViewById<View>(R.id.eventActions)
                            assertEquals(View.GONE, menu.visibility)
                            assertEquals(0, menu.width)
                            assertEquals(0, menu.height)
                            val status = row.findViewById<View>(R.id.eventStatus)
                            val end = if (direction == View.LAYOUT_DIRECTION_RTL) status.left else row.width - status.right
                            assertEquals("Hidden actions must return their width to content", row.paddingEnd, end)
                        }
                        val expected = image(fresh)
                        val actual = image(row)
                        try { assertTrue("Recycling left stale layout or decoration", expected.sameAs(actual)) }
                        finally { expected.recycle(); actual.recycle() }
                    }
                }
            }
        }
    }

    @Test fun microNotchAndVisibleDotsUseThemeColorsAndMirrorInsideTheCard() = instrumentation.runOnMainSync {
        for (language in listOf("en", "es", "ca")) for (dark in listOf(false, true)) {
            for (scale in listOf(1f, 2f)) {
                val context = localized(language, scale, dark)
                val row = current(context, if (scale == 1f) fixtures().first() else fixtures().last())
                for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
                    row.layoutDirection = direction
                    measure(context, row, 320)
                    assertEdgeTarget(context, row)
                    val menu = row.findViewById<View>(R.id.eventActions)
                    assertTrue(menu.isClickable && menu.isFocusable)
                    assertFalse(menu.contentDescription.isNullOrBlank())
                    assertNotNull("Native feedback must remain", menu.foreground)
                    assertEquals(Color.TRANSPARENT, (menu.background as ColorDrawable).color)
                    val pixels = image(row)
                    try {
                        val rtl = direction == View.LAYOUT_DIRECTION_RTL
                        fun x(distance: Int) = if (rtl) dp(context, distance) else row.width - 1 - dp(context, distance)
                        val center = (menu.top + menu.bottom) / 2
                        assertEquals("Notch must reach the edge", context.getColor(R.color.surface_secondary), pixels.getPixel(x(0), center))
                        assertEquals("Notch must stay shallow", context.getColor(R.color.surface), pixels.getPixel(x(18), center))
                        assertNotEquals("Notch must stay short", context.getColor(R.color.surface_secondary), pixels.getPixel(x(0), center - dp(context, 18)))
                        assertNotEquals("Notch must stay short", context.getColor(R.color.surface_secondary), pixels.getPixel(x(0), center + dp(context, 18)))
                        // This checks actual child drawing near the edge. Geometry
                        // alone would miss clipping by the row's old end padding.
                        assertEquals("Overflow dots must not be clipped", context.getColor(R.color.accent_text), pixels.getPixel(x(8), center))
                    } finally { pixels.recycle() }
                    capture(row, "home-notch-$language-$dark-$scale-$direction")
                }
            }
        }
    }

    @Test fun theFullMenuTargetDoesNotTriggerTheEventOrBypassDisabledAncestors() {
        var edits = 0
        var menus = 0
        lateinit var parent: FrameLayout
        lateinit var row: View
        lateinit var adapter: CountdownEventAdapter
        ActivityScenario.launch<MainActivity>(Intent(instrumentation.targetContext, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                adapter = CountdownEventAdapter(activity, {}, { edits++ }, { _, _ -> menus++ })
                adapter.submit(listOf(fixtures().first()), today)
                parent = FrameLayout(activity)
                row = adapter.getView(0, null, parent)
                parent.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
                activity.setContentView(parent)
            }
            var expectedMenus = 0
            for (fixture in listOf(fixtures().first(), fixtures().last())) {
                for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
                    scenario.onActivity {
                        adapter.submit(listOf(fixture), today)
                        assertSame(row, adapter.getView(0, row, parent))
                        row.layoutDirection = direction
                    }
                    instrumentation.waitForIdleSync()
                    for (edge in 0..3) {
                        scenario.onActivity {
                            val menu = row.findViewById<View>(R.id.eventActions)
                            assertTrue("Attached row has no measured target", menu.width > 0 && row.isAttachedToWindow)
                            val x = when (edge) {
                                0 -> menu.left + 1f
                                1 -> menu.right - 1f
                                else -> (menu.left + menu.right) / 2f
                            }
                            val y = when (edge) {
                                2 -> menu.top + 1f
                                3 -> menu.bottom - 1f
                                else -> (menu.top + menu.bottom) / 2f
                            }
                            val now = SystemClock.uptimeMillis()
                            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                                val event = MotionEvent.obtain(now, now + 16, action, x, y, 0)
                                try { assertTrue(row.dispatchTouchEvent(event)) } finally { event.recycle() }
                            }
                        }
                        instrumentation.waitForIdleSync()
                        expectedMenus++
                        scenario.onActivity {
                            assertEquals(expectedMenus, menus)
                            assertEquals(0, edits)
                        }
                    }
                    scenario.onActivity {
                        val menu = row.findViewById<View>(R.id.eventActions)
                        val rtl = direction == View.LAYOUT_DIRECTION_RTL
                        val x = (if (rtl) menu.left + dp(row.context, 47) else menu.right - dp(row.context, 47)).toFloat()
                        val y = (menu.top + menu.bottom) / 2f
                        val now = SystemClock.uptimeMillis()
                        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                            val event = MotionEvent.obtain(now, now + 16, action, x, y, 0)
                            try { assertTrue(row.dispatchTouchEvent(event)) } finally { event.recycle() }
                        }
                    }
                    instrumentation.waitForIdleSync()
                    expectedMenus++
                    scenario.onActivity {
                        assertEquals("Expanded 48dp delegate did not open actions", expectedMenus, menus)
                        assertEquals(0, edits)
                    }
                }
            }
            scenario.onActivity {
                val menu = row.findViewById<View>(R.id.eventActions)
                assertTrue(menu.requestFocusFromTouch())
                for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                    assertTrue(menu.dispatchKeyEvent(KeyEvent(action, KeyEvent.KEYCODE_DPAD_CENTER)))
                }
            }
            instrumentation.waitForIdleSync()
            expectedMenus++
            scenario.onActivity {
                val menu = row.findViewById<View>(R.id.eventActions)
                assertEquals(expectedMenus, menus)
                assertEquals(0, edits)
                parent.isEnabled = false
                menu.performClick()
                row.performClick()
                assertEquals(expectedMenus, menus)
                assertEquals(0, edits)
                parent.isEnabled = true
                row.performClick()
                assertEquals(1, edits)
            }
        }
    }

    private fun assertEdgeTarget(context: Context, row: View) {
        val menu = row.findViewById<View>(R.id.eventActions)
        val tile = row.findViewById<View>(R.id.eventCountTile)
        assertEquals(dp(context, 20), menu.width)
        assertEquals(dp(context, 48), menu.height)
        assertTrue(menu.top >= row.paddingTop && menu.bottom <= row.height - row.paddingBottom)
        assertEquals((tile.top + tile.bottom) / 2f, (menu.top + menu.bottom) / 2f, 1.1f)
        val rtl = row.layoutDirection == View.LAYOUT_DIRECTION_RTL
        assertEquals(if (rtl) 0 else row.width, if (rtl) menu.left else menu.right)
        val content = contentIds.map { row.findViewById<View>(it) }.filter { it.visibility == View.VISIBLE && it.width > 0 }
        for (view in content) {
            assertTrue("Content outside row", view.left >= 0 && view.top >= 0 && view.right <= row.width && view.bottom <= row.height)
            assertFalse("Action overlaps content", Rect.intersects(bounds(menu), bounds(view)))
            assertTrue("Content must clear the visible edge control", if (rtl) view.left >= menu.right + dp(context, 2) - 1
                else view.right <= menu.left - dp(context, 2) + 1)
        }
        for (i in content.indices) for (j in i + 1 until content.size) {
            assertFalse("Content overlaps", Rect.intersects(bounds(content[i]), bounds(content[j])))
        }
        assertFalse("Tile overlaps visible action", Rect.intersects(bounds(tile), bounds(menu)))
        assertEquals("Tile must sit directly beside the compact edge rail", dp(context, 2),
            if (rtl) tile.left - menu.right else menu.left - tile.right)
        for (id in listOf(R.id.eventTitle, R.id.eventMeta, R.id.eventIcon)) {
            assertFalse("Tile overlaps primary content", Rect.intersects(bounds(tile), bounds(row.findViewById(id))))
        }
    }

    private fun assertText(view: TextView) {
        val layout = requireNotNull(view.layout)
        assertTrue("Text height clipped: ${view.text}", layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
        for (line in 0 until layout.lineCount) {
            assertEquals("Ellipsis: ${view.text}", 0, layout.getEllipsisCount(line))
            assertTrue("Text width clipped: ${view.text}", layout.getLineMax(line) <= view.width - view.compoundPaddingLeft - view.compoundPaddingRight + 1)
            val end = layout.getLineEnd(line)
            if (line < layout.lineCount - 1 && end in 1 until view.text.length) {
                assertFalse("Word split: ${view.text}", view.text[end - 1].isLetterOrDigit() && view.text[end].isLetterOrDigit())
            }
        }
    }

    private fun current(context: Context, event: CountdownEvent): View {
        val adapter = CountdownEventAdapter(context, {}, {}, { _, _ -> })
        adapter.submit(listOf(event), today)
        return adapter.getView(0, null, FrameLayout(context))
    }

    private fun baseline(context: Context, event: CountdownEvent): View {
        val source = current(context, event) as ViewGroup
        val baseline = HomeRowBaseline(context).apply {
            background = source.background.constantState!!.newDrawable(context.resources).mutate()
            minimumHeight = source.minimumHeight
            setPadding(source.paddingLeft, source.paddingTop, source.paddingRight, source.paddingBottom)
        }
        while (source.childCount > 0) {
            val child = source.getChildAt(0)
            source.removeView(child)
            baseline.addView(child)
        }
        return baseline
    }

    private fun fixtures(): List<CountdownEvent> {
        fun event(offset: Int, mode: CountMode = CountMode.COUNT_DOWN, title: String = "Trip") =
            CountdownEvent("$offset-$mode-$title", title, today.plusDays(offset.toLong()), EventType.EVENT,
                createdAt = Instant.EPOCH, countMode = mode)
        return listOf(event(84), event(3), event(0), event(-20), event(24, CountMode.COUNT_UP),
            event(-1763, CountMode.COUNT_UP, "Reading together for our next project"),
            event(1, title = "Sofía 🧩 and family 👨‍👩‍👧‍👦"),
            event(-12345, CountMode.COUNT_UP, "Reading together 🧩 for our next project"),
            // At 200% / 320dp this word is wider than the compact text column
            // but still fits the full text band, so it exercises wideText
            // without violating the no-word-splitting readability contract.
            event(9, title = "Extraordinary"))
    }

    private fun localized(language: String, scale: Float, dark: Boolean? = null): Context {
        val base = instrumentation.targetContext
        val config = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
            fontScale = scale
            if (dark != null) uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return ContextThemeWrapper(base.createConfigurationContext(config), base.applicationInfo.theme)
    }
    private fun bounds(view: View) = Rect(view.left, view.top, view.right, view.bottom)
    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
    private fun measure(context: Context, view: View, width: Int) {
        repeat(3) {
            view.forceLayout()
            view.measure(View.MeasureSpec.makeMeasureSpec(dp(context, width), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
    }
    private fun image(view: View) = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        .also { view.draw(Canvas(it)) }
    private fun capture(view: View, name: String) {
        val pixels = image(view)
        try {
            File(instrumentation.targetContext.filesDir, "layout-evidence/$name.png").apply {
                parentFile!!.mkdirs()
                outputStream().use { pixels.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally { pixels.recycle() }
    }
}
