package com.santiagorodriguez.countaway.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationBlockStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val blocked = intent.getBooleanExtra(NotificationManager.EXTRA_BLOCKED_STATE, true)
        val channelId = intent.getStringExtra(NotificationManager.EXTRA_NOTIFICATION_CHANNEL_ID)

        if (NotificationBlockStatePolicy.shouldReschedule(action, blocked, channelId)) {
            ArrivalNotificationScheduler.ensureScheduled(context)
        }
    }
}

internal object NotificationBlockStatePolicy {
    const val ACTION_APP_BLOCK_STATE_CHANGED = "android.app.action.APP_BLOCK_STATE_CHANGED"
    const val ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED =
        "android.app.action.NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED"

    fun shouldReschedule(action: String?, blocked: Boolean, channelId: String?): Boolean = when (action) {
        ACTION_APP_BLOCK_STATE_CHANGED -> !blocked
        ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED ->
            !blocked && channelId == ArrivalNotificationScheduler.CHANNEL_ID
        else -> false
    }
}
