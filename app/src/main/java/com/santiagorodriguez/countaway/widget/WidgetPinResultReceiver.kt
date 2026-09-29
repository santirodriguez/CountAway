package com.santiagorodriguez.countaway.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.santiagorodriguez.countaway.data.CountdownIo

class WidgetPinResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val callbackIntent = intent ?: return
        val snapshot = WidgetPinRequestSnapshot.fromCallbackData(callbackIntent.dataString) ?: return
        val appWidgetId = appWidgetIdFrom(callbackIntent)

        val pending = goAsync()
        CountdownIo.execute {
            try {
                WidgetPinFinalizer.finalize(
                    context = context.applicationContext,
                    appWidgetId = appWidgetId,
                    snapshot = snapshot,
                )
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        fun appWidgetIdFrom(intent: Intent?): Int =
            intent?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }
}
