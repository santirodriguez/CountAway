package com.santiagorodriguez.countaway.backup

import android.app.job.JobScheduler
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
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
 * Two isolated CI instrumentation invocations separated by an actual adb reboot.
 * Uses public Downloads and the real persisted JobScheduler; the CI emulator is
 * disposable. This is not a test of OEM/cloud provider behavior or job deadlines.
 */
@SdkSuppress(minSdkVersion = 36)
@RunWith(AndroidJUnit4::class)
class AutoBackupRebootInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val repository = CountdownRepository(context)
    private val jobs = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
    private val sample = CountdownEvent(
        id = "countaway-reboot-ci",
        title = "Reboot recovery check",
        date = LocalDate.of(2027, 3, 12),
        type = EventType.CUSTOM,
        createdAt = Instant.EPOCH,
        countMode = CountMode.COUNT_UP,
    )

    @Test fun preparePublicCopyAndPersistedJobsBeforeReboot() {
        requireStage("prepare")
        assertTrue("Emulator must start with empty private events", storedEvents().isEmpty())
        assertFalse("Automatic backup must start disabled", AutoBackupStore.read(context).enabled)
        repository.save(listOf(sample))
        assertTrue(AutoBackupEngine.enable(
            context, AutoBackupStore.Destination(AutoBackupStore.Kind.DEFAULT_DOWNLOADS),
        ).created)
        val first = AutoBackupStore.read(context)
        assertTrue(first.enabled)
        assertEquals(1, first.owned.size)
        assertTrue(verified(first.owned.single()))
        assertTrue("Daily job must be persisted before reboot",
            jobs.getPendingJob(AutoBackupScheduler.DAILY_JOB_ID)?.isPersisted == true)

        AutoBackupScheduler.mutationCommitted(context)
        val dirty = AutoBackupStore.read(context)
        assertTrue("A successful mutation must mark backup state dirty",
            dirty.dirtyRevision > dirty.savedRevision)
        assertNotNull("Change job must be scheduled before reboot",
            jobs.getPendingJob(AutoBackupScheduler.CHANGE_JOB_ID))
        Log.i("CountAwayReboot", "REBOOT_PREPARED persistent_daily=true public_json=true")
    }

    @Test fun restoredStateAndAnotherVerifiedCopyAfterReboot() {
        requireStage("verify")
        val before = AutoBackupStore.read(context)
        try {
            assertTrue("Explicit opt-in must survive reboot", before.enabled)
            assertEquals(AutoBackupStore.Kind.DEFAULT_DOWNLOADS, before.destination?.kind)
            assertEquals(listOf(sample), storedEvents())
            assertTrue("Last valid public copy must still be readable", before.owned.isNotEmpty())
            assertTrue(verified(before.owned.first()))
            assertTrue("Persisted daily job must survive an actual reboot",
                jobs.getPendingJob(AutoBackupScheduler.DAILY_JOB_ID)?.isPersisted == true)

            val changed = sample.copy(date = sample.date.plusDays(1))
            repository.save(listOf(changed))
            AutoBackupScheduler.mutationCommitted(context)
            AutoBackupEngine.backupNow(context)
            val after = AutoBackupStore.read(context)
            assertTrue(after.enabled)
            assertEquals(listOf(changed), storedEvents())
            assertTrue("A different event revision must produce another recoverable JSON",
                after.owned.any { it.sha256 != before.owned.first().sha256 && verified(it) })
            assertTrue("The original copy must remain valid after the new write",
                verified(before.owned.first()))
            assertNull("The resumed backup must not report an error", after.lastError)
            Log.i("CountAwayReboot", "REBOOT_VERIFIED persisted=true new_verified_copy=true")
        } finally {
            // Only these test-owned records exist after the workflow's pm clear.
            AutoBackupScheduler.cancel(context)
            runCatching { AutoBackupEngine.disable(context) }
            runCatching { AutoBackupStore.read(context).owned }.getOrDefault(emptyList())
                .forEach { runCatching { AutoBackupFiles.deleteVerifiedOwned(context, it) } }
            repository.save(emptyList())
        }
    }

    private fun requireStage(expected: String) {
        assumeTrue("CI-only reboot stage",
            InstrumentationRegistry.getArguments().getString("countawayRebootStage") == expected)
    }

    private fun storedEvents(): List<CountdownEvent> =
        (repository.loadResult() as CountdownLoadResult.Success).events

    private fun verified(copy: AutoBackupStore.OwnedCopy): Boolean =
        AutoBackupFiles.verify(context, Uri.parse(copy.uri), copy.sha256)
}
