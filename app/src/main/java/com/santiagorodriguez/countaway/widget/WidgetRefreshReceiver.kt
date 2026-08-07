package com.santiagorodriguez.countaway.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        CountdownWidgetProvider.updateAllWidgets(context)
        WidgetUpdateScheduler.ensureScheduled(context)
    }
}
