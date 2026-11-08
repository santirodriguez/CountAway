package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.backup.AutoBackupEngine
import com.santiagorodriguez.countaway.backup.AutoBackupFiles
import com.santiagorodriguez.countaway.backup.AutoBackupScheduler
import com.santiagorodriguez.countaway.backup.AutoBackupStore
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.data.CountdownStorageCodec
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * Real system DocumentsUI selection, not a forged URI or direct lifecycle callback.
 * Runs only on Android 11+ where the allowed Documents folder is distinct from
 * protected storage roots. The emulator and its files are disposable CI state.
 */
@SdkSuppress(minSdkVersion = 30)
@RunWith(AndroidJUnit4::class)
class AutoBackupSafPickerInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation = instrumentation.uiAutomation
    private val repository = CountdownRepository(context)
    private val prefs = context.getSharedPreferences("countaway_auto_backups", Context.MODE_PRIVATE)
    private val sample = CountdownEvent(
        id = "saf-picker-instrumentation",
        title = "Replace air filter",
        date = LocalDate.of(2026, 11, 8),
        type = EventType.CUSTOM,
        createdAt = Instant.EPOCH,
        countMode = CountMode.COUNT_UP,
    )

    @Test fun systemFolderPickerPersistsGrantAndWritesRestorableJson() {
        val oldState = prefs.getString("configuration_v1", null)
        val oldEvents = (repository.loadResult() as CountdownLoadResult.Success).events
        val oldGrants = context.contentResolver.persistedUriPermissions.map { it.uri }.toSet()
        val owned = mutableListOf<Uri>()
        try {
            AutoBackupScheduler.cancel(context)
            prefs.edit().remove("configuration_v1").commit()
            repository.save(listOf(sample))
            ActivityScenario.launch<AutoBackupSettingsActivity>(
                Intent(context, AutoBackupSettingsActivity::class.java)
            ).use { scenario ->
                scenario.onActivity { activity ->
                    assertFalse(AutoBackupStore.read(context).enabled)
                    activity.findViewById<android.widget.Button>(R.id.autoBackupChoose)
                        .performClick()
                }
                // Do not bypass the Android picker: drive its real accessible UI.
                assertTrue("System folder picker did not open", waitForExternalPicker())
                val openedDocuments = clickAccessible("Documents", 8_000L)
                    || (clickAccessible("Show roots", 3_000L) &&
                        clickAccessible("Documents", 8_000L))
                    || (clickAccessible("Show navigation drawer", 3_000L) &&
                        clickAccessible("Documents", 8_000L))
                assertTrue("Documents is unavailable in the system picker: " + dumpUi(),
                    openedDocuments)
                assertTrue("Cannot select Documents in system picker: " + dumpUi(),
                    clickAccessible("Use this folder", 9_000L))
                assertTrue("System denied folder confirmation: " + dumpUi(),
                    clickAccessible("Allow", 9_000L))
                val until = SystemClock.uptimeMillis() + 18_000L
                var state: AutoBackupStore.State
                do {
                    state = AutoBackupStore.read(context)
                    if (state.enabled) break
                    SystemClock.sleep(200)
                } while (SystemClock.uptimeMillis() < until)
                assertTrue("The first SAF copy was not committed and enabled", state.enabled)
                assertEquals(AutoBackupStore.Kind.SELECTED_FOLDER, state.destination?.kind)
                assertNotNull(state.destination?.treeUri)
                assertEquals(1, state.owned.size)
                val copy = state.owned.single()
                owned += Uri.parse(copy.uri)
                assertTrue("Custom-folder JSON not readable after provider close",
                    AutoBackupFiles.verify(context, Uri.parse(copy.uri), copy.sha256))
                val restored = context.contentResolver.openInputStream(Uri.parse(copy.uri))!!.use {
                    CountdownStorageCodec.readUtf8Payload(it)
                }
                assertEquals(listOf(sample), CountdownStorageCodec.decodeForImport(restored))
                // The ledger is committed on the document worker before the retained
                // operation delivers its main-thread completion and re-renders this view.
                // Observe the real UI until it is ready; never force a render or accept
                // only the persisted state as proof of visible opt-in.
                val uiDeadline = SystemClock.uptimeMillis() + 8_000L
                var uiStatus = ""
                var expectedOn = ""
                var backupActionReady = false
                do {
                    scenario.onActivity { activity ->
                        expectedOn = activity.getString(R.string.auto_backup_on)
                        uiStatus = activity.findViewById<android.widget.TextView>(
                            R.id.autoBackupStatus).text.toString()
                        val backupAction = activity.findViewById<android.widget.Button>(
                            R.id.autoBackupNow)
                        backupActionReady = backupAction.visibility == android.view.View.VISIBLE &&
                            backupAction.isEnabled
                    }
                    if (uiStatus == expectedOn && backupActionReady) break
                    SystemClock.sleep(150L)
                } while (SystemClock.uptimeMillis() < uiDeadline)
                assertEquals("Verified SAF opt-in never reached the visible On state",
                    expectedOn, uiStatus)
                assertTrue("Back up now remains unavailable after verified SAF opt-in",
                    backupActionReady)
                val next = sample.copy(date = sample.date.plusDays(1))
                repository.save(listOf(next))
                val outcome = AutoBackupEngine.backupNow(context)
                assertTrue("Changed event should create another SAF snapshot", outcome.created)
                owned += AutoBackupStore.read(context).owned.map { Uri.parse(it.uri) }
                assertTrue(AutoBackupFiles.verify(context, owned.last(),
                    AutoBackupStore.read(context).owned.last().sha256))
            }
        } finally {
            // Cleanup only this test's own new URIs, while tree permission is live.
            for (uri in owned.distinct()) {
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
            AutoBackupScheduler.cancel(context)
            runCatching { AutoBackupEngine.disable(context) }
            for (grant in context.contentResolver.persistedUriPermissions) {
                if (grant.uri !in oldGrants) {
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            grant.uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    }
                }
            }
            repository.save(oldEvents)
            val edit = prefs.edit()
            if (oldState == null) edit.remove("configuration_v1")
            else edit.putString("configuration_v1", oldState)
            edit.commit()
        }
    }

    private fun waitForExternalPicker(): Boolean {
        val until = SystemClock.uptimeMillis() + 8_000L
        do {
            val root = automation.rootInActiveWindow
            if (root != null && root.packageName?.toString() != context.packageName) {
                Log.i("CountAwaySAF", "Picker package: ${root.packageName}")
                return true
            }
            SystemClock.sleep(150)
        } while (SystemClock.uptimeMillis() < until)
        return false
    }

    private fun clickAccessible(text: String, timeoutMs: Long): Boolean {
        val until = SystemClock.uptimeMillis() + timeoutMs
        do {
            val root = automation.rootInActiveWindow
            if (root != null) {
                val match = collect(root).firstOrNull {
                    it.isVisibleToUser && it.isEnabled &&
                        (it.text?.toString()?.equals(text, ignoreCase = true) == true ||
                            it.contentDescription?.toString()?.equals(text, ignoreCase = true) == true)
                }
                var current = match
                while (current != null) {
                    if (current.isClickable && current.isEnabled &&
                        current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        automation.waitForIdle(100, 1_000)
                        return true
                    }
                    current = current.parent
                }
            }
            SystemClock.sleep(175)
        } while (SystemClock.uptimeMillis() < until)
        Log.w("CountAwaySAF", "Missing picker control: $text, UI=" + dumpUi())
        return false
    }

    private fun collect(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { result += collect(it) }
        }
        return result
    }

    private fun dumpUi(): String {
        val root = automation.rootInActiveWindow ?: return "<no window>"
        return collect(root)
            .mapNotNull {
                val label = it.text?.toString() ?: it.contentDescription?.toString()
                label?.let { value -> "${value.take(70)} [${it.className}]" }
            }.take(75).joinToString(" | ")
    }
}
