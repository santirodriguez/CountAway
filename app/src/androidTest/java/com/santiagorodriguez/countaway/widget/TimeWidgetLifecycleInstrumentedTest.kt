package com.santiagorodriguez.countaway.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownTimeSnapshot
import com.santiagorodriguez.countaway.countdown.TimedEventResolver
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class TimeWidgetLifecycleInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext

    @Test fun restoringTimeInstanceLeavesBoundClassicConfigurationUntouched() {
        val host = AppWidgetHost(app, 1_240_058)
        val manager = AppWidgetManager.getInstance(app)
        val preferences = WidgetPreferences(app)
        val oldTimeId = 830_058
        val userId = shell("am get-current-user").toInt()
        var timeId = AppWidgetManager.INVALID_APPWIDGET_ID
        var classicId = AppWidgetManager.INVALID_APPWIDGET_ID
        try {
            assertTrue(shell("appwidget grantbind --package ${app.packageName} --user $userId").isBlank())
            host.deleteHost()
            preferences.remove(oldTimeId)
            timeId = host.allocateAppWidgetId()
            classicId = host.allocateAppWidgetId()
            val timeComponent = ComponentName(app, TimeWidgetProvider::class.java)
            val classicComponent = ComponentName(app, CountdownWidgetProvider::class.java)
            assertTrue(manager.bindAppWidgetIdIfAllowed(timeId, timeComponent, options(280, 210)))
            assertTrue(manager.bindAppWidgetIdIfAllowed(classicId, classicComponent, options(160, 100)))
            val timed = WidgetConfiguration("saved-timed-event", WidgetAppearance.DARK,
                WidgetBackground.HORIZON, WidgetEventSelection.FIXED)
            val classic = WidgetConfiguration("classic-event", WidgetAppearance.LIGHT,
                WidgetBackground.CLASSIC, WidgetEventSelection.FIXED)
            preferences.save(oldTimeId, timed.eventId, timed.appearance, timed.background, timed.eventSelection)
            preferences.save(classicId, classic.eventId, classic.appearance, classic.background,
                classic.eventSelection)
            TimeWidgetProvider().onRestored(app, intArrayOf(oldTimeId), intArrayOf(timeId))
            drain()
            assertNull(preferences.get(oldTimeId))
            assertEquals(timed, preferences.get(timeId))
            assertEquals(classic, preferences.get(classicId))
            assertEquals(timeComponent, manager.getAppWidgetInfo(timeId)?.provider)
            assertEquals(classicComponent, manager.getAppWidgetInfo(classicId)?.provider)
        } finally {
            runCatching { host.stopListening() }
            runCatching { host.deleteHost() }
            preferences.remove(oldTimeId)
            if (timeId != AppWidgetManager.INVALID_APPWIDGET_ID) preferences.remove(timeId)
            if (classicId != AppWidgetManager.INVALID_APPWIDGET_ID) preferences.remove(classicId)
            drain()
            shell("appwidget revokebind --package ${app.packageName} --user $userId")
        }
    }

    @Test fun localizedRendererKeepsSavedZoneAndNeutralCaptionAfterDeviceTravel() =
        instrumentation.runOnMainSync {
            val eventZone = ZoneId.of("Europe/Paris")
            val target = ZonedDateTime.now(eventZone).plusDays(4)
                .withHour(16).withMinute(30).withSecond(0).withNano(0)
            val event = CountdownEvent(
                id = "localized-timed", title = "Paris recital",
                date = target.toLocalDate(), eventTime = LocalTime.of(16, 30),
                eventZone = eventZone, type = EventType.EVENT, createdAt = Instant.now(),
            )
            val home = CountdownTimeSnapshot(ZonedDateTime.now(
                ZoneId.of("America/Argentina/Buenos_Aires")))
            val travel = CountdownTimeSnapshot(home.now.withZoneSameInstant(ZoneId.of("Asia/Tokyo")))
            assertEquals(TimedEventResolver.resolve(event, home.now)!!.target.toInstant(),
                TimedEventResolver.resolve(event, travel.now)!!.target.toInstant())
            for (tag in listOf("en", "es", "ca")) {
                val localized = app.createConfigurationContext(
                    Configuration(app.resources.configuration).apply {
                        setLocale(Locale.forLanguageTag(tag))
                    })
                for (snapshot in listOf(home, travel)) {
                    val views = TimeWidgetRenderer.render(
                        localized, localized, 830_060,
                        WidgetConfiguration(event.id, WidgetAppearance.DARK, WidgetBackground.HORIZON),
                        event, null, snapshot, 280, 210, hashMapOf(), interactive = false)
                    val root = views.apply(localized, FrameLayout(localized)) as FrameLayout
                    val caption = root.findViewById<TextView>(R.id.timeWidgetCaption)
                    val date = root.findViewById<TextView>(R.id.timeWidgetDate)
                    val clock = root.findViewById<Chronometer>(R.id.timeWidgetClock)
                    assertEquals(tag, localized.getString(R.string.time_widget_live_timer),
                        caption.text.toString())
                    assertTrue(tag + " target zone missing: " + date.text,
                        date.text.toString().contains("Europe/Paris"))
                    assertEquals(View.VISIBLE, clock.visibility)
                    assertTrue("No native clock for " + tag, clock.text.toString().contains(":"))
                }
            }
        }

    private fun options(width: Int, height: Int) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
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
