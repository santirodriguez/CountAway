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
}
