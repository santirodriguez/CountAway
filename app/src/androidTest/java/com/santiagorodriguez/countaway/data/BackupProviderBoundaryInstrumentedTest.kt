package com.santiagorodriguez.countaway.data

import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.system.Os
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BackupProviderBoundaryInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    /** A FIFO models an external document stream that opens but then stops sending bytes. */
    @Test fun stalledDocumentImportStarvesSharedIoQueueOnBaseline() {
        val fifo = File(context.cacheDir, "slow-document-${UUID.randomUUID()}")
        Os.mkfifo(fifo.absolutePath, 384) // 0600; no external provider or extra permission.
        val writing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val errors = mutableListOf<Throwable>()
        val writer = Thread({
            try {
                FileOutputStream(fifo).use { output ->
                    writing.countDown()
                    check(release.await(15, TimeUnit.SECONDS))
                    output.write(CountdownStorageCodec.encode(listOf(event("fifo-event", "FIFO import"))).toByteArray())
                }
            } catch (error: Throwable) {
                synchronized(errors) { errors += error }
            }
        }, "CountAway-fixture-writer").apply { isDaemon = true }
        val flow = BackupFlow(context)
        try {
            writer.start()
            instrumentation.runOnMainSync {
                flow.pickImport()
                flow.pickerResult(Uri.fromFile(fifo))
            }
            assertTrue("FIFO never opened by document reader", writing.await(10, TimeUnit.SECONDS))
            val saveProbe = CountDownLatch(1)
            CountdownIo.submit(task = { Unit }, onComplete = { saveProbe.countDown() })
            val completedWhileDocumentStalled = saveProbe.await(1250, TimeUnit.MILLISECONDS)
            Log.i("F5Diagnostic", "local_io_progress_during_stalled_document=$completedWhileDocumentStalled")
            assertFalse("Baseline no longer exhibits FIFO starvation; reconsider the F5 fix", completedWhileDocumentStalled)
            release.countDown()
            assertTrue("Queued work did not recover after provider resumed", saveProbe.await(10, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            assertEquals(BackupFlow.Stage.CONFIRM, flow.stage)
            instrumentation.runOnMainSync { flow.cancelConfirmation() }
            assertEquals(BackupFlow.Stage.IDLE, flow.stage)
        } finally {
            release.countDown()
            writer.join(10000)
            instrumentation.runOnMainSync { flow.abandon() }
            fifo.delete()
        }
        assertTrue("FIFO fixture had an I/O error: $errors", errors.isEmpty())
    }

    /** Storage deliberately retains historical over-limit fields, but a Bundle is IPC-sized. */
    @Test fun validHistoricalPayloadCanProduceOversizedActivityState() {
        val longTitle = "historical-title-" + "A".repeat(1_150_000)
        val longId = "historical-id-" + "B".repeat(180_000)
        val original = event(longId, longTitle)
        val encoded = CountdownStorageCodec.encode(listOf(original))
        assertTrue(encoded.toByteArray().size < CountdownValidation.MAX_PAYLOAD_BYTES)
        assertEquals(original, CountdownStorageCodec.decodeForImport(encoded).single())
        val state = Bundle().apply {
            putString("editor_title", original.title)
            putInt("editor_title_selection_start", original.title.length)
            putString("event_id", original.id)
        }
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(state)
            val size = parcel.dataSize()
            Log.i("F6Diagnostic", "valid_historical_activity_bundle_bytes=$size")
            assertTrue("Fixture must exercise a Binder-sized risk", size > 1_048_576)
            parcel.setDataPosition(0)
            val restored = parcel.readBundle(javaClass.classLoader)!!
            assertEquals(longTitle, restored.getString("editor_title"))
            assertEquals(longId, restored.getString("event_id"))
        } finally { parcel.recycle() }
    }

    private fun event(id: String, title: String) = CountdownEvent(
        id = id,
        title = title,
        date = LocalDate.of(2027, 4, 1),
        type = EventType.EVENT,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}
