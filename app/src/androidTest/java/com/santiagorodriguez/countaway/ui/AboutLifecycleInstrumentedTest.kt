package com.santiagorodriguez.countaway.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.data.BackupFlow
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AboutLifecycleInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun rotationRetainsValidationPickerConfirmationAndRunningImport() {
        val repository = CountdownRepository(context)
        val original = repository.exportPayload()
        val source = File(context.cacheDir, "lifecycle-${UUID.randomUUID()}.json").apply { writeText(original) }
        val scenario = ActivityScenario.launch<AboutActivity>(Intent(context, AboutActivity::class.java))
        val release = CountDownLatch(1)
        try {
            var retained: BackupFlow? = null
            val entered = CountDownLatch(1)
            CountdownIo.execute { entered.countDown(); check(release.await(15, TimeUnit.SECONDS)) }
            assertTrue(entered.await(15, TimeUnit.SECONDS))
            scenario.onActivity {
                retained = flow(it)
                retained!!.pickImport()
                retained!!.pickerResult(Uri.fromFile(source))
            }
            scenario.recreate()
            scenario.onActivity {
                assertSame(retained, flow(it))
                assertEquals(BackupFlow.Stage.VALIDATING, flow(it).stage)
                assertNull(dialog(it))
            }
            release.countDown()
            drain()
            scenario.onActivity {
                assertTrue(dialog(it)?.isShowing == true)
                flow(it).pickExport(true)
            }
            scenario.recreate()
            scenario.onActivity {
                assertSame(retained, flow(it))
                assertEquals(BackupFlow.Stage.PICK_EXPORT_FIRST, flow(it).stage)
                assertNull(dialog(it))
                flow(it).pickerResult(null)
                assertTrue(dialog(it)?.isShowing == true)
            }
            scenario.recreate()
            scenario.onActivity { assertTrue(dialog(it)?.isShowing == true) }
            val applyingRelease = CountDownLatch(1)
            val applyingEntered = CountDownLatch(1)
            CountdownIo.execute { applyingEntered.countDown(); check(applyingRelease.await(15, TimeUnit.SECONDS)) }
            assertTrue(applyingEntered.await(15, TimeUnit.SECONDS))
            try {
                scenario.onActivity { flow(it).confirmImport() }
                scenario.recreate()
                scenario.onActivity {
                    assertSame(retained, flow(it))
                    assertEquals(BackupFlow.Stage.APPLYING, flow(it).stage)
                    assertNull(dialog(it))
                }
            } finally { applyingRelease.countDown() }
            drain()
            scenario.onActivity { assertEquals(BackupFlow.Stage.IDLE, flow(it).stage); assertNull(dialog(it)) }
            assertEquals(original, repository.exportPayload())
        } finally {
            release.countDown()
            drain()
            scenario.close()
            source.delete()
            repository.importPayload(original)
        }
    }

    private fun flow(activity: AboutActivity): BackupFlow = AboutActivity::class.java.getDeclaredField("flow")
        .apply { isAccessible = true }.get(activity) as BackupFlow
    private fun dialog(activity: AboutActivity): AlertDialog? = AboutActivity::class.java.getDeclaredField("confirmation")
        .apply { isAccessible = true }.get(activity) as AlertDialog?
    private fun drain() {
        val done = CountDownLatch(1)
        CountdownIo.submit({ Unit }) { done.countDown() }
        assertTrue(done.await(15, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }
}
