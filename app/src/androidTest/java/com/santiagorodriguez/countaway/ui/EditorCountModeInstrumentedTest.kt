package com.santiagorodriguez.countaway.ui

import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.ReminderOption
import com.santiagorodriguez.countaway.model.RepeatRule
import com.santiagorodriguez.countaway.notification.ArrivalNotificationState
import com.santiagorodriguez.countaway.widget.WidgetAppearance
import com.santiagorodriguez.countaway.widget.WidgetBackground
import com.santiagorodriguez.countaway.widget.WidgetEventSelection
import com.santiagorodriguez.countaway.widget.WidgetPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EditorCountModeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val repository = CountdownRepository(context)

    @Test fun pendingConversionSurvivesRecreationWithoutApprovalOrPersistence() = isolated { original ->
        val event = fixture().copy(repeatRule = RepeatRule.WEEKLY, reminder = ReminderOption.ON_DAY)
        repository.save(original + event)
        val bytes = storage().readBytes()
        ActivityScenario.launch<EditorActivity>(intent(event)).use { scenario ->
            drain()
            chooseMode(scenario, 1)
            scenario.recreate()
            drain()
            onView(withText(R.string.count_mode_confirm_title)).inRoot(isDialog()).check(matches(isDisplayed()))
            scenario.onActivity {
                assertTrue(it.findViewById<Button>(R.id.modeButton).isSelected)
                assertFalse(it.findViewById<Button>(R.id.countUpModeButton).isSelected)
            }
            assertTrue(bytes.contentEquals(storage().readBytes()))
            dialogButton(android.R.id.button2)
            assertEquals(event, events().single { it.id == event.id })
            chooseMode(scenario, 1)
            dialogButton(android.R.id.button1)
            scenario.onActivity {
                assertEquals(View.GONE, it.findViewById<View>(R.id.repeatSection).visibility)
                assertEquals(View.GONE, it.findViewById<View>(R.id.reminderSection).visibility)
            }
            assertTrue(bytes.contentEquals(storage().readBytes()))
            chooseMode(scenario, 0)
            scenario.onActivity {
                assertEquals(0, it.findViewById<Spinner>(R.id.repeatSpinner).selectedItemPosition)
                assertEquals(0, it.findViewById<Spinner>(R.id.reminderSpinner).selectedItemPosition)
            }
            assertTrue(bytes.contentEquals(storage().readBytes()))
        }
    }

    @Test fun confirmedSaveKeepsIdentityWidgetStyleAndClearsDeliveryOnlyAfterSave() = isolated { original ->
        val event = fixture().copy(reminder = ReminderOption.ON_DAY, repeatRule = RepeatRule.MONTHLY)
        repository.save(original + event)
        val delivery = ArrivalNotificationState(context)
        delivery.markDelivered(event, event.date)
        val preferences = WidgetPreferences(context)
        val id = (1_800_000_000..1_800_000_100).first { preferences.get(it) == null }
        preferences.save(id, event.id, WidgetAppearance.DARK, WidgetBackground.MONOGRAM, WidgetEventSelection.FIXED)
        val configuration = preferences.get(id)
        try {
            ActivityScenario.launch<EditorActivity>(intent(event)).use { scenario ->
                drain()
                chooseMode(scenario, 1)
                dialogButton(android.R.id.button1)
                assertEquals(event, events().single { it.id == event.id })
                assertEquals(event.date, delivery.deliveredDate(event.id))
                scenario.onActivity { it.findViewById<Button>(R.id.saveButton).performClick() }
                drain()
                assertEquals(event.copy(countMode = CountMode.COUNT_UP, reminder = ReminderOption.OFF,
                    repeatRule = RepeatRule.NONE), events().single { it.id == event.id })
                assertEquals(configuration, preferences.get(id))
                assertNull(delivery.deliveredDate(event.id))
            }
        } finally { preferences.remove(id); delivery.remove(event.id) }
    }

    @Test fun historicalTitleAndEmojiSurviveModeChangeAndRecreation() = isolated { original ->
        val title = "Historical 👩🏽‍🚀 " + "long title ".repeat(30) + "end"
        val event = fixture().copy(title = title)
        repository.save(original + event)
        ActivityScenario.launch<EditorActivity>(intent(event)).use { scenario ->
            drain()
            chooseMode(scenario, 1)
            scenario.recreate()
            drain()
            scenario.onActivity {
                assertEquals(title, it.findViewById<EditText>(R.id.titleInput).text.toString())
                assertTrue(it.findViewById<Button>(R.id.countUpModeButton).isSelected)
                assertFalse(it.findViewById<Button>(R.id.modeButton).isSelected)
                it.findViewById<Button>(R.id.saveButton).performClick()
            }
            drain()
            assertEquals(event.copy(countMode = CountMode.COUNT_UP), events().single { it.id == event.id })
        }
    }

    @Test fun modeOnlyDraftIsDirtyAndFailedOrConflictingSaveKeepsStoredState() = isolated { original ->
        val event = fixture()
        repository.save(original + event)
        val delivery = ArrivalNotificationState(context)
        delivery.markDelivered(event, event.date)
        try {
            ActivityScenario.launch<EditorActivity>(intent(event)).use { scenario ->
                drain()
                chooseMode(scenario, 1)
                closeSoftKeyboard()
                pressBack()
                onView(withText(R.string.unsaved_changes_title)).inRoot(isDialog()).check(matches(isDisplayed()))
                dialogButton(android.R.id.button2)
                val newer = event.copy(title = "Concurrent change")
                repository.save(original + newer)
                val newerBytes = storage().readBytes()
                scenario.onActivity { it.findViewById<Button>(R.id.saveButton).performClick() }
                drain()
                onView(withText(R.string.data_conflict_title)).inRoot(isDialog()).check(matches(isDisplayed()))
                assertTrue(newerBytes.contentEquals(storage().readBytes()))
                assertEquals(event.date, delivery.deliveredDate(event.id))
                dialogButton(android.R.id.button2)
                storage().writeText("corrupt test fixture")
                val corruptBytes = storage().readBytes()
                scenario.onActivity { it.findViewById<Button>(R.id.saveButton).performClick() }
                drain()
                assertTrue(corruptBytes.contentEquals(storage().readBytes()))
                assertEquals(event.date, delivery.deliveredDate(event.id))
            }
        } finally { delivery.remove(event.id) }
    }

    private fun chooseMode(scenario: ActivityScenario<EditorActivity>, position: Int) {
        scenario.onActivity {
            it.findViewById<Button>(if (position == 1) R.id.countUpModeButton else R.id.modeButton).performClick()
        }
        instrumentation.waitForIdleSync()
    }

    private fun dialogButton(id: Int) {
        onView(withId(id)).inRoot(isDialog()).perform(click())
        instrumentation.waitForIdleSync()
    }

    private fun intent(event: CountdownEvent) = Intent(context, EditorActivity::class.java)
        .putExtra(EditorActivity.EXTRA_EVENT_ID, event.id)

    private fun fixture() = CountdownEvent(UUID.randomUUID().toString(), "Mode fixture",
        LocalDate.now().plusDays(10), EventType.EVENT, createdAt = Instant.parse("2026-01-01T00:00:00Z"))

    private fun storage() = File(context.filesDir, "countaways.json")
    private fun events() = (repository.loadResult() as CountdownLoadResult.Success).events

    private fun drain() {
        val latch = CountDownLatch(1)
        CountdownIo.submit({ Unit }) { latch.countDown() }
        assertTrue(latch.await(15, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }

    private fun isolated(test: (List<CountdownEvent>) -> Unit) {
        drain()
        val files = listOf("countaways.json", "countaways.json.bak", "countaways.json.new")
            .associate { name -> File(context.filesDir, name).let { it to if (it.isFile) it.readBytes() else null } }
        val original = events()
        try { test(original) } finally {
            drain()
            files.forEach { (file, bytes) -> if (bytes == null) file.delete() else file.writeBytes(bytes) }
        }
    }
}
