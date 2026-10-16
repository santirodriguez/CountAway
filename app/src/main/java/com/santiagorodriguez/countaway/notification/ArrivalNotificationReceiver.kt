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
import com.santiagorodriguez.countaway.data.EventTransport
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.ui.EditorActivity
import com.santiagorodriguez.countaway.ui.LanguageManager
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class ArrivalNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CountdownIo.execute {
            try {
                val snapshot = CountdownTime.snapshot()
                ArrivalNotificationScheduler.invalidatePlan()
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
            val localToday = ArrivalNotificationPolicy.localToday(event, snapshot.now)
            val scheduledDate = ArrivalNotificationPolicy.scheduledDate(event, localToday)
                ?: return@mapNotNull null
            if (
                ArrivalNotificationPolicy.isDue(
                    event,
                    localToday,
                    state.deliveredDate(event.id),
                ) &&
                state.canAttempt(event, scheduledDate) &&
                !ArrivalNotificationScheduler.triggerTime(snapshot.now, scheduledDate,
                    event, state.lastFailure(event, scheduledDate)).isAfter(snapshot.now)
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
                body = displayContext.getString(R.string.notification_arrival_body, EventTransport.preview(event.title))
            } else {
                title = displayContext.getString(R.string.notification_upcoming_title)
                body = displayContext.resources.getQuantityString(
                    R.plurals.notification_upcoming_body,
                    daysBefore,
                    daysBefore,
                    EventTransport.preview(event.title),
                )
            }

            val dueTime = event.eventTime
            val dueZone = event.eventZone
            val detailedBody = if (dueTime != null && dueZone != null) {
                val occurrence = scheduledDate.plusDays(daysBefore.toLong())
                    .atTime(dueTime).atZone(dueZone)
                val locale = displayContext.resources.configuration.locales[0]
                val dateTime = occurrence.format(
                    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                        .withLocale(locale),
                )
                displayContext.getString(R.string.notification_timed_due_format,
                    body, "$dateTime · ${dueZone.id}")
            } else body

            val notification = Notification.Builder(context, ArrivalNotificationScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_event_star)
                .setColor(context.getColor(R.color.accent))
                .setContentTitle(title)
                .setContentText(detailedBody)
                .setStyle(Notification.BigTextStyle().bigText(detailedBody))
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
                    state.recordFailure(event, scheduledDate, snapshot.now.toInstant())
                }
            }.onFailure {
                state.recordFailure(event, scheduledDate, snapshot.now.toInstant())
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
            val localToday = ArrivalNotificationPolicy.localToday(event, snapshot.now)
            val scheduledDate = ArrivalNotificationPolicy.scheduledDate(event, localToday)
            if (
                scheduledDate == localToday &&
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
            .putExtra(EditorActivity.EXTRA_EVENT_ID, EventTransport.wireId(eventId))
            .setData(Uri.parse("countaway://arrival/${EventTransport.wireId(eventId)}"))
        return PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
