package com.santiagorodriguez.countaway.ui

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EditorLifecycleInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val repository = CountdownRepository(context)

    @Test fun newSaveHeldAcrossTwoRecreationsCreatesOnlyOneEvent() = withFixture { original ->
        val title = "Lifecycle-${UUID.randomUUID()}"
        val scenario = ActivityScenario.launch<EditorActivity>(Intent(context, EditorActivity::class.java))
        val release = CountDownLatch(1)
        try {
            drain()
            scenario.onActivity { it.findViewById<EditText>(R.id.titleInput).setText(title) }
            blockWorker(release)
            scenario.onActivity { it.findViewById<Button>(R.id.saveButton).performClick() }
            scenario.recreate()
            scenario.recreate()
            val awaitDestroyed = watchDestruction(scenario)
            release.countDown()
            drain()
            val created = events().filter { it.title == title }
            assertEquals(1, created.size)
            assertEquals(original.size + 1, events().size)
            awaitDestroyed()
        } finally {
            release.countDown()
            drain()
            scenario.close()
        }
    }

    @Test fun staleDraftKeepsItsOriginalRevisionAndReloadDiscardsIt() = withFixture { original ->
        val event = fixture()
        repository.save(original + event)
        val scenario = ActivityScenario.launch<EditorActivity>(Intent(context, EditorActivity::class.java)
            .putExtra(EditorActivity.EXTRA_EVENT_ID, event.id))
        try {
            drain()
            scenario.onActivity { it.findViewById<EditText>(R.id.titleInput).setText("Stale draft") }
            val newer = event.copy(title = "External edit")
            repository.save(original + newer)
            scenario.recreate()
            drain()
            scenario.onActivity { it.findViewById<Button>(R.id.saveButton).performClick() }
            clickDialog(R.string.data_conflict_keep_editing)
            scenario.recreate()
            drain()
            scenario.onActivity { it.findViewById<Button>(R.id.saveButton).performClick() }
            assertEquals(newer, events().single { it.id == event.id })
            clickDialog(R.string.data_conflict_reload)
            drain()
            scenario.onActivity {
                assertEquals(newer.title, it.findViewById<EditText>(R.id.titleInput).text.toString())
                it.findViewById<EditText>(R.id.titleInput).setText("After reload")
                it.findViewById<Button>(R.id.saveButton).performClick()
            }
            drain()
            assertEquals("After reload", events().single { it.id == event.id }.title)
        } finally { scenario.close() }
    }

    @Test fun deletedEventCannotBeResurrectedByRecreatedEditor() = withFixture { original ->
        val event = fixture()
        repository.save(original + event)
        val scenario = ActivityScenario.launch<EditorActivity>(Intent(context, EditorActivity::class.java)
            .putExtra(EditorActivity.EXTRA_EVENT_ID, event.id))
        val release = CountDownLatch(1)
        try {
            drain()
            scenario.onActivity { it.findViewById<EditText>(R.id.titleInput).setText("Stale") }
            repository.save(original)
            blockWorker(release)
            scenario.recreate()
            val awaitDestroyed = watchDestruction(scenario)
            release.countDown()
            drain()
            assertEquals(original, events())
            awaitDestroyed()
        } finally { release.countDown(); drain(); scenario.close() }
    }

    @Test fun editAndDeleteFinishTheirNotificationCleanupAfterRecreation() = withFixture { original ->
        for (delete in listOf(false, true)) {
            val event = fixture()
            repository.save(original + event)
            val delivery = com.santiagorodriguez.countaway.notification.ArrivalNotificationState(context)
            delivery.markDelivered(event, event.date)
            val scenario = ActivityScenario.launch<EditorActivity>(Intent(context, EditorActivity::class.java)
                .putExtra(EditorActivity.EXTRA_EVENT_ID, event.id))
            val release = CountDownLatch(1)
            try {
                drain()
                if (!delete) {
                    scenario.onActivity { it.findViewById<android.widget.Spinner>(R.id.repeatSpinner).setSelection(1) }
                    instrumentation.waitForIdleSync()
                }
                blockWorker(release)
                scenario.onActivity {
                    it.findViewById<Button>(if (delete) R.id.deleteButton else R.id.saveButton).performClick()
                }
                if (delete) clickDialog(R.string.action_delete)
                scenario.recreate()
                release.countDown()
                drain()
                assertNull(delivery.deliveredDate(event.id))
                if (delete) assertFalse(events().any { it.id == event.id })
                else assertNotEquals(event.repeatRule, events().single { it.id == event.id }.repeatRule)
            } finally {
                release.countDown()
                drain()
                scenario.close()
                delivery.remove(event.id)
            }
        }
    }

    private fun watchDestruction(scenario: ActivityScenario<EditorActivity>): () -> Unit {
        val destroyed = CountDownLatch(1)
        lateinit var target: EditorActivity
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity === target && stage == Stage.DESTROYED) destroyed.countDown()
        }
        scenario.onActivity {
            target = it
            ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback)
        }
        return {
            try { assertTrue("Editor should finish after storage reconciliation", destroyed.await(15, TimeUnit.SECONDS)) }
            finally {
                instrumentation.runOnMainSync {
                    ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback)
                }
            }
        }
    }

    private fun clickDialog(text: Int) {
        instrumentation.waitForIdleSync()
        val automation = instrumentation.uiAutomation
        val deadline = android.os.SystemClock.uptimeMillis() + 5_000
        var clicked = false
        while (!clicked && android.os.SystemClock.uptimeMillis() < deadline) {
            automation.waitForIdle(100, 5_000)
            val action = automation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText(context.getString(text))
                ?.firstOrNull { it.isClickable }
            clicked = action?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        assertTrue("Expected dialog action to become accessible", clicked)
        instrumentation.waitForIdleSync()
    }

    private fun blockWorker(release: CountDownLatch) {
        val entered = CountDownLatch(1)
        CountdownIo.execute {
            entered.countDown()
            check(release.await(15, TimeUnit.SECONDS))
        }
        assertTrue(entered.await(15, TimeUnit.SECONDS))
    }

    private fun drain() {
        val completed = CountDownLatch(1)
        CountdownIo.submit({ Unit }) { completed.countDown() }
        assertTrue(completed.await(15, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }

    private fun events() = (repository.loadResult() as CountdownLoadResult.Success).events
    private fun fixture() = CountdownEvent(UUID.randomUUID().toString(), "Original",
        LocalDate.of(2027, 1, 1), EventType.EVENT, createdAt = Instant.parse("2026-01-01T00:00:00Z"))

    // Real Activity handoff needs the target app's store. Preserve its complete original payload;
    // the CI instrumentation installation is disposable and every test restores its fixture.
    private fun withFixture(test: (List<CountdownEvent>) -> Unit) {
        drain()
        val original = events()
        try { test(original) } finally { drain(); repository.save(original) }
    }
}
