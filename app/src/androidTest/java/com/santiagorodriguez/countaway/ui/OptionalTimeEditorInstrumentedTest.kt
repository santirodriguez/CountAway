package com.santiagorodriguez.countaway.ui

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
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
            var offeredTime: LocalTime? = null
            var pickerFocusBeforeConfirm = ""
            onView(isAssignableFrom(TimePicker::class.java)).inRoot(isDialog()).check { view, _ ->
                val picker = view as TimePicker
                offeredTime = LocalTime.of(picker.hour, picker.minute)
                assertTrue("A new native picker must expose a valid initial time",
                    picker.validateInput())
                val focused = picker.findFocus()
                pickerFocusBeforeConfirm = "is24Hour=${picker.is24HourView}; " +
                    "focused=${focused?.javaClass?.simpleName}; shown=${focused?.isShown}"
            }
            val expectedTime = requireNotNull(offeredTime)
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            scenario.onActivity { activity ->
                assertEditorTime(activity, expectedTime, "after dialog confirmation ($pickerFocusBeforeConfirm)")
            }
            scenario.recreate()
            drain()
            scenario.onActivity { activity ->
                assertEditorTime(activity, expectedTime, "after Activity recreation")
                activity.findViewById<Button>(R.id.saveButton).performClick()
            }
            drain()
            val saved = events().single { it.id == event.id }
            assertEquals("after repository save", expectedTime, saved.eventTime)
            assertNotNull(saved.eventZone)
        }
    }

    @Test fun midnightAndNoonRemainsConfirmableInNativeTimePicker() = isolated {
        val base = fixture().copy(eventZone = ZoneId.of("America/Argentina/Buenos_Aires"))
        for (hour in listOf(0, 12)) {
            val selected = LocalTime.of(hour, 20)
            val event = base.copy(eventTime = selected)
            repository.save(original() + event)
            ActivityScenario.launch<EditorActivity>(intent(event)).use { scenario ->
                drain()
                scenario.onActivity { activity ->
                    activity.findViewById<Button>(R.id.timeButton).performClick()
                }
                onView(isAssignableFrom(TimePicker::class.java)).inRoot(isDialog()).check { view, error ->
                    if (error != null) throw error
                    val picker = view as TimePicker
                    assertEquals("Initial native hour", hour, picker.hour)
                    assertEquals("Initial native minute", 20, picker.minute)
                    assertTrue("Native time should validate when no edits were entered", picker.validateInput())
                }
                onView(withId(android.R.id.button1)).inRoot(isDialog()).check { view, error ->
                    if (error != null) throw error
                    assertTrue("A valid initial time must permit confirmation", view.isEnabled)
                }.perform(click())
                scenario.onActivity { activity ->
                    assertEditorTime(activity, selected, "after native confirmation at $hour:20")
                }
            }
        }
    }

    @Test fun afternoonTimeSurvivesNativePickerAndRecreation() = isolated {
        val event = fixture().copy(eventTime = LocalTime.of(18, 30),
            eventZone = ZoneId.of("America/Argentina/Buenos_Aires"))
        repository.save(original() + event)
        ActivityScenario.launch<EditorActivity>(intent(event)).use { scenario ->
            drain()
            scenario.onActivity { activity ->
                activity.findViewById<Button>(R.id.timeButton).performClick()
            }
            onView(isAssignableFrom(TimePicker::class.java)).inRoot(isDialog()).check { view, _ ->
                val picker = view as TimePicker
                assertEquals(18, picker.hour)
                assertEquals(30, picker.minute)
            }
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            scenario.onActivity { activity ->
                assertEditorTime(activity, LocalTime.of(18, 30), "existing time after OK")
            }
            scenario.recreate()
            drain()
            scenario.onActivity { activity ->
                assertEquals(View.VISIBLE,
                    activity.findViewById<View>(R.id.removeTimeButton).visibility)
                activity.findViewById<Button>(R.id.saveButton).performClick()
            }
            drain()
            assertEquals(event, events().single { it.id == event.id })
        }
    }

    @Test fun changingExistingEveningTimePersistsAfterRecreation() = isolated {
        val event = fixture().copy(eventTime = LocalTime.of(18, 30),
            eventZone = ZoneId.of("America/Argentina/Buenos_Aires"))
        repository.save(original() + event)
        ActivityScenario.launch<EditorActivity>(intent(event)).use { scenario ->
            drain()
            scenario.onActivity { activity ->
                activity.findViewById<Button>(R.id.timeButton).performClick()
            }
            onView(isAssignableFrom(TimePicker::class.java)).inRoot(isDialog()).check { view, _ ->
                val picker = view as TimePicker
                picker.hour = 21
                picker.minute = 45
                assertEquals(21, picker.hour)
                assertEquals(45, picker.minute)
                assertTrue("A valid changed hour must pass the native pre-focus check",
                    picker.validateInput())
            }
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            scenario.onActivity { activity ->
                assertEditorTime(activity, LocalTime.of(21, 45), "changed time after OK")
            }
            scenario.recreate()
            drain()
            scenario.onActivity { activity ->
                assertEditorTime(activity, LocalTime.of(21, 45), "changed time after recreation")
                activity.findViewById<Button>(R.id.saveButton).performClick()
            }
            drain()
            assertEquals(event.copy(eventTime = LocalTime.of(21, 45)),
                events().single { it.id == event.id })
        }
    }

    @Test fun selectingANonexistentDstDateTimeDoesNotSave() = isolated {
        // Start with a valid 02:30. Changing only the date into a DST gap must
        // reject the edited event without silently normalizing the wall time.
        // Use DatePicker so this test cannot alter TimePicker state across tests.
        val event = fixture().copy(date = LocalDate.of(2026, 3, 9),
            eventTime = LocalTime.of(2, 30), eventZone = ZoneId.of("America/New_York"))
        repository.save(original() + event)
        ActivityScenario.launch<EditorActivity>(intent(event)).use { scenario ->
            drain()
            scenario.onActivity { activity ->
                activity.findViewById<Button>(R.id.dateButton).performClick()
            }
            onView(isAssignableFrom(DatePicker::class.java)).inRoot(isDialog()).check { view, _ ->
                val picker = view as DatePicker
                picker.updateDate(2026, 2, 8)
                assertEquals(2026, picker.year)
                assertEquals(2, picker.month)
                assertEquals(8, picker.dayOfMonth)
            }
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            scenario.onActivity { activity ->
                activity.findViewById<Button>(R.id.saveButton).performClick()
            }
            drain()
            assertEquals(event, events().single { it.id == event.id })
            scenario.onActivity { activity -> assertFalse(activity.isFinishing) }
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

    @Test fun timeControlsStayInsideTheirRowWithOrWithoutSelectedTime() = isolated {
        val dateOnly = fixture()
        repository.save(original() + dateOnly)
        ActivityScenario.launch<EditorActivity>(intent(dateOnly)).use { scenario ->
            drain()
            scenario.onActivity { activity ->
                assertTimeControlsContained(activity, "date-only")
            }
        }

        val timed = dateOnly.copy(eventTime = LocalTime.of(18, 30),
            eventZone = ZoneId.of("America/Argentina/Buenos_Aires"))
        repository.save(original() + timed)
        ActivityScenario.launch<EditorActivity>(intent(timed)).use { scenario ->
            drain()
            scenario.onActivity { activity ->
                assertTimeControlsContained(activity, "timed")
            }
        }
    }

    private fun assertTimeControlsContained(activity: EditorActivity, stage: String) {
        val row = activity.findViewById<ViewGroup>(R.id.timeControlsRow)
        assertTrue("$stage: time controls row was not laid out", row.height > 0)
        // A child may fit inside its parent and still be clipped by its padding.
        val contentTop = if (row.clipToPadding) row.paddingTop else 0
        val contentBottom = if (row.clipToPadding) row.height - row.paddingBottom else row.height
        for (id in listOf(R.id.timeButton, R.id.removeTimeButton)) {
            val control = activity.findViewById<View>(id)
            if (control.visibility != View.VISIBLE) continue
            assertTrue(
                "$stage: control $id is clipped by the time row: top=${control.top}, " +
                    "bottom=${control.bottom}, visibleContent=$contentTop..$contentBottom",
                control.top >= contentTop && control.bottom <= contentBottom,
            )
        }
    }

    private fun assertEditorTime(activity: EditorActivity, expected: LocalTime, stage: String) {
        val formatted = EventTimeText.time(activity, expected)
        val label = activity.findViewById<Button>(R.id.timeButton).text.toString()
        assertEquals("$stage: time label",
            activity.getString(R.string.editor_selected_time, formatted), label)
        assertEquals("$stage: remove-time visibility", View.VISIBLE,
            activity.findViewById<View>(R.id.removeTimeButton).visibility)
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
