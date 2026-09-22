package com.santiagorodriguez.countaway.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.countdown.CountdownTimeSnapshot

object WidgetUpdateScheduler {
    const val ACTION_DAILY_REFRESH = "com.santiagorodriguez.countaway.widget.DAILY_REFRESH"

    fun ensureScheduled(
        context: Context,
        snapshot: CountdownTimeSnapshot = CountdownTime.snapshot(),
    ) {
        if (!hasWidgets(context)) {
            cancel(context)
            return
        }

        val nextRefreshMillis = runCatching {
            snapshot.today
                .plusDays(1)
                .atStartOfDay(snapshot.zone)
                .plusMinutes(1)
                .toInstant()
                .toEpochMilli()
        }.getOrNull() ?: run {
            cancel(context)
            return
        }

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        runCatching {
            alarmManager.setWindow(
                AlarmManager.RTC,
                nextRefreshMillis,
                REFRESH_WINDOW_MILLIS,
                pendingIntent(context),
            )
        }.onFailure {
            cancel(context)
        }
    }

    fun cancel(context: Context) {
        runCatching {
            context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
        }
    }

    private fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, CountdownWidgetProvider::class.java)
        return manager.getAppWidgetIds(component).isNotEmpty()
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION_DAILY_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val REFRESH_WINDOW_MILLIS = 15L * 60L * 1000L
}
