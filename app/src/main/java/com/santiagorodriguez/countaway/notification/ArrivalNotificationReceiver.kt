package com.santiagorodriguez.countaway.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.countdown.CountdownTimeSnapshot
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.ui.EditorActivity
import com.santiagorodriguez.countaway.ui.LanguageManager

class ArrivalNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val appContext = context.applicationContext
        val snapshot = CountdownTime.snapshot()
        CountdownIo.execute {
            try {
                ArrivalNotifier.notifyDueEvents(appContext, snapshot)
                ArrivalNotificationScheduler.ensureScheduled(appContext, snapshot)
            } finally {
                pending.finish()
            }
        }
    }
}

object ArrivalNotifier {
    fun notifyDueEvents(
        context: Context,
        snapshot: CountdownTimeSnapshot = CountdownTime.snapshot(),
    ) {
        if (!ArrivalNotificationScheduler.hasNotificationPermission(context)) return

        val displayContext = LanguageManager.localizedContext(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                ArrivalNotificationScheduler.CHANNEL_ID,
                displayContext.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        if (!ArrivalNotificationScheduler.canPostNotifications(context)) return

        val events = when (val result = CountdownRepository(context).loadResult()) {
            is CountdownLoadResult.Success -> result.events
            is CountdownLoadResult.Failure -> return
        }
        val state = ArrivalNotificationState(context)
        val dueEvents = events.mapNotNull { event ->
            val scheduledDate = ArrivalNotificationPolicy.scheduledDate(event, snapshot.today)
                ?: return@mapNotNull null
            if (
                ArrivalNotificationPolicy.isDue(
                    event,
                    snapshot.today,
                    state.deliveredDate(event.id),
                ) &&
                state.canAttempt(event, scheduledDate)
            ) {
                event to scheduledDate
            } else {
                null
            }
        }
        if (dueEvents.isEmpty()) return

        dueEvents.forEach { (event, scheduledDate) ->
            val daysBefore = event.reminder.daysBefore ?: return@forEach
            val title: String
            val body: String
            if (daysBefore == 0) {
                title = displayContext.getString(R.string.notification_arrival_title)
                body = displayContext.getString(R.string.notification_arrival_body, event.title)
            } else {
                title = displayContext.getString(R.string.notification_upcoming_title)
                body = displayContext.resources.getQuantityString(
                    R.plurals.notification_upcoming_body,
                    daysBefore,
                    daysBefore,
                    event.title,
                )
            }

            val notification = Notification.Builder(context, ArrivalNotificationScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_event_star)
                .setColor(context.getColor(R.color.accent))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(editPendingIntent(context, event.id))
                .build()

            runCatching {
                manager.notify(
                    ArrivalNotificationIdentity.tag(event.id),
                    ArrivalNotificationIdentity.ID,
                    notification,
                )
            }.onSuccess {
                if (ArrivalNotificationScheduler.canPostNotifications(context)) {
                    state.markDelivered(event, scheduledDate)
                } else {
                    state.recordFailure(event, scheduledDate)
                }
            }.onFailure {
                state.recordFailure(event, scheduledDate)
            }
        }
    }

    fun cancelEvent(context: Context, eventId: String) {
        runCatching {
            context.getSystemService(NotificationManager::class.java).cancel(
                ArrivalNotificationIdentity.tag(eventId),
                ArrivalNotificationIdentity.ID,
            )
        }
    }

    fun cancelAllEventNotifications(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        runCatching {
            manager.activeNotifications
                .filter { notification -> notification.id == ArrivalNotificationIdentity.ID }
                .forEach { notification -> manager.cancel(notification.tag, notification.id) }
        }
    }

    fun reconcileVisibleNotifications(
        context: Context,
        events: List<CountdownEvent>,
        snapshot: CountdownTimeSnapshot = CountdownTime.snapshot(),
    ) {
        val state = ArrivalNotificationState(context)
        val keepTags = events.mapNotNullTo(HashSet()) { event ->
            val scheduledDate = ArrivalNotificationPolicy.scheduledDate(event, snapshot.today)
            if (
                scheduledDate == snapshot.today &&
                state.deliveredDate(event.id) == scheduledDate
            ) {
                ArrivalNotificationIdentity.tag(event.id)
            } else {
                null
            }
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        runCatching {
            manager.activeNotifications
                .filter { notification ->
                    notification.id == ArrivalNotificationIdentity.ID &&
                        notification.tag !in keepTags
                }
                .forEach { notification -> manager.cancel(notification.tag, notification.id) }
        }
    }

    private fun editPendingIntent(context: Context, eventId: String): PendingIntent {
        val intent = Intent(context, EditorActivity::class.java)
            .putExtra(EditorActivity.EXTRA_EVENT_ID, eventId)
            .setData(Uri.parse("countaway://arrival/$eventId"))
        return PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
