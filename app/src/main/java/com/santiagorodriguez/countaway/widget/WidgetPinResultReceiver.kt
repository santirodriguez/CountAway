package com.santiagorodriguez.countaway.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository

class WidgetPinResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val callbackIntent = intent ?: return
        val snapshot = WidgetPinRequestSnapshot.fromCallbackData(callbackIntent.dataString) ?: return
        val appWidgetId = appWidgetIdFrom(callbackIntent)
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val provider = ComponentName(appContext, CountdownWidgetProvider::class.java)
        if (!WidgetInstanceValidator.isOwnedBy(manager, appWidgetId, provider)) return

        val pending = goAsync()
        CountdownIo.execute {
            try {
                val eventExists = when (val result = CountdownRepository(appContext).loadResult()) {
                    is CountdownLoadResult.Success -> result.events.any { it.id == snapshot.eventId }
                    is CountdownLoadResult.Failure -> false
                }
                val stillOwned = WidgetInstanceValidator.isOwnedBy(manager, appWidgetId, provider)
                val preferences = WidgetPreferences(appContext)
                val existing = preferences.get(appWidgetId)
                if (
                    WidgetPinResultPolicy.shouldApply(
                        owned = stillOwned,
                        eventExists = eventExists,
                        existing = existing,
                    )
                ) {
                    preferences.save(
                        appWidgetId = appWidgetId,
                        eventId = snapshot.eventId,
                        appearance = snapshot.style.appearance,
                        background = snapshot.style.background,
                        eventSelection = WidgetEventSelection.FIXED,
                    )
                }

                if (stillOwned) {
                    val timeSnapshot = CountdownTime.snapshot()
                    CountdownWidgetProvider.updateWidget(appContext, manager, appWidgetId, timeSnapshot)
                    WidgetUpdateScheduler.ensureScheduled(appContext, timeSnapshot)
                }
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
