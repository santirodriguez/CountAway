package com.santiagorodriguez.countaway.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
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
class BackupFlowInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun exportFirstPickerSurvivesRestorationAndCancellationPreservesExactPayload() = isolated { context ->
        val flow = validated(context)
        val saved = Bundle()
        main { flow.pickExport(true); flow.saveState(saved) }
        val restored = BackupFlow(context, saved)
        assertEquals(BackupFlow.Stage.PICK_EXPORT_FIRST, restored.stage)
        main { restored.pickerResult(null) }
        assertEquals(BackupFlow.Stage.CONFIRM, restored.stage)
        main { restored.confirmImport() }
        drain()
        assertEquals("Approved", events(context).single().title)
        // A saved confirmation/picker from before the import cannot reoffer consumed approval.
        val stale = BackupFlow(context, saved)
        assertEquals(BackupFlow.Stage.IDLE, stale.stage)
        main { stale.confirmImport() }
        assertEquals("Approved", events(context).single().title)
    }

    @Test fun exportFirstSuccessAndFailureReturnToOneConfirmation() = isolated { context ->
        for (validDestination in listOf(true, false)) {
            val flow = validated(context)
            val destination = if (validDestination) File(context.cacheDir, "export.json")
                else File(context.cacheDir, "absent/child/export.json")
            main { flow.pickExport(true); flow.pickerResult(Uri.fromFile(destination)) }
            drain()
            assertEquals(BackupFlow.Stage.CONFIRM, flow.stage)
            assertEquals(if (validDestination) R.string.backup_export_success else R.string.backup_export_failed,
                flow.takeNotice())
            assertNull(flow.takeNotice())
            main { flow.cancelConfirmation() }
        }
    }

    @Test fun abandonedValidationCleansOnlyItsOwnSnapshotAfterWorkerCompletes() = isolated { context ->
        val independent = CountdownImportSnapshot.create(context)
        independent.write(payload())
        val source = File(context.cacheDir, "source.json").apply { writeText(payload()) }
        val flow = BackupFlow(context)
        val release = CountDownLatch(1)
        val entered = CountDownLatch(1)
        CountdownIo.execute { entered.countDown(); check(release.await(15, TimeUnit.SECONDS)) }
        assertTrue(entered.await(15, TimeUnit.SECONDS))
        try {
            main { flow.pickImport(); flow.pickerResult(Uri.fromFile(source)); flow.abandon() }
        } finally { release.countDown() }
        drain()
        assertEquals(1, context.cacheDir.listFiles()!!.count { it.name.startsWith("pending-countaway-import-") })
        assertEquals(payload(), independent.readPayload())
        independent.clear()
    }

    @Test fun importContinuesAfterListenerDetachesAndCannotBeSubmittedTwice() = isolated { context ->
        val flow = validated(context)
        val release = CountDownLatch(1)
        val entered = CountDownLatch(1)
        CountdownIo.execute { entered.countDown(); check(release.await(15, TimeUnit.SECONDS)) }
        assertTrue(entered.await(15, TimeUnit.SECONDS))
        try {
            main {
                flow.onChanged = { error("Destroyed screen was called") }
                flow.onChanged = null
                flow.confirmImport()
                flow.confirmImport()
                flow.abandon()
            }
        } finally { release.countDown() }
        drain()
        assertEquals(1, events(context).size)
        assertEquals("Approved", events(context).single().title)
        assertFalse(context.cacheDir.listFiles()!!.any { it.name.startsWith("pending-countaway-import-") })
    }

    @Test fun corruptAndMissingSnapshotsCannotReplaceStoredData() = isolated { context ->
        val repository = CountdownRepository(context)
        repository.importPayload(payload())
        for (contents in listOf("not json", """{"schemaVersion":999,"events":[]}""")) {
            val source = File(context.cacheDir, "bad.json").apply { writeText(contents) }
            val flow = BackupFlow(context)
            main { flow.pickImport(); flow.pickerResult(Uri.fromFile(source)) }
            drain()
            assertEquals(BackupFlow.Stage.IDLE, flow.stage)
            assertEquals("Approved", events(context).single().title)
        }
        val flow = validated(context)
        val saved = Bundle()
        main { flow.saveState(saved); flow.cancelConfirmation() }
        assertEquals(BackupFlow.Stage.IDLE, BackupFlow(context, saved).stage)
    }

    @Test fun conflictingOrFailedMutationDoesNotClearDeliveryState() = isolated { context ->
        val original = CountdownStorageCodec.decode(payload()).single()
        val repository = CountdownRepository(context)
        repository.save(listOf(original))
        val delivery = com.santiagorodriguez.countaway.notification.ArrivalNotificationState(context)
        delivery.markDelivered(original, original.date)
        val changed = original.copy(title = "Changed elsewhere")
        repository.save(listOf(changed))
        val mutations = CountdownMutations(context)
        assertEquals(CountdownMutationResult.CONFLICT,
            mutations.save(EventRevision.of(original), original, original.copy(date = original.date.plusDays(1))))
        assertEquals(CountdownMutationResult.CONFLICT, mutations.delete(original.id, EventRevision.of(original)))
        assertEquals(original.date, delivery.deliveredDate(original.id))
        File(context.filesDir, "countaways.json").writeText("broken")
        assertTrue(runCatching { mutations.delete(original.id, EventRevision.of(changed)) }.isFailure)
        assertEquals(original.date, delivery.deliveredDate(original.id))
    }

    private fun validated(context: Context): BackupFlow {
        val source = File(context.cacheDir, "source.json").apply { writeText(payload()) }
        val flow = BackupFlow(context)
        main { flow.pickImport(); flow.pickerResult(Uri.fromFile(source)) }
        drain()
        assertEquals(BackupFlow.Stage.CONFIRM, flow.stage)
        return flow
    }

    private fun payload() = CountdownStorageCodec.encode(listOf(CountdownEvent("approved", "Approved",
        LocalDate.of(2027, 1, 1), EventType.EVENT, createdAt = Instant.parse("2026-01-01T00:00:00Z"))))
    private fun events(context: Context) = (CountdownRepository(context).loadResult() as CountdownLoadResult.Success).events
    private fun main(task: () -> Unit) = instrumentation.runOnMainSync(task)
    private fun drain() {
        val done = CountDownLatch(1)
        CountdownIo.submit({ Unit }) { done.countDown() }
        assertTrue(done.await(15, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }
    private fun isolated(test: (Context) -> Unit) {
        val target = instrumentation.targetContext
        val id = UUID.randomUUID().toString()
        val directory = File(target.cacheDir, "backup-flow-$id").apply { mkdirs() }
        val preferences = mutableListOf<String>()
        val context = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(directory, "cache").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val isolatedName = "$id-$name"
                preferences.add(isolatedName)
                return target.getSharedPreferences(isolatedName, mode)
            }
        }
        try { test(context) } finally {
            drain()
            directory.deleteRecursively()
            preferences.distinct().forEach { target.deleteSharedPreferences(it) }
        }
    }
}
