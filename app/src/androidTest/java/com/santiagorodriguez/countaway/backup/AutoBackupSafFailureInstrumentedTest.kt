package com.santiagorodriguez.countaway.backup

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.ui.AutoBackupSettingsActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/** Real DocumentsUI/ContentResolver flow with a debug-only controlled SAF provider. */
@SdkSuppress(minSdkVersion = 36)
@RunWith(AndroidJUnit4::class)
class AutoBackupSafFailureInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation = instrumentation.uiAutomation
    private val repository = CountdownRepository(context)
    private val faultPrefs = context.getSharedPreferences("ci_fault_saf", Context.MODE_PRIVATE)
    private val backupPrefs = context.getSharedPreferences("countaway_auto_backups", Context.MODE_PRIVATE)
    private val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    private val sample = CountdownEvent(
        id = "saf-failure-regression", title = "Safe local snapshot",
        date = LocalDate.of(2027, 4, 9), type = EventType.CUSTOM,
        createdAt = Instant.EPOCH, countMode = CountMode.COUNT_UP,
    )

    @Test fun errorsSlowOutputAndRevokedGrantPreserveLastGoodCopy() {
        val oldConfig = backupPrefs.getString("configuration_v1", null)
        val oldEvents = (repository.loadResult() as CountdownLoadResult.Success).events
        val oldGrants = context.contentResolver.persistedUriPermissions.map { it.uri }.toSet()
        val folder = File(context.filesDir, "ci-fault-saf")
        try {
            AutoBackupScheduler.cancel(context)
            backupPrefs.edit().remove("configuration_v1").commit()
            folder.deleteRecursively()
            faultPrefs.edit().clear().commit()
            repository.save(listOf(sample))
            ActivityScenario.launch<AutoBackupSettingsActivity>(
                Intent(context, AutoBackupSettingsActivity::class.java)
            ).use { scenario ->
                chooseFixture(scenario)
                val original = waitForCopy()
                assertTrue("First test-provider JSON was not verified", verified(original))
                faultPrefs.edit().putString("protected_document",
                    DocumentsContract.getDocumentId(Uri.parse(original.uri))).commit()
                repository.save(listOf(sample.copy(date = sample.date.plusDays(1))))

                for (mode in listOf("UNAVAILABLE", "NO_SPACE", "ABORT", "TRUNCATED")) {
                    faultPrefs.edit().putString("mode", mode).commit()
                    assertTrue("Provider fault $mode did not reject the write",
                        runCatching { AutoBackupEngine.backupNow(context) }.isFailure)
                    val state = AutoBackupStore.read(context)
                    assertEquals("Failure $mode changed the ledger", listOf(original), state.owned)
                    assertEquals("Failure $mode was hidden", "BACKUP_FAILED", state.lastError)
                    faultPrefs.edit().putString("mode", "NORMAL").commit()
                    assertTrue("Failure $mode damaged the last good document", verified(original))
                }

                faultPrefs.edit().putString("mode", "SLOW")
                    .putBoolean("entered_slow_write", false).commit()
                val operation = FutureTask { AutoBackupEngine.backupNow(context) }
                val worker = Thread(operation, "CountAway-slow-SAF").apply {
                    isDaemon = true
                    start()
                }
                assertTrue("Slow provider did not enter an output operation", waitSlow())
                val progress = CountDownLatch(1)
                CountdownIo.submit(task = { Unit }, onComplete = { progress.countDown() })
                assertTrue("Slow provider starved serialized event I/O",
                    progress.await(1300L, TimeUnit.MILLISECONDS))
                assertTrue("Delayed output did not commit",
                    operation.get(12L, TimeUnit.SECONDS).created)
                worker.join(1000L)
                faultPrefs.edit().putString("mode", "NORMAL").commit()
                val stored = AutoBackupStore.read(context)
                assertEquals(2, stored.owned.size)
                assertTrue("Previously good file damaged", verified(original))
                assertTrue("Delayed file unreadable", verified(stored.owned.last()))

                val tree = Uri.parse(requireNotNull(stored.destination?.treeUri))
                context.contentResolver.releasePersistableUriPermission(tree, flags)
                assertFalse("Persisted provider access was not revoked",
                    context.contentResolver.persistedUriPermissions.any {
                        it.uri == tree && it.isReadPermission && it.isWritePermission
                    })
                repository.save(listOf(sample.copy(date = sample.date.plusDays(2))))
                assertTrue("Revoked provider unexpectedly wrote a backup",
                    runCatching { AutoBackupEngine.backupNow(context) }.isFailure)
                val afterRevocation = AutoBackupStore.read(context)
                assertEquals(stored.owned, afterRevocation.owned)
                assertEquals("BACKUP_FAILED", afterRevocation.lastError)

                chooseFixture(scenario)
                val deadline = SystemClock.uptimeMillis() + 18_000L
                var recovered = AutoBackupStore.read(context)
                while (SystemClock.uptimeMillis() < deadline) {
                    recovered = AutoBackupStore.read(context)
                    if (recovered.lastError == null && recovered.owned.size >= 3) break
                    SystemClock.sleep(150L)
                }
                assertTrue("Reselection did not restore verified backups",
                    recovered.lastError == null && recovered.owned.size >= 3)
                assertTrue("Original snapshot no longer readable after regrant", verified(original))
                assertTrue("New snapshot failed verification", verified(recovered.owned.last()))
            }
        } finally {
            faultPrefs.edit().putString("mode", "NORMAL").commit()
            AutoBackupScheduler.cancel(context)
            for (copy in runCatching { AutoBackupStore.read(context).owned }.getOrDefault(emptyList())) {
                runCatching { context.contentResolver.delete(Uri.parse(copy.uri), null, null) }
            }
            runCatching { AutoBackupEngine.disable(context) }
            for (grant in context.contentResolver.persistedUriPermissions) {
                if (grant.uri !in oldGrants) {
                    runCatching { context.contentResolver.releasePersistableUriPermission(grant.uri, flags) }
                }
            }
            folder.deleteRecursively()
            faultPrefs.edit().clear().commit()
            repository.save(oldEvents)
            if (oldConfig == null) backupPrefs.edit().remove("configuration_v1").commit()
            else backupPrefs.edit().putString("configuration_v1", oldConfig).commit()
        }
    }

    private fun verified(copy: AutoBackupStore.OwnedCopy) =
        AutoBackupFiles.verify(context, Uri.parse(copy.uri), copy.sha256)

    private fun waitForCopy(): AutoBackupStore.OwnedCopy {
        val until = SystemClock.uptimeMillis() + 18_000L
        do {
            val state = AutoBackupStore.read(context)
            if (state.enabled && state.owned.isNotEmpty()) return state.owned.last()
            SystemClock.sleep(150L)
        } while (SystemClock.uptimeMillis() < until)
        error("First fixture backup never completed")
    }

    private fun waitSlow(): Boolean {
        val until = SystemClock.uptimeMillis() + 8_000L
        do {
            if (faultPrefs.getBoolean("entered_slow_write", false)) return true
            SystemClock.sleep(80L)
        } while (SystemClock.uptimeMillis() < until)
        return false
    }

    private fun chooseFixture(scenario: ActivityScenario<AutoBackupSettingsActivity>) {
        val authority = context.packageName + ".ci-fault-saf"
        assertNotNull("Debug provider missing from installed debug APK",
            context.packageManager.resolveContentProvider(authority, 0))
        scenario.onActivity {
            it.findViewById<Button>(R.id.autoBackupChoose).performClick()
        }
        assertTrue("System picker did not open", waitPicker())
        // Android DocumentsUI opens at the last local folder, with the roots drawer
        // closed. A custom provider cannot be selected from that document listing.
        val opened = click("CountAway Test Storage", 2_000L) ||
            (revealRoots() && click("CountAway Test Storage", 5_000L)) ||
            (clickCurrentRoot() && click("CountAway Test Storage", 5_000L))
        assertTrue("Debug provider root unavailable in DocumentsUI: " + dump(), opened)
        assertTrue("Cannot open test folder: " + dump(), click("Backup Scenarios", 7_000L))
        assertTrue("Cannot use test folder: " + dump(), click("Use this folder", 7_000L))
        assertTrue("Permission dialog missing: " + dump(), click("Allow", 7_000L))
    }

    private fun revealRoots(): Boolean {
        for (label in listOf("Show roots", "Show navigation drawer",
                "Open navigation drawer", "Browse")) {
            if (click(label, 350L)) return true
        }
        // Gesture fallback: Android 15/16 Files picker often exposes a hamburger
        // without a stable accessible label. Swipe from the left screen edge.
        val root = automation.rootInActiveWindow ?: return false
        val bounds = Rect().also(root::getBoundsInScreen)
        val y = bounds.centerY().toFloat()
        val left = bounds.left + 4f
        val right = bounds.left + bounds.width() * 0.72f
        val downTime = SystemClock.uptimeMillis()
        fun touch(action: Int, x: Float) {
            val event = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                automation.injectInputEvent(event, true)
            } finally { event.recycle() }
        }
        touch(MotionEvent.ACTION_DOWN, left)
        for (step in 1..12) {
            SystemClock.sleep(18L)
            touch(MotionEvent.ACTION_MOVE, left + (right - left) * step / 12f)
        }
        touch(MotionEvent.ACTION_UP, right)
        SystemClock.sleep(400L)
        return nodes().any {
            it.isVisibleToUser &&
                (it.text?.toString() == "CountAway Test Storage" ||
                    it.contentDescription?.toString() == "CountAway Test Storage")
        }
    }

    private fun clickCurrentRoot(): Boolean {
        // In some DocumentsUI builds the toolbar location opens the same roots
        // drawer; do not guess at arbitrary unrelated buttons.
        val currentRoot = nodes().firstOrNull {
            it.isVisibleToUser &&
                (it.contentDescription?.toString()?.startsWith("Files on ") == true ||
                    it.text?.toString()?.startsWith("Files on ") == true)
        } ?: return false
        var node: AccessibilityNodeInfo? = currentRoot
        while (node != null) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            node = node.parent
        }
        return false
    }

    private fun waitPicker(): Boolean {
        val until = SystemClock.uptimeMillis() + 8_000L
        do {
            val root = automation.rootInActiveWindow
            if (root != null && root.packageName?.toString() != context.packageName) return true
            SystemClock.sleep(150L)
        } while (SystemClock.uptimeMillis() < until)
        return false
    }

    private fun click(label: String, millis: Long): Boolean {
        val until = SystemClock.uptimeMillis() + millis
        do {
            val found = nodes().firstOrNull {
                it.isVisibleToUser && it.isEnabled &&
                    (it.text?.toString()?.equals(label, true) == true ||
                        it.contentDescription?.toString()?.equals(label, true) == true)
            }
            var node = found
            while (node != null) {
                if (node.isClickable && node.isEnabled &&
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                node = node.parent
            }
            SystemClock.sleep(150L)
        } while (SystemClock.uptimeMillis() < until)
        return false
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val root = automation.rootInActiveWindow ?: return emptyList()
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            result += node
            for (n in 0 until node.childCount) node.getChild(n)?.let(::visit)
        }
        visit(root)
        return result
    }

    private fun dump() = nodes().mapNotNull {
        val label = it.text?.toString() ?: it.contentDescription?.toString()
        label?.let { text ->
            "$text [${it.className};id=${it.viewIdResourceName};click=${it.isClickable}]"
        }
    }.take(75).joinToString(" | ")
}
