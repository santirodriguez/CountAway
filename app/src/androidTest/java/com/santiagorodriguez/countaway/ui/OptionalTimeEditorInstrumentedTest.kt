package com.santiagorodriguez.countaway.ui

import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.TimePicker
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
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
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OptionalTimeEditorInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val repository = CountdownRepository(context)

    @Test fun timeIsOffByDefaultAndPickerSavesOnlyWhenExplicitlyChosen() = isolated {
        val event = fixture()
        repository.save(original() + event)
        ActivityScenario.launch<EditorActivity>(intent(event)).use { scenario ->
            drain()
            scenario.onActivity { activity ->
                assertEquals(View.GONE,
                    activity.findViewById<View>(R.id.removeTimeButton).visibility)
                activity.findViewById<Button>(R.id.timeButton).performClick()
            }
            onView(withId(android.R.id.timePicker)).inRoot(isDialog()).check { view, _ ->
                (view as TimePicker).hour = 18
                view.minute = 30
            }
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            scenario.recreate()
            drain()
            scenario.onActivity { activity ->
                assertEquals(View.VISIBLE,
                    activity.findViewById<View>(R.id.removeTimeButton).visibility)
                activity.findViewById<Button>(R.id.saveButton).performClick()
            }
            drain()
            val saved = events().single { it.id == event.id }
            assertEquals(LocalTime.of(18, 30), saved.eventTime)
            assertNotNull(saved.eventZone)
        }
    }

    @Test fun removingTimeDoesNotChangeDateTitleOrIdentity() = isolated {
        val event = fixture().copy(
            eventTime = LocalTime.of(16, 30),
            eventZone = ZoneId.of("America/Argentina/Buenos_Aires"))
        repository.save(original() + event)
        ActivityScenario.launch<EditorActivity>(intent(event)).use { scenario ->
            drain()
            scenario.recreate()
            drain()
            scenario.onActivity { activity ->
                assertEquals(View.VISIBLE,
                    activity.findViewById<View>(R.id.removeTimeButton).visibility)
                activity.findViewById<Button>(R.id.removeTimeButton).performClick()
                assertEquals(View.GONE,
                    activity.findViewById<View>(R.id.removeTimeButton).visibility)
                activity.findViewById<Button>(R.id.saveButton).performClick()
            }
            drain()
            assertEquals(event.copy(eventTime = null, eventZone = null),
                events().single { it.id == event.id })
        }
    }

    private var initialEvents: List<CountdownEvent> = emptyList()
    private fun original(): List<CountdownEvent> = initialEvents
    private fun events(): List<CountdownEvent> =
        (repository.loadResult() as CountdownLoadResult.Success).events
    private fun intent(event: CountdownEvent): Intent = Intent(context, EditorActivity::class.java)
        .putExtra(EditorActivity.EXTRA_EVENT_ID, event.id)
    private fun fixture() = CountdownEvent(UUID.randomUUID().toString(), "Timed editor",
        LocalDate.now().plusDays(10), EventType.EVENT,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"))
    private fun drain() {
        val gate = CountDownLatch(1)
        CountdownIo.submit({ Unit }) { gate.countDown() }
        assertTrue(gate.await(15, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }
    private fun isolated(action: () -> Unit) {
        drain()
        val originals = listOf("countaways.json", "countaways.json.bak", "countaways.json.new")
            .associate { name -> File(context.filesDir, name).let { it to
                if (it.exists()) it.readBytes() else null } }
        initialEvents = events()
        try { action() } finally {
            drain()
            originals.forEach { (file, bytes) ->
                if (bytes == null) file.delete() else file.writeBytes(bytes)
            }
        }
    }
}
