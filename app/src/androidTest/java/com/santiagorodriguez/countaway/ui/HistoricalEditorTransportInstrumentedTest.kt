package com.santiagorodriguez.countaway.ui

import android.content.Intent
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.data.CountdownStorageCodec
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
class HistoricalEditorTransportInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val repository = CountdownRepository(context)

    @Test fun validHistoricalLargeTitleSurvivesRealEditorRecreationWithoutDataLoss() {
        val title = "Historical-" + "A".repeat(1_150_000)
        val event = fixture("large-title-" + UUID.randomUUID(), title)
        withStoredFixture(event) {
            ActivityScenario.launch<EditorActivity>(
                Intent(context, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_EVENT_ID, event.id),
            ).use { scenario ->
                drain()
                scenario.onActivity { activity ->
                    assertEquals(title, activity.findViewById<EditText>(R.id.titleInput).text.toString())
                }
                scenario.recreate()
                drain()
                scenario.onActivity { activity ->
                    assertEquals(title, activity.findViewById<EditText>(R.id.titleInput).text.toString())
                }
            }
        }
    }

    @Test fun validHistoricalLargeIdCanOpenEditorViaActualActivityIntent() {
        val id = "long-id-" + "B".repeat(1_100_000)
        val event = fixture(id, "Historical identifier")
        withStoredFixture(event) {
            ActivityScenario.launch<EditorActivity>(
                Intent(context, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_EVENT_ID, event.id),
            ).use { scenario ->
                drain()
                scenario.onActivity { activity ->
                    assertEquals(event.title, activity.findViewById<EditText>(R.id.titleInput).text.toString())
                }
            }
        }
    }

    private fun withStoredFixture(event: CountdownEvent, test: () -> Unit) {
        val baseline = (repository.loadResult() as CountdownLoadResult.Success).events
        val originalPayload = CountdownStorageCodec.encode(baseline)
        try {
            val candidate = baseline + event
            assertEquals(candidate, CountdownStorageCodec.decodeForImport(CountdownStorageCodec.encode(candidate)))
            repository.save(candidate)
            test()
            assertEquals(event, (repository.loadResult() as CountdownLoadResult.Success).events.single { it.id == event.id })
        } finally {
            repository.importPayload(originalPayload)
        }
    }

    private fun fixture(id: String, title: String) = CountdownEvent(
        id = id, title = title, date = LocalDate.of(2027, 6, 1),
        type = EventType.EVENT, createdAt = Instant.EPOCH,
    )

    private fun drain() {
        val complete = CountDownLatch(1)
        CountdownIo.submit({ Unit }) { complete.countDown() }
        assertTrue(complete.await(15, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }
}
