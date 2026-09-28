package com.santiagorodriguez.countaway.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.ListView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WidgetConfigLifecycleInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val repository = CountdownRepository(context)

    @Test fun realConfigurationRestoresEmptySingleAndLongLists() = withWidget { id ->
        for (count in listOf(0, 1, 1000)) {
            repository.save((0 until count).map { event(it) })
            ActivityScenario.launch<WidgetConfigActivity>(intent(id)).use { scenario ->
                drain()
                val selectedPosition = count + 1 // Header, Next, then events.
                scenario.onActivity { activity ->
                    val list = activity.findViewById<ListView>(R.id.widgetEventList)
                    assertEquals(count + 3, list.count) // Header and footer plus Next.
                    assertTrue("List must recycle", list.childCount < 50)
                    list.setSelection(selectedPosition)
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val list = activity.findViewById<ListView>(R.id.widgetEventList)
                    val row = list.getChildAt(selectedPosition - list.firstVisiblePosition)
                    assertNotNull("Selected row must be reachable", row)
                    list.performItemClick(row, selectedPosition, list.adapter.getItemId(selectedPosition))
                }
                scenario.recreate()
                drain()
                scenario.onActivity { activity ->
                    val list = activity.findViewById<ListView>(R.id.widgetEventList)
                    assertEquals(selectedPosition, list.checkedItemPosition)
                    assertTrue(list.firstVisiblePosition <= selectedPosition)
                    assertTrue(list.lastVisiblePosition >= selectedPosition)
                    list.setSelection(list.count - 1)
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val save = activity.findViewById<android.widget.Button>(R.id.widgetSaveButton)
                    assertNotNull("Footer must be reachable after rotation", save)
                    assertTrue(save.isEnabled)
                }
                capture("activity-config-$count")
                scenario.onActivity { activity ->
                    val value = activity.findViewById<android.widget.TextView>(R.id.widgetCount)
                    val layout = requireNotNull(value.layout)
                    val available = value.width - value.compoundPaddingLeft - value.compoundPaddingRight
                    val textWidth = value.paint.measureText(value.text.toString())
                    val summary = "Activity preview events=$count text=${value.text} width=$available needed=$textWidth height=${value.height} neededHeight=${layout.height} ellipsis=${layout.getEllipsisCount(0)}"
                    android.util.Log.i("LayoutMeasurement", summary)
                    if (count == 0) assertEquals("—", value.text.toString())
                    assertEquals(summary, 0, layout.getEllipsisCount(0))
                    assertTrue(summary, textWidth <= available)
                    assertTrue(summary, layout.height <= value.height)
                }
            }
        }
    }

    @Test fun reloadKeepsTheCheckedEventWhenAnEarlierEventIsInserted() = withWidget { id ->
        val selected = event(1)
        repository.save(listOf(selected))
        WidgetPreferences(context).save(id, selected.id, WidgetAppearance.SYSTEM,
            WidgetBackground.MONOGRAM, WidgetEventSelection.FIXED)
        ActivityScenario.launch<WidgetConfigActivity>(intent(id)).use { scenario ->
            drain()
            scenario.moveToState(Lifecycle.State.CREATED)
            repository.save(listOf(event(0), selected))
            scenario.moveToState(Lifecycle.State.RESUMED)
            drain()
            scenario.onActivity { activity ->
                val list = activity.findViewById<ListView>(R.id.widgetEventList)
                assertEquals("Selection must follow identity, not the old row index", 3, list.checkedItemPosition)
                assertTrue(list.adapter.getItem(list.checkedItemPosition).toString().startsWith(selected.title))
            }
        }
    }

    private fun withWidget(block: (Int) -> Unit) {
        val original = (repository.loadResult() as CountdownLoadResult.Success).events
        val host = AppWidgetHost(context, 120023)
        val manager = AppWidgetManager.getInstance(context)
        val id = host.allocateAppWidgetId()
        val userId = shell("am get-current-user").toInt()
        try {
            // The widget service needs a concrete user ID, not the shell's USER_CURRENT sentinel.
            val output = shell("appwidget grantbind --package ${context.packageName} --user $userId")
            assertTrue("Widget binding setup failed: $output", output.isBlank())
            assertTrue("Cannot bind widget $id for user $userId", manager.bindAppWidgetIdIfAllowed(id,
                ComponentName(context, CountdownWidgetProvider::class.java), Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 160)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 100)
                }))
            drain()
            block(id)
        } finally {
            drain()
            host.deleteAppWidgetId(id)
            WidgetPreferences(context).remove(id)
            shell("appwidget revokebind --package ${context.packageName} --user $userId")
            repository.save(original)
        }
    }

    private fun intent(id: Int) = Intent(context, WidgetConfigActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)

    private fun event(index: Int) = CountdownEvent("config-$index", "Journey ${index + 1}",
        LocalDate.now().plusDays(index.toLong() + 20), EventType.CUSTOM, createdAt = Instant.EPOCH)

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes().toString(Charsets.UTF_8).trim() }

    private fun drain() {
        val done = CountDownLatch(1)
        CountdownIo.submit({ Unit }) { done.countDown() }
        assertTrue(done.await(15, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }

    private fun capture(name: String) {
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val file = File(context.filesDir, "layout-evidence/$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
