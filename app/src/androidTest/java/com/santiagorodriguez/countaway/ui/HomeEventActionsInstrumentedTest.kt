package com.santiagorodriguez.countaway.ui

import android.app.Instrumentation
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.data.EventRevision
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventIcon
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.ReminderOption
import com.santiagorodriguez.countaway.model.RepeatRule
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class HomeEventActionsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val repository = CountdownRepository(context)

    @Test fun actionsPreserveTextAndExistingGesturesAcrossLayouts() {
        instrumentation.runOnMainSync {
            var checked = 0
            for (locale in listOf("en", "es", "ca")) for (dark in listOf(false, true)) {
                for (scale in listOf(1f, 1.3f, 2f)) for (width in listOf(320, 411)) {
                    val config = Configuration(context.resources.configuration).apply {
                        setLocale(Locale.forLanguageTag(locale))
                        fontScale = scale
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                            if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    }
                    val themed = ContextThemeWrapper(context.createConfigurationContext(config), context.applicationInfo.theme)
                    val event = fixture().copy(title = "Reading together 🧩 for our next project")
                    var edits = 0
                    var widgets = 0
                    var menus = 0
                    val plain = CountdownEventAdapter(themed)
                    val actions = CountdownEventAdapter(themed, { widgets++ }, { edits++ }, { _, _ -> menus++ })
                    listOf(plain, actions).forEach { it.submit(listOf(event), CountdownTime.snapshot().today) }
                    val parent = FrameLayout(themed)
                    val before = plain.getView(0, null, parent)
                    val after = actions.getView(0, null, parent)
                    measure(themed, before, width)
                    measure(themed, after, width)
                    val button = after.findViewById<View>(R.id.eventActions)
                    for (id in listOf(R.id.eventTitle, R.id.eventMeta, R.id.eventStatus)) {
                        val old = before.findViewById<TextView>(id)
                        val current = after.findViewById<TextView>(id)
                        assertEquals(old.text.toString(), current.text.toString())
                        // A dedicated native edge target may reflow content, but
                        // must never shrink, clip or truncate it to make room.
                        assertEquals(old.textSize, current.textSize, 0.001f)
                        assertTrue("Text height clipped", current.layout.height <=
                            current.height - current.compoundPaddingTop - current.compoundPaddingBottom)
                        for (line in 0 until current.layout.lineCount) {
                            assertEquals("Text must remain complete", 0, current.layout.getEllipsisCount(line))
                            assertTrue("Text exceeds its measured width", current.layout.getLineMax(line) <=
                                current.width - current.compoundPaddingLeft - current.compoundPaddingRight + 1f)
                            val end = current.layout.getLineEnd(line)
                            if (line < current.layout.lineCount - 1 && end in 1 until current.text.length) {
                                assertFalse("Word split", current.text[end - 1].isLetterOrDigit() && current.text[end].isLetterOrDigit())
                            }
                        }
                        assertFalse("Menu overlaps text", Rect.intersects(
                            Rect(button.left, button.top, button.right, button.bottom),
                            Rect(current.left, current.top, current.right, current.bottom)))
                    }
                    assertEquals(dp(themed, 20), button.width)
                    assertTrue(button.height >= dp(themed, 48))
                    assertEquals(after.width, button.right)
                    assertTrue(after.performClick())
                    assertTrue(after.performLongClick())
                    assertTrue(button.performClick())
                    assertEquals(1, edits)
                    assertEquals(1, widgets)
                    assertEquals(1, menus)
                    capture(after, "home-actions-$locale-$dark-$scale-$width")
                    checked++
                }
            }
            assertEquals(36, checked)
        }
    }

    @Test fun disabledAncestorBlocksRowMenuAndAccessibilityActionsUntilReenabled() {
        instrumentation.runOnMainSync {
            val event = fixture()
            var edits = 0
            var widgets = 0
            var menus = 0
            val adapter = CountdownEventAdapter(context, { widgets++ }, { edits++ }, { _, _ -> menus++ })
            adapter.submit(listOf(event), CountdownTime.snapshot().today)
            val parent = FrameLayout(context)
            val row = adapter.getView(0, null, parent)
            parent.addView(row)
            val button = row.findViewById<View>(R.id.eventActions)

            parent.isEnabled = false
            row.performClick()
            row.performLongClick()
            button.performClick()
            row.performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null)
            assertEquals(0, edits)
            assertEquals(0, widgets)
            assertEquals(0, menus)

            parent.isEnabled = true
            assertTrue(row.performClick())
            assertTrue(row.performLongClick())
            assertTrue(button.performClick())
            assertTrue(row.performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null))
            assertEquals(1, edits)
            assertEquals(2, widgets)
            assertEquals(1, menus)
        }
    }

    @Test fun menuOffersFiveActionsAndDeleteIsCancellableAndConflictSafe() = isolated {
        val source = fixture()
        repository.save(listOf(source))
        val originalBytes = File(context.filesDir, "countaways.json").readBytes()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            drain()
            var deleteLabel = ""
            var labels = emptyList<String>()
            // This assertion validates keyboard/non-touch initial focus. Release acceptance
            // performs real upgrade taps before instrumentation on API 33, which intentionally
            // leaves Android in touch mode; touch-opened menus must not be forced to show focus.
            instrumentation.setInTouchMode(false)
            scenario.onActivity {
                deleteLabel = it.getString(R.string.action_delete)
                labels = listOf(R.string.event_action_widget, R.string.event_action_edit,
                    R.string.event_action_duplicate, R.string.action_share,
                    R.string.action_delete).map(it::getString)
                actionButton(it).performClick()
            }
            onView(withId(R.id.eventActionsPopup)).check(matches(isDisplayed()))
            onView(withId(R.id.eventActionsPopupTitle)).check(matches(withText(source.title)))
            onView(withId(R.id.home_event_widget)).check { widget, error ->
                if (error != null) throw error
                val edit = widget.rootView.findViewById<View>(R.id.home_event_edit)
                val duplicate = widget.rootView.findViewById<View>(R.id.home_event_duplicate)
                assertTrue("Add widget must appear above Edit", widget.top < edit.top)
                val share = widget.rootView.findViewById<View>(R.id.home_event_share)
                val delete = widget.rootView.findViewById<View>(R.id.home_event_delete)
                assertTrue("Edit must appear above Duplicate", edit.top < duplicate.top)
                assertTrue("Duplicate must appear above Share", duplicate.top < share.top)
                assertTrue("Share must appear above Delete", share.top < delete.top)
                assertFalse("Keyboard-focus assertion unexpectedly ran in touch mode", widget.isInTouchMode)
                assertTrue("Add widget must receive initial popup focus in keyboard mode", widget.hasFocus())
            }
            for (id in listOf(R.id.home_event_edit, R.id.home_event_duplicate,
                R.id.home_event_widget, R.id.home_event_share, R.id.home_event_delete)) {
                onView(withId(id)).check(matches(isDisplayed()))
            }
            labels.forEach { onView(withText(it)).check(matches(isDisplayed())) }
            onView(withText(deleteLabel)).perform(click())
            onView(withId(android.R.id.button1)).inRoot(isDialog()).check { view, error ->
                if (error != null) throw error
                assertEquals(view.context.getColor(R.color.danger), (view as Button).currentTextColor)
            }
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            drain()
            assertArrayEquals(originalBytes, File(context.filesDir, "countaways.json").readBytes())
            scenario.onActivity { actionButton(it).performClick() }
            onView(withText(deleteLabel)).perform(click())
            val changed = source.copy(title = "Changed elsewhere")
            repository.save(listOf(changed))
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            drain()
            assertEquals(listOf(changed), events())
            scenario.onActivity { actionButton(it).performClick() }
            onView(withText(deleteLabel)).perform(click())
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            drain()
            assertTrue(events().isEmpty())
        }
    }

    @Test fun aShortMenuViewportCanScrollToTheFinalDeleteAction() = instrumentation.runOnMainSync {
        val theme = ContextThemeWrapper(context, context.applicationInfo.theme)
        val popup = LayoutInflater.from(theme).inflate(R.layout.event_actions_popup, null) as ScrollView
        val width = View.MeasureSpec.makeMeasureSpec(dp(theme, 236), View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(dp(theme, 235), View.MeasureSpec.EXACTLY)
        popup.measure(width, height)
        popup.layout(0, 0, popup.measuredWidth, popup.measuredHeight)
        val share = popup.findViewById<View>(R.id.home_event_share)
        val delete = popup.findViewById<View>(R.id.home_event_delete)
        assertTrue("Share should be above Delete", share.top < delete.top)
        assertTrue("The compact viewport must need scrolling", popup.getChildAt(0).height > popup.height)
        popup.scrollTo(0, popup.getChildAt(0).height - popup.height)
        assertTrue("Delete remains clipped at the bottom",
            delete.bottom <= popup.scrollY + popup.height)
    }

    @Test fun sharingFromHomeUsesNativeChooserWithoutMutatingEitherCountMode() = isolated {
        for (mode in CountMode.entries) {
            val event = fixture().copy(
                countMode = mode,
                repeatRule = if (mode == CountMode.COUNT_DOWN) RepeatRule.MONTHLY else RepeatRule.NONE,
            )
            repository.save(listOf(event))
            val unchanged = File(context.filesDir, "countaways.json").readBytes()
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                drain()
                // Blocking chooser monitors must not intercept ActivityScenario startup.
                val chooserMonitor = Instrumentation.ActivityMonitor(IntentFilter(Intent.ACTION_CHOOSER), null, true)
                instrumentation.addMonitor(chooserMonitor)
                try {
                    scenario.onActivity { actionButton(it).performClick() }
                    onView(withId(R.id.home_event_share)).perform(click())
                    drain()
                    assertTrue("Share did not request Android's native chooser",
                        instrumentation.checkMonitorHit(chooserMonitor, 1))
                    assertArrayEquals("Sharing must not write stored events",
                        unchanged, File(context.filesDir, "countaways.json").readBytes())
                } finally {
                    instrumentation.removeMonitor(chooserMonitor)
                }
            }
        }
    }

    @Test fun duplicateSurvivesRecreationAndSavesOnlyOneIndependentRecord() = isolated {
        for (mode in CountMode.entries) {
            val source = fixture().copy(countMode = mode,
                repeatRule = if (mode == CountMode.COUNT_DOWN) RepeatRule.YEARLY else RepeatRule.NONE,
                reminder = if (mode == CountMode.COUNT_DOWN) ReminderOption.ONE_DAY else ReminderOption.OFF)
            repository.save(listOf(source))
            val originalBytes = File(context.filesDir, "countaways.json").readBytes()
            ActivityScenario.launch<EditorActivity>(copyIntent(source)).use { scenario ->
                drain()
                scenario.onActivity {
                    assertEquals(source.title, it.findViewById<EditText>(R.id.titleInput).text.toString())
                    assertEquals(it.getString(R.string.event_copy_title), it.findViewById<TextView>(R.id.editorHeading).text)
                    assertEquals(View.GONE, it.findViewById<View>(R.id.deleteButton).visibility)
                }
                assertArrayEquals(originalBytes, File(context.filesDir, "countaways.json").readBytes())
                val changedSource = source.copy(title = "Original changed after copying")
                repository.save(listOf(changedSource))
                scenario.recreate()
                drain()
                scenario.onActivity {
                    assertEquals(source.title, it.findViewById<EditText>(R.id.titleInput).text.toString())
                    val save = it.findViewById<Button>(R.id.saveButton)
                    save.performClick()
                    save.performClick()
                }
                drain()
                val saved = events()
                assertEquals(2, saved.size)
                assertEquals(changedSource, saved.single { it.id == source.id })
                val copy = saved.single { it.id != source.id }
                assertEquals(source.copy(id = copy.id, createdAt = copy.createdAt, reminder = ReminderOption.OFF), copy)
            }
        }
    }

    @Test fun abandonedCopyAndHistoricalTitleValidationDoNotWrite() = isolated {
        val source = fixture().copy(title = "Long title 🧩 ".repeat(40))
        repository.save(listOf(source))
        val bytes = File(context.filesDir, "countaways.json").readBytes()
        ActivityScenario.launch<EditorActivity>(copyIntent(source)).use { scenario ->
            drain()
            scenario.onActivity {
                val title = it.findViewById<EditText>(R.id.titleInput)
                assertEquals(source.title, title.text.toString())
                it.findViewById<Button>(R.id.saveButton).performClick()
                assertNotNull(title.error)
            }
        }
        drain()
        assertArrayEquals(bytes, File(context.filesDir, "countaways.json").readBytes())
    }

    private fun actionButton(activity: MainActivity): View =
        activity.findViewById<ListView>(R.id.countdownList).getChildAt(0).findViewById(R.id.eventActions)

    private fun copyIntent(source: CountdownEvent) = Intent(context, EditorActivity::class.java)
        .putExtra(EditorActivity.EXTRA_DUPLICATE_EVENT_ID, source.id)
        .putExtra(EditorActivity.EXTRA_SOURCE_REVISION, EventRevision.of(source))

    private fun fixture() = CountdownEvent(id = UUID.randomUUID().toString(), title = "Reading 🧩",
        date = CountdownTime.snapshot().today.plusDays(20), type = EventType.CUSTOM,
        icon = EventIcon.BOOK, createdAt = Instant.EPOCH)

    private fun events() = (repository.loadResult() as CountdownLoadResult.Success).events

    private fun measure(context: Context, view: View, width: Int) {
        repeat(3) {
            view.forceLayout()
            view.measure(View.MeasureSpec.makeMeasureSpec(dp(context, width), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
    }

    private fun capture(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            File(context.filesDir, "layout-evidence/$name.png").apply {
                parentFile!!.mkdirs()
                outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally { bitmap.recycle() }
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).roundToInt()

    private fun drain() {
        repeat(2) {
            val done = CountDownLatch(1)
            CountdownIo.submit({ Unit }) { done.countDown() }
            assertTrue(done.await(15, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
        }
    }

    private fun isolated(test: () -> Unit) {
        drain()
        val backup = listOf("countaways.json", "countaways.json.bak", "countaways.json.new")
            .associate { name -> File(context.filesDir, name).let { it to if (it.isFile) it.readBytes() else null } }
        try { test() } finally {
            drain()
            backup.forEach { (file, bytes) -> if (bytes == null) file.delete() else file.writeBytes(bytes) }
        }
    }
}
