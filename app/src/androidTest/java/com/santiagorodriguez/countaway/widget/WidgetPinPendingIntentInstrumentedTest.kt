package com.santiagorodriguez.countaway.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetPinPendingIntentInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences(
        WidgetPinPendingIntentProbeReceiver.PREFS_NAME,
        android.content.Context.MODE_PRIVATE,
    )

    @Test
    fun immutableBroadcastDropsPlatformFillInWidgetId() {
        val pendingIntent = probePendingIntent(
            data = "countaway://widget/probe/immutable",
            requestCode = 781_001,
            flags = PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        try {
            sendWidgetId(pendingIntent, 41_001)
            assertEquals(AppWidgetManager.INVALID_APPWIDGET_ID, awaitWidgetId())
        } finally {
            pendingIntent.cancel()
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun mutableBroadcastReceivesPlatformFillInWidgetId() {
        val mutableFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = probePendingIntent(
            data = "countaway://widget/probe/mutable",
            requestCode = 781_002,
            flags = PendingIntent.FLAG_CANCEL_CURRENT or mutableFlag,
        )

        try {
            sendWidgetId(pendingIntent, 41_002)
            assertEquals(41_002, awaitWidgetId())
        } finally {
            pendingIntent.cancel()
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun productionCallbackIsMutableOnAndroid12AndNewer() {
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
            assertFalse(pendingIntent.isImmutable)
        } finally {
            pendingIntent.cancel()
        }
    }

    private fun probePendingIntent(
        data: String,
        requestCode: Int,
        flags: Int,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, WidgetPinPendingIntentProbeReceiver::class.java)
            .setData(Uri.parse(data)),
        flags,
    )

    private fun sendWidgetId(pendingIntent: PendingIntent, appWidgetId: Int) {
        preferences.edit().clear().commit()
        pendingIntent.send(
            context,
            0,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
    }

    private fun awaitWidgetId(): Int {
        repeat(100) {
            if (preferences.contains(WidgetPinPendingIntentProbeReceiver.KEY_WIDGET_ID)) {
                return preferences.getInt(
                    WidgetPinPendingIntentProbeReceiver.KEY_WIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                )
            }
            Thread.sleep(20)
        }
        return Int.MIN_VALUE
    }
}
