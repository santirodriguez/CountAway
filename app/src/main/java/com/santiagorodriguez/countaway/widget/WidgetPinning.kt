package com.santiagorodriguez.countaway.widget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle

object WidgetPinning {
    fun request(activity: Activity, eventId: String? = null): Boolean {
        val manager = AppWidgetManager.getInstance(activity)
        if (!manager.isRequestPinAppWidgetSupported) return false

        val provider = ComponentName(activity, CountdownWidgetProvider::class.java)
        val extras = Bundle().apply {
            putParcelable(
                AppWidgetManager.EXTRA_APPWIDGET_PREVIEW,
                WidgetPreviewFactory.remoteViews(activity),
            )
        }
        val callback = eventId?.let { id ->
            PendingIntent.getBroadcast(
                activity,
                id.hashCode(),
                Intent(activity, WidgetPinResultReceiver::class.java)
                    .putExtra(EXTRA_EVENT_ID, id)
                    .setData(Uri.parse("countaway://widget/pin/$id")),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        return runCatching {
            manager.requestPinAppWidget(provider, extras, callback)
        }.getOrDefault(false)
    }

    const val EXTRA_EVENT_ID = "countaway_widget_event_id"
}
