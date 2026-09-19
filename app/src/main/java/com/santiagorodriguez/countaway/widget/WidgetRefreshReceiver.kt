package com.santiagorodriguez.countaway.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.notification.ArrivalNotificationScheduler
import com.santiagorodriguez.countaway.notification.ArrivalNotifier

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in REFRESH_ACTIONS) return

        val pending = goAsync()
        val appContext = context.applicationContext
        val snapshot = CountdownTime.snapshot()

        CountdownIo.execute {
            try {
                when (val result = CountdownRepository(appContext).loadResult()) {
                    is CountdownLoadResult.Success ->
                        ArrivalNotifier.reconcileVisibleNotifications(appContext, result.events, snapshot)
                    is CountdownLoadResult.Failure -> Unit
                }
                CountdownWidgetProvider.updateAllWidgets(appContext, snapshot)
                WidgetUpdateScheduler.ensureScheduled(appContext, snapshot)
                ArrivalNotificationScheduler.ensureScheduled(appContext, snapshot)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val REFRESH_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
        )
    }
}
