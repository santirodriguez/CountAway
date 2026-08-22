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
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.ui.EditorActivity
import com.santiagorodriguez.countaway.ui.LanguageManager
import java.time.LocalDate

class ArrivalNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ArrivalNotifier.notifyDueEvents(context)
        ArrivalNotificationScheduler.ensureScheduled(context)
    }
}

object ArrivalNotifier {
    private const val CHANNEL_ID = "countdown_arrivals"

    fun notifyDueEvents(context: Context) {
        if (!ArrivalNotificationScheduler.canPostNotifications(context)) return

        val displayContext = LanguageManager.localizedContext(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val state = ArrivalNotificationState(context)
        val today = LocalDate.now()
        val dueEvents = CountdownRepository(context).load().mapNotNull { event ->
            val scheduledDate = ArrivalNotificationPolicy.scheduledDate(event) ?: return@mapNotNull null
            if (ArrivalNotificationPolicy.isDue(event, today, state.deliveredDate(event.id))) {
                event to scheduledDate
            } else {
                null
            }
        }
        if (dueEvents.isEmpty()) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                displayContext.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )

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

            val notification = Notification.Builder(context, CHANNEL_ID)
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
                manager.notify(event.id.hashCode(), notification)
            }.onSuccess {
                state.markDelivered(event, scheduledDate)
            }
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
