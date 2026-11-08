package com.santiagorodriguez.countaway.backup

import android.app.job.JobScheduler
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.data.CountdownStorageCodec
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AutoBackupEngineInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = CountdownRepository(context)
    private val prefs = context.getSharedPreferences("countaway_auto_backups", Context.MODE_PRIVATE)
    private val sample = CountdownEvent(
        id = "auto-backup-instrumented", title = "Filter replacement",
        date = LocalDate.of(2026, 11, 7),
        type = EventType.CUSTOM, createdAt = Instant.EPOCH,
        countMode = CountMode.COUNT_UP,
    )

    @Test fun corruptBackupSettingsRequireExplicitResetWithoutDeletingFiles() = isolated { _: MutableSet<String> ->
        prefs.edit().putString("configuration_v1", "{broken state").commit()
        assertTrue(runCatching { AutoBackupStore.read(context) }.isFailure)
        AutoBackupStore.resetUnreadable(context)
        assertFalse(AutoBackupStore.read(context).enabled)
        assertTrue(AutoBackupStore.read(context).owned.isEmpty())
    }

    @Test fun automaticBackupsAreOptInAndDoNotScheduleWhenOff() = isolated { _: MutableSet<String> ->
        assertFalse(AutoBackupStore.read(context).enabled)
        AutoBackupScheduler.mutationCommitted(context)
        AutoBackupScheduler.ensureScheduled(context)
        val jobs = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        assertNull(jobs.getPendingJob(AutoBackupScheduler.CHANGE_JOB_ID))
        assertNull(jobs.getPendingJob(AutoBackupScheduler.DAILY_JOB_ID))
    }

    @Test fun selectedFolderWithoutPersistedGrantCannotActivate() = isolated { _: MutableSet<String> ->
        repository.save(listOf(sample))
        val target = AutoBackupStore.Destination(
            AutoBackupStore.Kind.SELECTED_FOLDER,
            "content://com.android.externalstorage.documents/tree/primary%3ADocuments",
            "Documents",
        )
        assertTrue(runCatching { AutoBackupEngine.enable(context, target) }.isFailure)
        assertFalse(AutoBackupStore.read(context).enabled)
        assertTrue(AutoBackupStore.read(context).owned.isEmpty())
    }

    @Test fun corruptStoredEventDataNeverBecomesAnEmptyVerifiedBackup() = isolated { _: MutableSet<String> ->
        repository.save(listOf(sample))
        val file = File(context.filesDir, "countaways.json")
        file.writeText("{this is not a backup}", Charsets.UTF_8)
        assertTrue(runCatching {
            AutoBackupEngine.enable(context,
                AutoBackupStore.Destination(AutoBackupStore.Kind.DEFAULT_DOWNLOADS))
        }.isFailure)
        assertFalse(AutoBackupStore.read(context).enabled)
        assertEquals(0, AutoBackupStore.read(context).owned.size)
    }

    @Test fun aSuccessfulMutationCreatesOneCoalescedJobAndARequiredDailyRecheck() = isolated { _: MutableSet<String> ->
        AutoBackupStore.update(context) {
            it.copy(enabled = true, destination = AutoBackupStore.Destination(
                AutoBackupStore.Kind.DEFAULT_DOWNLOADS))
        }
        AutoBackupScheduler.mutationCommitted(context)
        val state = AutoBackupStore.read(context)
        assertEquals(1, state.dirtyRevision)
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        assertNotNull(scheduler.getPendingJob(AutoBackupScheduler.DAILY_JOB_ID))
        assertNotNull(scheduler.getPendingJob(AutoBackupScheduler.CHANGE_JOB_ID))
        val scheduled = scheduler.getPendingJob(AutoBackupScheduler.CHANGE_JOB_ID)
        AutoBackupScheduler.mutationCommitted(context)
        assertEquals(2, AutoBackupStore.read(context).dirtyRevision)
        assertEquals(scheduled?.id, scheduler.getPendingJob(AutoBackupScheduler.CHANGE_JOB_ID)?.id)
        AutoBackupEngine.disable(context)
        AutoBackupScheduler.cancel(context)
        assertNull(scheduler.getPendingJob(AutoBackupScheduler.CHANGE_JOB_ID))
        assertNull(scheduler.getPendingJob(AutoBackupScheduler.DAILY_JOB_ID))
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test fun failedReadbackNeverReplacesTheLastValidBackup() = isolated { owned ->
        repository.save(listOf(sample))
        val target = AutoBackupStore.Destination(AutoBackupStore.Kind.DEFAULT_DOWNLOADS)
        assertTrue(AutoBackupEngine.enable(context, target).created)
        val before = AutoBackupStore.read(context).owned.single()
        owned += before.uri
        val valid = repository.exportPayload()
        val failure = runCatching {
            AutoBackupFiles.createVerified(
                context, target, valid, "0".repeat(64),
                "CountAway-rejected-${java.util.UUID.randomUUID()}.json",
            )
        }
        assertTrue("A mismatched hash must reject the new copy", failure.isFailure)
        val after = AutoBackupStore.read(context)
        assertEquals(listOf(before), after.owned)
        assertTrue("Existing verified backup must survive a rejected write",
            AutoBackupFiles.verify(context, Uri.parse(before.uri), before.sha256))
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test fun aMissingLastSnapshotIsRegeneratedRatherThanClaimedRecovered() = isolated { owned ->
        repository.save(listOf(sample))
        val target = AutoBackupStore.Destination(AutoBackupStore.Kind.DEFAULT_DOWNLOADS)
        assertTrue(AutoBackupEngine.enable(context, target).created)
        val first = AutoBackupStore.read(context).owned.single()
        owned += first.uri
        val oldUri = Uri.parse(first.uri)
        // Some API29 MediaStore implementations report 0 deletions even for a
        // previously app-owned URI. Verify the document's actual availability.
        context.contentResolver.delete(oldUri, null, null)
        if (AutoBackupFiles.verify(context, oldUri, first.sha256)) {
            // An unreadable or modified file is equally important to recover.
            context.contentResolver.openOutputStream(oldUri, "w")?.use {
                it.write("{invalid backup".toByteArray(Charsets.UTF_8))
            } ?: error("Provider could not simulate a missing or damaged snapshot")
        }
        assertFalse("The first copy must no longer be verifiable",
            AutoBackupFiles.verify(context, oldUri, first.sha256))
        assertTrue(AutoBackupEngine.backupNow(context).created)
        val state = AutoBackupStore.read(context)
        owned += state.owned.map { it.uri }
        assertTrue(state.enabled)
        assertEquals(2, state.owned.size)
        assertTrue(AutoBackupFiles.verify(context, Uri.parse(state.owned.last().uri),
            state.owned.last().sha256))
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test fun publicDownloadsSupportsVerificationManualRestoreDedupAndSevenOwnedSnapshots() = isolated { owned ->
        repository.save(listOf(sample))
        val target = AutoBackupStore.Destination(AutoBackupStore.Kind.DEFAULT_DOWNLOADS)
        assertTrue(AutoBackupEngine.enable(context, target).created)
        var state = AutoBackupStore.read(context)
        owned += state.owned.map { it.uri }
        assertTrue(state.enabled)
        assertEquals("Downloads/CountAway", state.destination?.label)
        assertEquals(1, state.owned.size)
        val first = Uri.parse(state.owned.single().uri)
        assertTrue(AutoBackupFiles.verify(context, first, state.owned.single().sha256))
        val payload = context.contentResolver.openInputStream(first)!!.use {
            CountdownStorageCodec.readUtf8Payload(it)
        }
        assertEquals(1, repository.previewImport(payload))
        repository.save(emptyList())
        assertEquals(1, repository.importPayload(payload))
        assertEquals(listOf(sample), (repository.loadResult() as CountdownLoadResult.Success).events)
        assertFalse(AutoBackupEngine.backupNow(context).created)
        assertEquals(1, AutoBackupStore.read(context).owned.size)

        for (n in 1..8) {
            repository.save(listOf(sample.copy(date = sample.date.plusDays(n.toLong()))))
            assertTrue(AutoBackupEngine.backupNow(context).created)
            owned += AutoBackupStore.read(context).owned.map { it.uri }
        }
        state = AutoBackupStore.read(context)
        assertEquals(7, state.owned.size)
        assertEquals(7, state.owned.map { it.sha256 }.distinct().size)
        val mostRecent = state.owned.last()
        assertTrue(AutoBackupFiles.verify(context, Uri.parse(mostRecent.uri), mostRecent.sha256))
        AutoBackupEngine.disable(context)
        assertFalse(AutoBackupStore.read(context).enabled)
        assertTrue("Disabling must keep the last backup",
            AutoBackupFiles.verify(context, Uri.parse(mostRecent.uri), mostRecent.sha256))
    }

    private fun isolated(test: (MutableSet<String>) -> Unit) {
        val originalState = prefs.getString("configuration_v1", null)
        val originalEvents = (repository.loadResult() as CountdownLoadResult.Success).events
        val created = mutableSetOf<String>()
        try {
            AutoBackupScheduler.cancel(context)
            prefs.edit().remove("configuration_v1").commit()
            test(created)
        } finally {
            AutoBackupScheduler.cancel(context)
            created.forEach { runCatching { context.contentResolver.delete(Uri.parse(it), null, null) } }
            repository.save(originalEvents)
            val edit = prefs.edit()
            if (originalState == null) edit.remove("configuration_v1")
            else edit.putString("configuration_v1", originalState)
            edit.commit()
        }
    }

}
