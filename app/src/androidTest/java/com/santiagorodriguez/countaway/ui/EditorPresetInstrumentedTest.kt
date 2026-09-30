package com.santiagorodriguez.countaway.ui

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
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
import org.hamcrest.Matchers.anything
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
            scenario.onActivity { assertMode(it, false) }
            EditorTestActions.chooseMode(scenario, true)
            scenario.onActivity { activity ->
                assertEquals(9, activity.findViewById<GridLayout>(R.id.typeGrid).childCount)
                assertTrue(choice(activity, "up:EVENT").isSelected)
                assertEquals("", title(activity).text.toString())
                choice(activity, "up:READING").performClick()
                suggestion = activity.getString(R.string.preset_reading)
                assertEquals(suggestion, title(activity).text.toString())
                val section = activity.findViewById<ViewGroup>(R.id.customIconSection)
                assertTrue((0 until section.childCount).none { section.getChildAt(it) is GridLayout })
            }
            scenario.recreate()
            drain()
            scenario.onActivity {
                assertMode(it, true)
                assertTrue(choice(it, "up:READING").isSelected)
                assertEquals(suggestion, title(it).text.toString())
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
                assertEquals(suggestion, title(it).text.toString())
            }
        }
        ActivityScenario.launch<EditorActivity>(Intent(context, EditorActivity::class.java)).use { scenario ->
            drain()
            scenario.onActivity { assertMode(it, false) }
        }
    }

    @Test fun switchingTemplatesOnlyReplacesAppSuggestedTitles() = isolated {
        ActivityScenario.launch<EditorActivity>(Intent(context, EditorActivity::class.java)).use { scenario ->
            drain()
            EditorTestActions.chooseMode(scenario, true)
            scenario.onActivity { activity ->
                choice(activity, "up:READING").performClick()
                assertEquals(activity.getString(R.string.preset_reading), title(activity).text.toString())
                choice(activity, "up:PROJECT").performClick()
                assertEquals(activity.getString(R.string.preset_project), title(activity).text.toString())
            }
            scenario.recreate()
            drain()
            scenario.onActivity { activity ->
                choice(activity, "up:EVENT").performClick()
                assertEquals("", title(activity).text.toString())
                title(activity).setText("My own title")
                choice(activity, "up:SMOKE_FREE").performClick()
                assertEquals("My own title", title(activity).text.toString())
            }
        }
    }

    @Test fun editingBackToSuggestedTextTransfersOwnershipAndPreservesSelection() = isolated {
        var expected = ""
        ActivityScenario.launch<EditorActivity>(Intent(context, EditorActivity::class.java)).use { scenario ->
            drain()
            EditorTestActions.chooseMode(scenario, true)
            scenario.onActivity {
                choice(it, "up:READING").performClick()
                val input = title(it)
                expected = input.text.toString()
                input.setText("Edited by the user")
                input.setText(expected)
                input.setSelection(1, 3)
            }
            scenario.recreate()
            drain()
            scenario.onActivity {
                val input = title(it)
                assertEquals(1, input.selectionStart)
                assertEquals(3, input.selectionEnd)
                choice(it, "up:PROJECT").performClick()
                assertEquals(expected, input.text.toString())
                choice(it, "up:EVENT").performClick()
                assertEquals(expected, input.text.toString())
            }
        }
    }

    @Test fun iconDialogCancelPreservesDraftAndExplicitSelectionSurvives() = isolated {
        ActivityScenario.launch<EditorActivity>(Intent(context, EditorActivity::class.java)).use { scenario ->
            drain()
            EditorTestActions.chooseMode(scenario, true)
            var before = ""
            scenario.onActivity {
                choice(it, "up:TRAINING").performClick()
                val button = it.findViewById<Button>(R.id.iconButton)
                before = button.text.toString()
                button.performClick()
            }
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            drain()
            scenario.onActivity {
                val button = it.findViewById<Button>(R.id.iconButton)
                assertEquals(before, button.text.toString())
                assertTrue(choice(it, "up:TRAINING").isSelected)
                button.performClick()
            }
            onData(anything()).inRoot(isDialog()).atPosition(7).perform(click())
            drain()
            scenario.recreate()
            drain()
            scenario.onActivity {
                assertTrue(choice(it, "up:CUSTOM").isSelected)
                assertEquals(it.getString(R.string.editor_change_icon,
                    it.getString(EventIconPresentation.labelRes(EventIcon.CALENDAR))),
                    it.findViewById<Button>(R.id.iconButton).text.toString())
                assertEquals(it.getString(R.string.preset_training), title(it).text.toString())
            }
        }
    }

    @Test fun typedTitleAndExplicitDateArePreservedAcrossModesAndRecreation() = isolated { original ->
        val tomorrow = CountdownTime.snapshot().today.plusDays(1)
        val expected = "My own milestone \uD83D\uDC69\uD83C\uDFFD\u200D\uD83D\uDE80"
        ActivityScenario.launch<EditorActivity>(Intent(context, EditorActivity::class.java)).use { scenario ->
            drain()
            scenario.onActivity {
                title(it).setText(expected)
                choice(it, "down:EXAM").performClick()
                it.findViewById<Button>(R.id.dateButton).performClick()
            }
            // Confirming the unchanged default date is still an explicit choice.
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            scenario.recreate()
            drain()
            EditorTestActions.chooseMode(scenario, true)
            scenario.onActivity {
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.presetPreservedNotice).visibility)
                choice(it, "up:SMOKE_FREE").performClick()
                assertEquals(expected, title(it).text.toString())
            }
            EditorTestActions.chooseMode(scenario, false)
            EditorTestActions.chooseMode(scenario, true)
            scenario.onActivity {
                assertTrue(choice(it, "up:SMOKE_FREE").isSelected)
                assertEquals(expected, title(it).text.toString())
                it.findViewById<Button>(R.id.saveButton).performClick()
            }
            drain()
        }
        val created = events().single { candidate -> original.none { it.id == candidate.id } }
        assertEquals(expected, created.title)
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
                    assertEquals(event.title, title(it).text.toString())
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
        assertEquals(9, CountUpPreset.entries.map(CountUpPresetPresentation::glyphRes).distinct().size)
    }

    private fun assertMode(activity: EditorActivity, up: Boolean) {
        assertEquals(activity.getString(if (up) R.string.count_mode_up else R.string.count_mode_down),
            activity.findViewById<Button>(R.id.modeButton).text.toString())
    }

    private fun title(activity: EditorActivity): EditText = activity.findViewById(R.id.titleInput)

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
