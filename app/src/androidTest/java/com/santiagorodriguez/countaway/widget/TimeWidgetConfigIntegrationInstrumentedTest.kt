package com.santiagorodriguez.countaway.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Button
import android.widget.ListView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** The time provider has its own fixed-event configuration and saves one bound instance. */
class TimeWidgetConfigIntegrationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val repository = CountdownRepository(app)

    @Test fun timedPickerFiltersDateOnlyAndPersistsAcrossActivityRecreation() {
        val before = (repository.loadResult() as CountdownLoadResult.Success).events
        val host = AppWidgetHost(app, 1_240_057)
        val manager = AppWidgetManager.getInstance(app)
        val preferences = WidgetPreferences(app)
        val userId = shell("am get-current-user").toInt()
        var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        try {
            assertTrue(shell("appwidget grantbind --package ${app.packageName} --user $userId").isBlank())
            host.deleteHost()
            widgetId = host.allocateAppWidgetId()
            assertTrue(manager.bindAppWidgetIdIfAllowed(widgetId,
                ComponentName(app, TimeWidgetProvider::class.java), Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 240)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
                }))
            val zone = ZoneId.of("America/Argentina/Buenos_Aires")
            val timed = CountdownEvent(
                id = "time-config-" + UUID.randomUUID(), title = "Timed option",
                date = LocalDate.now(zone).plusDays(4),
                type = EventType.EVENT, createdAt = Instant.now(),
                eventTime = LocalTime.of(16, 30), eventZone = zone,
            )
            val dateOnly = CountdownEvent(
                id = "date-config-" + UUID.randomUUID(), title = "Date-only option",
                date = LocalDate.now(zone).plusDays(3),
                type = EventType.EVENT, createdAt = Instant.now(),
            )
            repository.save(before + timed + dateOnly)
            ActivityScenario.launch<TimeWidgetConfigActivity>(
                Intent(app, TimeWidgetConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            ).use { scenario ->
                drain()
                scenario.onActivity { activity ->
                    val list = activity.findViewById<ListView>(R.id.widgetEventList)
                    // Bound time widget: header, exactly one timed option, footer.
                    // This test's fixture must not leak into other instrumentation cases.
                    assertEquals(3, list.count)
                    assertTrue(list.adapter.getItem(1).toString().contains("Timed option"))
                    assertFalse(list.adapter.getItem(1).toString().contains("Date-only option"))
                    list.performItemClick(list.getChildAt(1 - list.firstVisiblePosition),
                        1, list.adapter.getItemId(1))
                    assertTrue(activity.findViewById<Button>(R.id.widgetSaveButton).isEnabled)
                }
                scenario.recreate()
                drain()
                scenario.onActivity { activity ->
                    val list = activity.findViewById<ListView>(R.id.widgetEventList)
                    assertEquals(1, list.checkedItemPosition)
                    assertTrue(activity.findViewById<Button>(R.id.widgetSaveButton).isEnabled)
                    activity.findViewById<Button>(R.id.widgetSaveButton).performClick()
                }
            }
            drain()
            val stored = requireNotNull(preferences.get(widgetId))
            assertEquals(timed.id, stored.eventId)
            assertEquals(WidgetEventSelection.FIXED, stored.eventSelection)
            assertEquals(WidgetBackground.HORIZON, stored.background)
        } finally {
            runCatching { host.stopListening() }
            runCatching { host.deleteHost() }
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) preferences.remove(widgetId)
            repository.save(before)
            drain()
            shell("appwidget revokebind --package ${app.packageName} --user $userId")
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
