package com.santiagorodriguez.countaway.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.ui.EditorActivity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class TimeWidgetSelectionLifecycleInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val repository = CountdownRepository(app)
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")

    @Test fun deletedOrUntimedSelectionCannotBeSavedAfterResume() = withBoundWidget { widgetId ->
        for (removeEvent in listOf(false, true)) {
            val timed = timedEvent()
            repository.save(listOf(timed))
            ActivityScenario.launch<TimeWidgetConfigActivity>(intent(widgetId)).use { scenario ->
                drain()
                scenario.onActivity { activity ->
                    val list = activity.findViewById<ListView>(R.id.widgetEventList)
                    assertEquals(3, list.count)
                    list.performItemClick(list.getChildAt(1 - list.firstVisiblePosition),
                        1, list.adapter.getItemId(1))
                    assertTrue(activity.findViewById<Button>(R.id.widgetSaveButton).isEnabled)
                }
                scenario.moveToState(Lifecycle.State.CREATED)
                repository.save(if (removeEvent) emptyList() else listOf(timed.copy(eventTime = null)))
                scenario.moveToState(Lifecycle.State.RESUMED)
                drain()
                scenario.onActivity { activity ->
                    val list = activity.findViewById<ListView>(R.id.widgetEventList)
                    assertEquals(2, list.count) // Only header and footer remain.
                    assertEquals(-1, list.checkedItemPosition)
                    assertFalse(activity.findViewById<Button>(R.id.widgetSaveButton).isEnabled)
                    val empty = activity.findViewById<TextView>(R.id.widgetEmptyState)
                    assertEquals(View.VISIBLE, empty.visibility)
                    assertEquals(activity.getString(R.string.time_widget_no_events), empty.text)
                }
            }
            assertNull(WidgetPreferences(app).get(widgetId))
        }
    }

    @Test fun creationReturnsWithoutAnImplicitTimeOrDuplicateEvent() = withBoundWidget { widgetId ->
        repository.save(emptyList())
        ActivityScenario.launch<TimeWidgetConfigActivity>(intent(widgetId)).use { scenario ->
            drain()
            scenario.onActivity { activity ->
                val create = activity.findViewById<Button>(R.id.widgetCreateButton)
                assertEquals(activity.getString(R.string.time_widget_create_event_with_time),
                    create.text.toString())
                assertFalse(activity.findViewById<Button>(R.id.widgetSaveButton).isEnabled)
                create.performClick()
            }
            instrumentation.waitForIdleSync()
            val editor = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<EditorActivity>().singleOrNull()
            assertNotNull("Create must open the ordinary editor", editor)
            instrumentation.runOnMainSync { checkNotNull(editor).finish() }
            drain()
            scenario.onActivity { activity ->
                assertEquals(2, activity.findViewById<ListView>(R.id.widgetEventList).count)
                assertFalse(activity.findViewById<Button>(R.id.widgetSaveButton).isEnabled)
            }
            assertTrue((repository.loadResult() as CountdownLoadResult.Success).events.isEmpty())
        }
    }

    private fun timedEvent() = CountdownEvent(
        id = "timed-widget-lifecycle-" + UUID.randomUUID(),
        title = "Eligible timed event",
        date = LocalDate.now(zone).plusDays(2),
        type = EventType.EVENT, createdAt = Instant.now(),
        eventTime = LocalTime.of(16, 30), eventZone = zone,
    )

    private fun intent(id: Int) = Intent(app, TimeWidgetConfigActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)

    private fun withBoundWidget(test: (Int) -> Unit) {
        val original = (repository.loadResult() as CountdownLoadResult.Success).events
        val host = AppWidgetHost(app, 1_240_059)
        val manager = AppWidgetManager.getInstance(app)
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
            test(widgetId)
        } finally {
            runCatching { host.stopListening() }
            runCatching { host.deleteHost() }
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) WidgetPreferences(app).remove(widgetId)
            repository.save(original)
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
