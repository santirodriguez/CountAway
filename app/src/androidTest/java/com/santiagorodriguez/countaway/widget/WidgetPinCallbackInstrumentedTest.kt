package com.santiagorodriguez.countaway.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetPinCallbackInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun callbackIntentWidgetIdIsParsedDeterministically() {
        val callbackIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 41_002)

        assertEquals(41_002, WidgetPinResultReceiver.appWidgetIdFrom(callbackIntent))
        assertEquals(
            AppWidgetManager.INVALID_APPWIDGET_ID,
            WidgetPinResultReceiver.appWidgetIdFrom(Intent()),
        )
    }

    @Test
    fun productionCallbackIsImmutableOnAndroid12AndNewer() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val pendingIntent = WidgetPinning.callbackPendingIntent(
            context,
            WidgetPinRequestSnapshot.create(
                eventId = "event",
                style = WidgetStyleSelection.FACTORY,
                requestToken = "production-callback-probe",
            ),
        )

        try {
            assertTrue(pendingIntent.isImmutable)
        } finally {
            pendingIntent.cancel()
        }
    }
}
