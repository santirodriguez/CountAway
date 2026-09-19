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
        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val eventId = intent?.getStringExtra(WidgetPinning.EXTRA_EVENT_ID) ?: return
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val provider = ComponentName(appContext, CountdownWidgetProvider::class.java)
        if (!WidgetInstanceValidator.isOwnedBy(manager, appWidgetId, provider)) return

        val pending = goAsync()
        CountdownIo.execute {
            try {
                val eventExists = when (val result = CountdownRepository(appContext).loadResult()) {
                    is CountdownLoadResult.Success -> result.events.any { it.id == eventId }
                    is CountdownLoadResult.Failure -> false
                }
                if (eventExists) {
                    WidgetPreferences(appContext).save(
                        appWidgetId = appWidgetId,
                        eventId = eventId,
                        appearance = WidgetAppearance.SYSTEM,
                        background = WidgetBackground.CLASSIC,
                        eventSelection = WidgetEventSelection.FIXED,
                    )
                }

                val snapshot = CountdownTime.snapshot()
                CountdownWidgetProvider.updateWidget(appContext, manager, appWidgetId, snapshot)
                WidgetUpdateScheduler.ensureScheduled(appContext, snapshot)
            } finally {
                pending.finish()
            }
        }
    }
}
