package com.santiagorodriguez.countaway.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.os.SystemClock
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import org.junit.Assert.*
import org.junit.Test

class StageAWidgetPlatformInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application = instrumentation.targetContext

    @Test fun twoProvidersAreRegisteredWithoutChangingClassicIdentity() {
        val classic = ComponentName(application, CountdownWidgetProvider::class.java)
        val experiment = ComponentName(application, TimeWidgetStageAProvider::class.java)
        val providers = AppWidgetManager.getInstance(application).installedProviders
        val names = providers.map { it.provider.className }
        assertTrue("Original widget not registered: " + names,
            providers.any { it.provider == classic })
        assertTrue("Experimental widget not registered: " + names,
            providers.any { it.provider == experiment })
        assertTrue(classic != experiment)
        if (Build.VERSION.SDK_INT >= 31) {
            assertEquals(1, providers.first { it.provider == classic }.targetCellWidth)
            assertEquals(1, providers.first { it.provider == classic }.targetCellHeight)
            assertEquals(2, providers.first { it.provider == experiment }.targetCellWidth)
            assertEquals(2, providers.first { it.provider == experiment }.targetCellHeight)
        }
        StageAProbeEvidence.report("two_provider_registration=" + names + "\n")
    }

    @Test fun observeNativeChronometerOverThreeDays() = instrumentation.runOnMainSync {
        val seconds = (3L * 24L + 7L) * 3600L + 12L * 60L + 5L
        val remote = RemoteViews(application.packageName, R.layout.time_widget_stage_a_native)
        remote.setChronometer(R.id.stageANativeClock,
            SystemClock.elapsedRealtime() + seconds * 1000L, null, true)
        remote.setChronometerCountDown(R.id.stageANativeClock, true)
        val root = remote.apply(application, FrameLayout(application))
        StageAProbeEvidence.layout(root, application, 200, 60)
        val actual = root.findViewById<Chronometer>(R.id.stageANativeClock).text.toString()
        assertTrue("Native clock produced no time: " + actual, actual.contains(":"))
        StageAProbeEvidence.report("native_chronometer=" + actual +
            "; duration_days=3; decomposed_days=false\n")
    }
}
