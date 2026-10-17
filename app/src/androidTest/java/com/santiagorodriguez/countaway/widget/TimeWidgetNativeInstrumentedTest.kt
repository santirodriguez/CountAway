package com.santiagorodriguez.countaway.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownTimeSnapshot
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Test

class TimeWidgetNativeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val snapshot = CountdownTimeSnapshot(
        ZonedDateTime.of(2026, 10, 16, 12, 0, 0, 0, ZoneId.of("America/Argentina/Buenos_Aires")))
    private val event = CountdownEvent(
        id = "concert", title = "Extraordinarily long concert anniversary in Buenos Aires",
        date = LocalDate.of(2026, 10, 19), eventTime = LocalTime.of(19, 12, 5),
        eventZone = snapshot.zone, type = EventType.EVENT,
        createdAt = Instant.parse("2026-09-01T12:00:00Z"),
    )

    @Test fun newProviderIsIndependentOfTheOriginalOneByOne() {
        val providers = AppWidgetManager.getInstance(app).installedProviders
        val classic = providers.first { it.provider == ComponentName(app, CountdownWidgetProvider::class.java) }
        val timed = providers.first { it.provider == ComponentName(app, TimeWidgetProvider::class.java) }
        assertNotEquals(classic.provider, timed.provider)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            assertEquals(1, classic.targetCellWidth)
            assertEquals(1, classic.targetCellHeight)
            assertEquals(2, timed.targetCellWidth)
            assertEquals(2, timed.targetCellHeight)
        }
        assertEquals(ComponentName(app, TimeWidgetConfigActivity::class.java), timed.configure)
        assertTrue("Timed widget should fit a two-column launcher cell", timed.minResizeWidth <= 140)
        assertTrue("Timed widget should shrink to a one-row height", timed.minResizeHeight <= 84)
    }

    @Test fun nativeClockAndTitleFitCompactWideTallAndLargeHostSizes() = instrumentation.runOnMainSync {
        val sizes = listOf(160 to 90, 240 to 95, 180 to 120, 160 to 190,
            240 to 180, 280 to 210, 320 to 250)
        val fonts = listOf(1f, 1.3f, 2f)
        val themes = listOf(WidgetAppearance.DARK, WidgetAppearance.LIGHT)
        for ((width, height) in sizes) for (font in fonts) for (appearance in themes) {
            val context = fontContext(font)
            val style = WidgetConfiguration(event.id, appearance, WidgetBackground.HORIZON)
            val remote = TimeWidgetRenderer.render(context, context, 1234, style,
                event, null, snapshot, width, height, hashMapOf(), interactive = false)
            val root = remote.apply(context, FrameLayout(context)) as FrameLayout
            layout(root, context, width, height)
            val native = root.findViewById<Chronometer>(R.id.timeWidgetClock)
            assertEquals(View.VISIBLE, native.visibility)
            assertTrue("Clock failed to show native H:MM:SS", native.text.toString().contains(":"))
            assertTrue("More than 24 hours must use total hours: " + native.text,
                native.text.toString().substringBefore(":").toLongOrNull()?.let { it >= 24L } == true)
            val name = "native-" + width + "x" + height + "-font" +
                (font * 100).toInt() + "-" + appearance.storageKey
            val plan = TimeWidgetLayoutPlanner.plan(width, height, font)
            val date = root.findViewById<TextView>(R.id.timeWidgetDate)
            assertTrue(date.text.toString().contains("19"))
            assertEquals(name + " date visibility",
                if (plan.showDate) View.VISIBLE else View.GONE, date.visibility)
            assertEquals(name + " no redundant live caption", View.GONE,
                root.findViewById<TextView>(R.id.timeWidgetCaption).visibility)
            assertEquals(name + " decorative accent visibility",
                if (plan.showAccent) View.VISIBLE else View.GONE,
                root.findViewById<View>(R.id.timeWidgetAccentLine).visibility)
            assertEquals(name + " more title lines when available",
                plan.titleLines, root.findViewById<TextView>(R.id.timeWidgetTitle).maxLines)
            val labelIds = buildList {
                add(R.id.timeWidgetTitle)
                add(R.id.timeWidgetClock)
                if (plan.showDate) add(R.id.timeWidgetDate)
            }
            val bounds = labelIds.map { id ->
                val tv = root.findViewById<TextView>(id)
                assertTrue(name + " lost a field: " + id, tv.visibility == View.VISIBLE && tv.height > 0)
                assertNotNull(name + " missing text layout", tv.layout)
                val rect = Rect()
                root.offsetDescendantRectToMyCoords(tv, rect.apply {
                    set(0, 0, tv.width, tv.height)
                })
                assertTrue(name + " outside card: " + tv.text + " " + rect,
                    rect.left >= 0 && rect.top >= 0 &&
                        rect.right <= root.width && rect.bottom <= root.height)
                rect
            }
            val nativeLayout = checkNotNull(native.layout)
            val ink = Rect()
            val digits = native.text.toString()
            native.paint.getTextBounds(digits, 0, digits.length, ink)
            ink.offset(native.compoundPaddingLeft + nativeLayout.getLineLeft(0).toInt(), native.baseline)
            assertTrue(name + " native digits clipped " + ink,
                Rect(0, 0, native.width, native.height).contains(ink))
            for (index in bounds.indices) for (other in index + 1 until bounds.size) {
                assertFalse(name + " overlapping " + index + "/" + other,
                    Rect.intersects(bounds[index], bounds[other]))
            }
            capture(root, name + ".png")
        }
    }

    @Test fun pickerNamesLeadWithDaysOrLiveHoursInAllLanguages() {
        for ((tag,days,hours) in listOf(
            Triple("en", "Days", "Live hours"),
            Triple("es", "Días", "Horas en vivo"),
            Triple("ca", "Dies", "Hores en directe"),
        )) {
            val localized = app.createConfigurationContext(
                Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
            assertTrue(localized.getString(R.string.widget_name).startsWith(days))
            assertTrue(localized.getString(R.string.time_widget_name).startsWith(hours))
            assertNotEquals(localized.getString(R.string.widget_name),
                localized.getString(R.string.time_widget_name))
        }
    }

    @Test fun compactHorizonBackgroundKeepsTimerClearWithoutChangingClassicArt() {
        for (dark in listOf(false, true)) {
            val classic = WidgetBackgroundRenderer.render(app, WidgetBackground.HORIZON,
                dark, 240, 180)
            val timed = WidgetBackgroundRenderer.render(app, WidgetBackground.HORIZON,
                dark, 240, 180, hideHorizonDisc = true)
            val cx = (classic.width * (if (dark) 0.79f else 0.73f)).toInt()
            val cy = (classic.height * (if (dark) 0.63f else 0.72f)).toInt()
            assertNotEquals("Compact timed Horizon must not paint over the clock",
                classic.getPixel(cx, cy), timed.getPixel(cx, cy))
            val unrelated = WidgetBackgroundRenderer.render(app, WidgetBackground.MIST,
                dark, 240, 180)
            val unchanged = WidgetBackgroundRenderer.render(app, WidgetBackground.MIST,
                dark, 240, 180, hideHorizonDisc = true)
            assertEquals("Other styles must not change for the timed widget",
                unrelated.getPixel(cx, cy), unchanged.getPixel(cx, cy))
            classic.recycle()
            timed.recycle()
            unrelated.recycle()
            unchanged.recycle()
        }
    }

    @Test fun fourDigitNativeHoursFitAtTwoHundredPercentText() = instrumentation.runOnMainSync {
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        val now = ZonedDateTime.now(zone)
        val future = now.plusDays(120)
        val targetEvent = event.copy(date = future.toLocalDate(),
            eventTime = future.toLocalTime().withSecond(0).withNano(0), eventZone = zone)
        val context = fontContext(2f)
        val configuration = WidgetConfiguration(targetEvent.id, WidgetAppearance.DARK, WidgetBackground.HORIZON)
        for ((width, height) in listOf(160 to 90, 160 to 190, 240 to 180, 280 to 210, 320 to 250)) {
            val views = TimeWidgetRenderer.render(context, context, 1234, configuration,
                targetEvent, null, CountdownTimeSnapshot(now), width, height,
                hashMapOf(), interactive = false)
            val root = views.apply(context, FrameLayout(context)) as FrameLayout
            layout(root, context, width, height)
            val clock = root.findViewById<Chronometer>(R.id.timeWidgetClock)
            assertEquals(View.VISIBLE, clock.visibility)
            val label = clock.text.toString()
            assertTrue("Expected four-digit accumulated hours: " + label,
                label.substringBefore(":").length >= 4)
            val ink = Rect()
            clock.paint.getTextBounds(label, 0, label.length, ink)
            ink.offset(clock.compoundPaddingLeft + checkNotNull(clock.layout).getLineLeft(0).toInt(),
                clock.baseline)
            assertTrue("Four-digit native counter clipped at " + width + "dp: " + ink,
                Rect(0, 0, clock.width, clock.height).contains(ink))
            assertTrue(root.findViewById<TextView>(R.id.timeWidgetTitle).height > 0)
            val date = root.findViewById<TextView>(R.id.timeWidgetDate)
            val showDate = TimeWidgetLayoutPlanner.plan(width, height, 2f).showDate
            assertEquals(if (showDate) View.VISIBLE else View.GONE, date.visibility)
            if (showDate) assertTrue(date.height > 0)
            capture(root, "native-four-digit-" + width + "x" + height + "-font200-dark.png")
        }
    }

    @Test fun farTargetsNeverDisplayFrozenOrOverflowingSeconds() = instrumentation.runOnMainSync {
        val distant = event.copy(date = LocalDate.of(2030, 10, 19))
        val remote = TimeWidgetRenderer.render(app, app, 1234,
            WidgetConfiguration(distant.id, WidgetAppearance.SYSTEM, WidgetBackground.HORIZON),
            distant, null, snapshot, 240, 180, hashMapOf(), interactive = false)
        val root = remote.apply(app, FrameLayout(app)) as FrameLayout
        layout(root, app, 240, 180)
        assertEquals(View.GONE, root.findViewById<Chronometer>(R.id.timeWidgetClock).visibility)
        assertEquals(View.GONE, root.findViewById<TextView>(R.id.timeWidgetCaption).visibility)
        assertEquals(View.VISIBLE, root.findViewById<TextView>(R.id.timeWidgetFallback).visibility)
    }

    private fun fontContext(scale: Float): Context = app.createConfigurationContext(
        Configuration(app.resources.configuration).apply { fontScale = scale; setLocale(Locale.US) })

    private fun layout(root: View, context: Context, widthDp: Int, heightDp: Int) {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).roundToInt()
        val height = (heightDp * density).roundToInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
    }

    private fun capture(root: View, name: String) {
        val file = File(app.filesDir, "layout-evidence/time-widget/" + name)
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        try {
            root.draw(Canvas(bitmap))
            file.outputStream().use { stream ->
                assertTrue("Cannot save native widget screenshot", bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
