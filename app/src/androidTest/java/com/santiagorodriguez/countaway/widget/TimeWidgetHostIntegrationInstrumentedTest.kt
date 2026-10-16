package com.santiagorodriguez.countaway.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.ui.MainActivity
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/**
 * Real AppWidgetManager -> AppWidgetHost binder path. Unlike RemoteViews.apply,
 * the widget is bound to an actual host ID, receives manager updates, and is
 * attached to a visible Activity so the native Chronometer can tick.
 * This is an instrumented host, not a third-party launcher/Doze device test.
 */
class TimeWidgetHostIntegrationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val repository = CountdownRepository(app)

    @Test fun boundTimeWidgetTicksAndCrossesZeroWhileClassicInstanceSurvives() {
        val original = (repository.loadResult() as CountdownLoadResult.Success).events
        val manager = AppWidgetManager.getInstance(app)
        val preferences = WidgetPreferences(app)
        val host = AppWidgetHost(app, 1_240_056)
        val userId = shell("am get-current-user").toInt()
        var timedId = AppWidgetManager.INVALID_APPWIDGET_ID
        var classicId = AppWidgetManager.INVALID_APPWIDGET_ID
        try {
            val grant = shell("appwidget grantbind --package ${app.packageName} --user $userId")
            assertTrue("Grant app-widget binding failed: $grant", grant.isBlank())
            host.deleteHost() // Only this test-specific host ID, never the user's launcher.
            timedId = host.allocateAppWidgetId()
            classicId = host.allocateAppWidgetId()
            val timedComponent = ComponentName(app, TimeWidgetProvider::class.java)
            val classicComponent = ComponentName(app, CountdownWidgetProvider::class.java)
            assertTrue(manager.bindAppWidgetIdIfAllowed(timedId, timedComponent, options(280, 210)))
            assertTrue(manager.bindAppWidgetIdIfAllowed(classicId, classicComponent, options(160, 100)))
            assertEquals(timedComponent, manager.getAppWidgetInfo(timedId)?.provider)
            assertEquals(classicComponent, manager.getAppWidgetInfo(classicId)?.provider)
            drain()

            val zone = java.time.ZoneId.systemDefault()
            val farEnough = ZonedDateTime.now(zone).plusMinutes(3).withNano(0)
            val timed = CountdownEvent(
                id = "time-host-" + UUID.randomUUID(),
                title = "Hosted live concert",
                date = farEnough.toLocalDate(),
                eventTime = farEnough.toLocalTime(),
                eventZone = zone,
                type = EventType.EVENT,
                createdAt = Instant.now(),
            )
            val dateOnly = CountdownEvent(
                id = "classic-host-" + UUID.randomUUID(),
                title = "Date-only companion",
                date = LocalDate.now(zone).plusDays(7),
                type = EventType.EVENT,
                createdAt = Instant.now(),
            )
            repository.save(original + timed + dateOnly)
            preferences.save(timedId, timed.id, WidgetAppearance.DARK, WidgetBackground.HORIZON,
                WidgetEventSelection.FIXED)
            preferences.save(classicId, dateOnly.id, WidgetAppearance.LIGHT, WidgetBackground.CLASSIC,
                WidgetEventSelection.FIXED)

            ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)).use { scenario ->
                lateinit var timedView: AppWidgetHostView
                lateinit var classicView: AppWidgetHostView
                scenario.onActivity { activity ->
                    val container = activity.findViewById<FrameLayout>(android.R.id.content)
                    timedView = host.createView(activity, timedId, manager.getAppWidgetInfo(timedId))
                    classicView = host.createView(activity, classicId, manager.getAppWidgetInfo(classicId))
                    container.addView(timedView, FrameLayout.LayoutParams(
                        px(activity, 280), px(activity, 210), Gravity.TOP or Gravity.START))
                    container.addView(classicView, FrameLayout.LayoutParams(
                        px(activity, 160), px(activity, 100), Gravity.BOTTOM or Gravity.END))
                    host.startListening()
                }
                TimeWidgetProvider.updateWidget(app, manager, timedId)
                CountdownWidgetProvider.updateWidget(app, manager, classicId)
                instrumentation.waitForIdleSync()
                val first = awaitClock(timedView, 8_000) { value ->
                    value.contains(":") && !value.startsWith("-")
                }
                assertTrue("Classic provider lost its independent title",
                    awaitText(classicView, R.id.widgetTitle, 8_000).contains("Date-only companion"))
                capture(timedView, "bound-time-before-tick.png")
                SystemClock.sleep(2_200)
                val second = awaitClock(timedView, 4_000) { it != first && it.contains(":") }
                assertNotEquals("Native host did not advance without app refresh", first, second)

                // One-off countdown must pass zero as a signed native host timer,
                // without a per-second app-side update or a false static day prefix.
                val soon = ZonedDateTime.now(zone).plusSeconds(10).withNano(0)
                repository.save(original + timed.copy(date = soon.toLocalDate(),
                    eventTime = soon.toLocalTime()) + dateOnly)
                TimeWidgetProvider.updateWidget(app, manager, timedId)
                awaitClock(timedView, 5_000) { value ->
                    value.contains(":") && !value.startsWith("-") && value != second
                }
                val negative = awaitClock(timedView, 15_000) { it.startsWith("-") }
                assertTrue("Host did not display signed elapsed time after the target: $negative",
                    negative.startsWith("-"))
                assertTrue("Classic widget disappeared when time widget crossed zero",
                    awaitText(classicView, R.id.widgetTitle, 4_000).contains("Date-only companion"))
                capture(timedView, "bound-time-after-target.png")
            }
        } finally {
            runCatching { host.stopListening() }
            runCatching { host.deleteHost() }
            if (timedId != AppWidgetManager.INVALID_APPWIDGET_ID) preferences.remove(timedId)
            if (classicId != AppWidgetManager.INVALID_APPWIDGET_ID) preferences.remove(classicId)
            repository.save(original)
            drain()
            shell("appwidget revokebind --package ${app.packageName} --user $userId")
        }
    }

    private fun options(width: Int, height: Int) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
    }

    private fun px(context: android.content.Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()

    private fun awaitClock(view: AppWidgetHostView, timeoutMs: Long,
        accept: (String) -> Boolean): String {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var observed = ""
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                val clock = view.findViewById<Chronometer>(R.id.timeWidgetClock)
                observed = if (clock?.visibility == View.VISIBLE && clock.isShown)
                    clock.text.toString() else ""
            }
            if (accept(observed)) return observed
            SystemClock.sleep(100)
        }
        fail("No valid hosted clock within $timeoutMs ms; last text: '$observed'")
        error("Unreachable")
    }

    private fun awaitText(view: AppWidgetHostView, id: Int, timeoutMs: Long): String {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var observed = ""
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                observed = view.findViewById<TextView>(id)?.text?.toString().orEmpty()
            }
            if (observed.isNotBlank()) return observed
            SystemClock.sleep(100)
        }
        fail("No hosted text for view $id")
        error("Unreachable")
    }

    private fun capture(view: AppWidgetHostView, name: String) {
        instrumentation.runOnMainSync {
            val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1),
                view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val file = File(app.filesDir, "layout-evidence/time-widget-host/$name")
                file.parentFile?.mkdirs()
                file.outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)
        ).use { it.readBytes().toString(Charsets.UTF_8).trim() }

    private fun drain() {
        val done = CountDownLatch(1)
        CountdownIo.submit({ Unit }) { done.countDown() }
        assertTrue(done.await(15, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }
}
