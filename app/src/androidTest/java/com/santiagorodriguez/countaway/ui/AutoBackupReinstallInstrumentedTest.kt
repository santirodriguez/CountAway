package com.santiagorodriguez.countaway.ui

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.backup.AutoBackupEngine
import com.santiagorodriguez.countaway.backup.AutoBackupFiles
import com.santiagorodriguez.countaway.backup.AutoBackupStore
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * CI-only two-process recovery test. The workflow clears app data before prepare,
 * uninstalls/reinstalls the same APK, then runs restore. The verified snapshot
 * lives in public Downloads and must survive uninstall. The emulator is disposable.
 */
@SdkSuppress(minSdkVersion = 36)
@RunWith(AndroidJUnit4::class)
class AutoBackupReinstallInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation = instrumentation.uiAutomation
    private val repository = CountdownRepository(context)
    private val sample = CountdownEvent(
        id = "countaway-reinstall-recovery-ci",
        title = "Filter replacement after reinstall",
        date = LocalDate.of(2027, 2, 9),
        type = EventType.CUSTOM,
        createdAt = Instant.EPOCH,
        countMode = CountMode.COUNT_UP,
    )

    @Test fun prepareVerifiedPublicBackupBeforeUninstall() {
        requireStage("prepare")
        assertFalse(AutoBackupStore.read(context).enabled)
        assertTrue(events().isEmpty())
        repository.save(listOf(sample))
        assertTrue(AutoBackupEngine.enable(
            context, AutoBackupStore.Destination(AutoBackupStore.Kind.DEFAULT_DOWNLOADS)
        ).created)
        val state = AutoBackupStore.read(context)
        assertTrue(state.enabled)
        assertEquals(1, state.owned.size)
        val copy = state.owned.single()
        assertTrue(AutoBackupFiles.verify(context, android.net.Uri.parse(copy.uri), copy.sha256))
        assertEquals("Downloads/CountAway", state.destination?.label)
        Log.i("CountAwayReinstall", "PREPARE_VERIFIED public_download=true")
        // Intentionally leave this fixture in Downloads until after the real uninstall.
    }

    @Test fun restorePublicBackupThroughSystemPickerAfterReinstall() {
        requireStage("restore")
        assertFalse("Backup opt-in must not survive uninstall", AutoBackupStore.read(context).enabled)
        assertTrue("Private events must not survive uninstall", events().isEmpty())
        ActivityScenario.launch<AboutActivity>(
            Intent(context, AboutActivity::class.java)
        ).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.importButton).performClick()
            }
            assertTrue("ACTION_OPEN_DOCUMENT did not open", waitForExternalPicker())
            if (!clickBackupFile(5_000L)) {
                val downloads = clickText("Downloads", 4_000L) ||
                    clickText("Download", 3_000L) ||
                    (clickText("Show roots", 2_000L) &&
                        (clickText("Downloads", 4_000L) || clickText("Download", 3_000L))) ||
                    (clickText("Show navigation drawer", 2_000L) &&
                        (clickText("Downloads", 4_000L) || clickText("Download", 3_000L)))
                assertTrue("Downloads root unavailable: " + dumpUi(), downloads)
                assertTrue("CountAway folder unavailable after uninstall: " + dumpUi(),
                    clickText("CountAway", 6_000L))
                assertTrue("Public JSON missing after uninstall: " + dumpUi(),
                    clickBackupFile(9_000L))
            }
            assertTrue("Real import confirmation missing: " + dumpUi(),
                waitForText(context.getString(R.string.backup_import_confirm_title), 15_000L))
            assertTrue("Cannot confirm real import: " + dumpUi(),
                clickText(context.getString(R.string.backup_import_action), 7_000L))
            val deadline = SystemClock.uptimeMillis() + 15_000L
            do {
                if (events() == listOf(sample)) break
                SystemClock.sleep(200)
            } while (SystemClock.uptimeMillis() < deadline)
            assertEquals("Restored events differ from pre-uninstall JSON", listOf(sample), events())
            Log.i("CountAwayReinstall", "RESTORE_VERIFIED real_picker=true real_import=true")
        }
    }

    private fun requireStage(expected: String) {
        assumeTrue("Separate CI reinstall stage only",
            InstrumentationRegistry.getArguments().getString("countawayReinstallStage") == expected)
    }

    private fun events(): List<CountdownEvent> =
        (repository.loadResult() as CountdownLoadResult.Success).events

    private fun waitForExternalPicker(): Boolean {
        val until = SystemClock.uptimeMillis() + 9_000L
        do {
            val root = automation.rootInActiveWindow
            if (root != null && root.packageName?.toString() != context.packageName) return true
            SystemClock.sleep(150)
        } while (SystemClock.uptimeMillis() < until)
        return false
    }

    private fun waitForText(value: String, timeout: Long): Boolean {
        val until = SystemClock.uptimeMillis() + timeout
        do {
            if (nodes().any { it.isVisibleToUser && it.text?.toString() == value }) return true
            SystemClock.sleep(150)
        } while (SystemClock.uptimeMillis() < until)
        return false
    }

    private fun clickText(value: String, timeout: Long): Boolean =
        clickMatching(timeout) { it.equals(value, ignoreCase = true) }

    private fun clickBackupFile(timeout: Long): Boolean =
        clickMatching(timeout) { it.startsWith("CountAway-") && it.endsWith(".json") }

    private fun clickMatching(timeout: Long, matches: (String) -> Boolean): Boolean {
        val until = SystemClock.uptimeMillis() + timeout
        do {
            for (node in nodes()) {
                if (!node.isVisibleToUser || !node.isEnabled) continue
                val labels = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                if (!labels.any(matches)) continue
                var current: AccessibilityNodeInfo? = node
                while (current != null) {
                    if (current.isClickable && current.isEnabled &&
                        current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                    current = current.parent
                }
            }
            SystemClock.sleep(175)
        } while (SystemClock.uptimeMillis() < until)
        Log.w("CountAwayReinstall", "Picker control not found: " + dumpUi())
        return false
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val root = automation.rootInActiveWindow ?: return emptyList()
        val found = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            found += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        visit(root)
        return found
    }

    private fun dumpUi(): String = nodes().mapNotNull {
        (it.text?.toString() ?: it.contentDescription?.toString())?.take(65)
    }.take(60).joinToString(" | ")
}
