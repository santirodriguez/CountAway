package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.view.ContextThemeWrapper
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
    private val textIds = listOf(R.id.eventTitle, R.id.eventMeta)
    private val visibleIds = textIds + listOf(R.id.eventStatus, R.id.eventIcon, R.id.eventArrival, R.id.eventActions)

    @Test fun lateralPlacementPreservesTextAndFallbackMatchesAcceptedRow() = instrumentation.runOnMainSync {
        var lateralCount = 0
        var fallbackCount = 0
        for (language in listOf("en", "es", "ca")) for (scale in listOf(1f, 1.3f, 2f)) {
            for (width in listOf(320, 360, 411)) for ((index, event) in fixtures().withIndex()) {
                val context = localized(language, scale)
                val before = baseline(context, event)
                val after = current(context, event)
                measure(context, before, width)
                measure(context, after, width)
                val status = after.findViewById<View>(R.id.eventStatus)
                val menu = after.findViewById<View>(R.id.eventActions)
                val lateral = menu.top < status.bottom
                assertTrue("Card must not become taller", after.height <= before.height)
                for (id in textIds) {
                    val old = before.findViewById<TextView>(id)
                    val now = after.findViewById<TextView>(id)
                    assertEquals(old.text.toString(), now.text.toString())
                    assertEquals("Primary typography changed", old.textSize, now.textSize, 0.001f)
                    assertEquals("Primary text acquired different wrapping", lineEnds(old), lineEnds(now))
                    for (line in 0 until now.layout.lineCount) {
                        assertEquals(0, now.layout.getEllipsisCount(line))
                    }
                }
                assertTrue(menu.width >= dp(context, 48) && menu.height >= dp(context, 48))
                assertTrue(menu.left >= 0 && menu.right <= after.width && menu.bottom <= after.height)
                if (lateral) {
                    lateralCount++
                    assertTrue(menu.left >= status.right)
                    for (id in textIds) {
                        val text = after.findViewById<View>(id)
                        assertFalse(Rect.intersects(bounds(text), bounds(menu)))
                        assertFalse(Rect.intersects(bounds(text), bounds(status)))
                    }
                    val center = (after.findViewById<View>(R.id.eventTitle).top +
                        after.findViewById<View>(R.id.eventMeta).bottom) / 2f
                    assertEquals(center, (menu.top + menu.bottom) / 2f, 1.1f)
                    assertEquals(center, (status.top + status.bottom) / 2f, 1.1f)
                } else {
                    fallbackCount++
                    assertEquals("Fallback changed height", before.height, after.height)
                    for (id in visibleIds) {
                        val old = before.findViewById<View>(id)
                        val now = after.findViewById<View>(id)
                        assertEquals("Fallback moved $id", bounds(old), bounds(now))
                        assertEquals(old.visibility, now.visibility)
                    }
                    assertEquals(before.findViewById<TextView>(R.id.eventStatus).textSize,
                        after.findViewById<TextView>(R.id.eventStatus).textSize, 0.001f)
                    val oldPixels = image(before)
                    val newPixels = image(after)
                    try { assertTrue("Fallback pixels changed", oldPixels.sameAs(newPixels)) }
                    finally { oldPixels.recycle(); newPixels.recycle() }
                }
                if (width == 411 && scale == 1f) capture(after, "home-lateral-$language-$index")
            }
        }
        assertTrue("Lateral layout never activated", lateralCount > 0)
        assertTrue("Fallback never exercised", fallbackCount > 0)
        File(instrumentation.targetContext.filesDir, "layout-evidence/home-lateral-matrix.txt").apply {
            parentFile!!.mkdirs()
            writeText("checked=${lateralCount + fallbackCount}\nlateral=$lateralCount\nfallback=$fallbackCount\nfailures=0\n")
        }
    }

    @Test fun recyclingAndRtlKeepAnchorsSeparateAndReturnToOriginalFallback() = instrumentation.runOnMainSync {
        val context = localized("en", 1f)
        val event = fixtures().first()
        val row = current(context, event)
        val baseline = baseline(context, event)
        for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
            row.layoutDirection = direction
            baseline.layoutDirection = direction
            for (width in listOf(600, 320, 411, 320)) {
                measure(context, row, width)
                measure(context, baseline, width)
                val status = row.findViewById<View>(R.id.eventStatus)
                val menu = row.findViewById<View>(R.id.eventActions)
                if (menu.top < status.bottom) {
                    assertFalse(Rect.intersects(bounds(menu), bounds(status)))
                    assertTrue(if (direction == View.LAYOUT_DIRECTION_LTR) menu.left >= status.right
                        else menu.right <= status.left)
                } else {
                    for (id in visibleIds) assertEquals(bounds(baseline.findViewById(id)), bounds(row.findViewById(id)))
                }
            }
        }
    }

    @Test fun theFullMenuTargetDoesNotTriggerTheEventOrBypassDisabledAncestors() {
        var edits = 0
        var menus = 0
        lateinit var parent: FrameLayout
        lateinit var row: View
        ActivityScenario.launch<MainActivity>(Intent(instrumentation.targetContext, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                val adapter = CountdownEventAdapter(activity, {}, { edits++ }, { _, _ -> menus++ })
                adapter.submit(listOf(fixtures().first()), today)
                parent = FrameLayout(activity)
                row = adapter.getView(0, null, parent)
                parent.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
                activity.setContentView(parent)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val menu = row.findViewById<View>(R.id.eventActions)
                val status = row.findViewById<View>(R.id.eventStatus)
                assertTrue("Attached row has no measured menu target", menu.width > 0 && row.isAttachedToWindow)
                assertTrue("Expected lateral target in ordinary layout", menu.top < status.bottom)
                val now = SystemClock.uptimeMillis()
                // Hit the invisible edge of the full target, outside its visible circle.
                val x = menu.left + 1f
                val y = (menu.top + menu.bottom) / 2f
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(now, now + 16, action, x, y, 0)
                    try { assertTrue(row.dispatchTouchEvent(event)) } finally { event.recycle() }
                }
            }
            // View posts its click callback; do not assert before the main queue drains.
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val menu = row.findViewById<View>(R.id.eventActions)
                assertEquals(1, menus)
                assertEquals(0, edits)
                parent.isEnabled = false
                menu.performClick()
                row.performClick()
                assertEquals(1, menus)
                assertEquals(0, edits)
                parent.isEnabled = true
                row.performClick()
                assertEquals(1, edits)
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
            event(-1763, CountMode.COUNT_UP, "Reading together for our next project"))
    }

    private fun localized(language: String, scale: Float): Context {
        val base = instrumentation.targetContext
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)); fontScale = scale }
        return ContextThemeWrapper(base.createConfigurationContext(config), base.applicationInfo.theme)
    }
    private fun lineEnds(view: TextView) = (0 until view.layout.lineCount).map(view.layout::getLineEnd)
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
