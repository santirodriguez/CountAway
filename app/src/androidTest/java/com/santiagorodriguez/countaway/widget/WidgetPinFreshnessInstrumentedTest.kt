package com.santiagorodriguez.countaway.widget

import android.content.Intent
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownTimeSnapshot
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.RepeatRule
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WidgetPinFreshnessInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val repository = CountdownRepository(context)

    @Test fun initialLoadAfterPauseAndEditedEventOnResumeKeepChosenAppearance() {
        val original = (repository.loadResult() as CountdownLoadResult.Success).events
        val event = CountdownEvent(UUID.randomUUID().toString(), "Before", LocalDate.of(2026, 1, 31),
            EventType.EVENT, createdAt = Instant.parse("2026-01-01T00:00:00Z"), repeatRule = RepeatRule.MONTHLY)
        repository.save(original + event)
        val release = CountDownLatch(1)
        val entered = CountDownLatch(1)
        CountdownIo.execute { entered.countDown(); check(release.await(15, TimeUnit.SECONDS)) }
        assertTrue(entered.await(15, TimeUnit.SECONDS))
        val scenario = ActivityScenario.launch<WidgetPinSetupActivity>(Intent(context, WidgetPinSetupActivity::class.java)
            .putExtra(WidgetPinSetupActivity.EXTRA_EVENT_ID, event.id))
        try {
            scenario.moveToState(Lifecycle.State.CREATED)
            release.countDown()
            drain()
            scenario.moveToState(Lifecycle.State.RESUMED)
            drain()
            scenario.onActivity {
                assertTrue(it.findViewById<Button>(R.id.widgetPinSetupAddButton).isEnabled)
                it.findViewById<RadioGroup>(R.id.widgetPinSetupAppearanceGroup).check(R.id.widgetPinSetupAppearanceDark)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            repository.save(original + event.copy(title = "After"))
            scenario.moveToState(Lifecycle.State.RESUMED)
            drain()
            scenario.onActivity {
                assertEquals("After", it.findViewById<TextView>(R.id.widgetPinSetupEventTitle).text.toString())
                assertEquals(R.id.widgetPinSetupAppearanceDark,
                    it.findViewById<RadioGroup>(R.id.widgetPinSetupAppearanceGroup).checkedRadioButtonId)
                val update = WidgetPinSetupActivity::class.java.getDeclaredMethod("updatePreview", CountdownTimeSnapshot::class.java)
                    .apply { isAccessible = true }
                for (day in listOf(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 1))) {
                    update.invoke(it, CountdownTimeSnapshot(day.atStartOfDay(ZoneId.of("UTC"))))
                    assertEquals(it.findViewById<TextView>(R.id.widgetPinSetupEventDate).text.toString(),
                        it.findViewById<TextView>(R.id.widgetDate).text.toString())
                }
            }
        } finally {
            release.countDown()
            drain()
            scenario.close()
            repository.save(original)
        }
    }
    private fun drain() {
        val done = CountDownLatch(1)
        CountdownIo.submit({ Unit }) { done.countDown() }
        assertTrue(done.await(15, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }
}
