package com.santiagorodriguez.countaway.ui

import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.data.CountdownStorageCodec
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountUpPreset
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventIcon
import com.santiagorodriguez.countaway.model.EventType
import org.json.JSONObject
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
class EditorPresetInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val repository = CountdownRepository(context)

    @Test fun neutralCountUpDefaultAndTemplateSurviveRecreationAndSave() = isolated { original ->
        val today = CountdownTime.snapshot().today
        var suggestion = ""
        ActivityScenario.launch<EditorActivity>(Intent(context, EditorActivity::class.java)).use { scenario ->
            drain()
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<Button>(R.id.modeButton).isSelected)
                activity.findViewById<Button>(R.id.countUpModeButton).performClick()
                val grid = activity.findViewById<GridLayout>(R.id.typeGrid)
                assertEquals(9, grid.childCount)
                assertTrue(choice(activity, "up:EVENT").isSelected)
                assertEquals("", activity.findViewById<EditText>(R.id.titleInput).text.toString())
                choice(activity, "up:LEARNING").performClick()
                suggestion = activity.getString(R.string.preset_learning)
                assertEquals(suggestion, activity.findViewById<EditText>(R.id.titleInput).text.toString())
            }
            scenario.recreate()
            drain()
            scenario.onActivity {
                assertTrue(it.findViewById<Button>(R.id.countUpModeButton).isSelected)
                assertTrue(choice(it, "up:LEARNING").isSelected)
                assertEquals(suggestion, it.findViewById<EditText>(R.id.titleInput).text.toString())
                it.findViewById<Button>(R.id.saveButton).performClick()
            }
            drain()
        }
        val created = events().single { candidate -> original.none { it.id == candidate.id } }
        assertEquals(suggestion, created.title)
        assertEquals(today, created.date)
        assertEquals(CountMode.COUNT_UP, created.countMode)
        assertEquals(EventType.CUSTOM, created.type)
        assertEquals(EventIcon.STAR, created.icon)
        assertEquals(listOf(created), CountdownStorageCodec.decode(CountdownStorageCodec.encode(listOf(created))))
        ActivityScenario.launch<EditorActivity>(intent(created)).use { scenario ->
            drain()
            scenario.onActivity {
                assertTrue(choice(it, "up:CUSTOM").isSelected)
                assertEquals(suggestion, it.findViewById<EditText>(R.id.titleInput).text.toString())
            }
        }
    }

    @Test fun typedTitleAndExplicitDateArePreservedAcrossModesAndRecreation() = isolated { original ->
        val tomorrow = CountdownTime.snapshot().today.plusDays(1)
        val title = "My own milestone 👩🏽‍🚀"
        ActivityScenario.launch<EditorActivity>(Intent(context, EditorActivity::class.java)).use { scenario ->
            drain()
            scenario.onActivity {
                it.findViewById<EditText>(R.id.titleInput).setText(title)
                choice(it, "down:EXAM").performClick()
                it.findViewById<Button>(R.id.dateButton).performClick()
            }
            // Confirm the existing date as an explicit choice, even though its value does not change.
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            scenario.recreate()
            drain()
            scenario.onActivity {
                it.findViewById<Button>(R.id.countUpModeButton).performClick()
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.presetPreservedNotice).visibility)
                choice(it, "up:SMOKE_FREE").performClick()
                assertEquals(title, it.findViewById<EditText>(R.id.titleInput).text.toString())
                it.findViewById<Button>(R.id.modeButton).performClick()
                it.findViewById<Button>(R.id.countUpModeButton).performClick()
                assertTrue(choice(it, "up:SMOKE_FREE").isSelected)
                assertEquals(title, it.findViewById<EditText>(R.id.titleInput).text.toString())
                it.findViewById<Button>(R.id.saveButton).performClick()
            }
            drain()
        }
        val created = events().single { candidate -> original.none { it.id == candidate.id } }
        assertEquals(title, created.title)
        assertEquals(tomorrow, created.date)
        assertEquals(EventType.CUSTOM, created.type)
        assertEquals(EventIcon.HEART, created.icon)
    }

    @Test fun legacyCountUpCategoriesAreNotRelabeledWhenOpenedOrSaved() = isolated { original ->
        for (type in EventType.entries) {
            val event = CountdownEvent(UUID.randomUUID().toString(), "Stored ${type.name}",
                LocalDate.of(2024, 2, 29), type, createdAt = Instant.EPOCH, countMode = CountMode.COUNT_UP)
            repository.save(original + event)
            val bytes = File(context.filesDir, "countaways.json").readBytes()
            ActivityScenario.launch<EditorActivity>(intent(event)).use { scenario ->
                drain()
                scenario.recreate()
                drain()
                scenario.onActivity {
                    val generic = type == EventType.EVENT || type == EventType.CUSTOM
                    assertEquals(if (generic) View.GONE else View.VISIBLE,
                        it.findViewById<View>(R.id.presetPreservedNotice).visibility)
                    assertEquals(event.title, it.findViewById<EditText>(R.id.titleInput).text.toString())
                    assertTrue(bytes.contentEquals(File(context.filesDir, "countaways.json").readBytes()))
                    it.findViewById<Button>(R.id.saveButton).performClick()
                }
                drain()
            }
            assertEquals(event, events().single { it.id == event.id })
        }
    }

    @Test fun allTemplatesRoundTripThroughTheUnchangedSchema7Reader() {
        val baselineTypes = setOf("trip", "exam", "party", "birthday", "anniversary", "concert", "deadline", "event", "custom")
        val baselineIcons = setOf("airplane", "book", "confetti", "cake", "heart", "music", "hourglass", "calendar", "star", "gift", "flag", "pin")
        val baselineCustomIcons = setOf("star", "gift", "flag", "pin", "heart", "music", "airplane", "calendar")
        val fixtures = CountUpPreset.entries.map { preset ->
            CountdownEvent(preset.name, "Milestone ${preset.name}", LocalDate.of(2024, 2, 29),
                preset.type, icon = preset.icon, createdAt = Instant.EPOCH, countMode = CountMode.COUNT_UP)
        }
        val payload = CountdownStorageCodec.encode(fixtures)
        val root = JSONObject(payload)
        assertEquals(7, root.getInt("schemaVersion"))
        val entries = root.getJSONArray("events")
        for (index in 0 until entries.length()) {
            val event = entries.getJSONObject(index)
            assertTrue(event.getString("type") in baselineTypes)
            assertTrue(event.getString("iconKey") in baselineIcons)
            if (event.getString("type") == "custom") assertTrue(event.getString("iconKey") in baselineCustomIcons)
            assertFalse(event.has("preset"))
            assertFalse(event.has("template"))
        }
        assertEquals(fixtures, CountdownStorageCodec.decode(payload))
        assertEquals(fixtures, CountdownStorageCodec.decodeForImport(payload))
    }

    private fun choice(activity: EditorActivity, key: String): View {
        val grid = activity.findViewById<GridLayout>(R.id.typeGrid)
        return (0 until grid.childCount).map(grid::getChildAt).single { it.tag == key }
    }

    private fun intent(event: CountdownEvent) = Intent(context, EditorActivity::class.java)
        .putExtra(EditorActivity.EXTRA_EVENT_ID, event.id)

    private fun events() = (repository.loadResult() as CountdownLoadResult.Success).events

    private fun drain() {
        val done = CountDownLatch(1)
        CountdownIo.submit({ Unit }) { done.countDown() }
        assertTrue(done.await(15, TimeUnit.SECONDS))
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
