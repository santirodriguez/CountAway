package com.santiagorodriguez.countaway.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

object ArrivalNotificationScheduler {
    const val ACTION_ARRIVAL_CHECK = "com.santiagorodriguez.countaway.notification.ARRIVAL_CHECK"
    const val CHANNEL_ID = "countdown_arrivals"

    fun ensureScheduled(context: Context) {
        if (!canPostNotifications(context)) {
            cancel(context)
            return
        }

        val events = when (val result = CountdownRepository(context).loadResult()) {
            is CountdownLoadResult.Success -> result.events
            is CountdownLoadResult.Failure -> return
        }
        val state = ArrivalNotificationState(context)
        val today = LocalDate.now()
        val nextDate = ArrivalNotificationPolicy.nextPendingDate(events, today, state::wasDelivered)
            ?: run {
                cancel(context)
                return
            }

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val trigger = triggerTime(ZonedDateTime.now(), nextDate)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            trigger.toInstant().toEpochMilli(),
            pendingIntent(context),
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canPostNotifications(context: Context): Boolean {
        if (!hasNotificationPermission(context)) return false

        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false

        val channel = manager.getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    internal fun triggerTime(now: ZonedDateTime, eventDate: LocalDate): ZonedDateTime {
        val scheduled = eventDate.atTime(REMINDER_TIME).atZone(now.zone)
        return if (!scheduled.isAfter(now) && eventDate == now.toLocalDate()) {
            now.plusSeconds(10)
        } else {
            scheduled
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ArrivalNotificationReceiver::class.java).setAction(ACTION_ARRIVAL_CHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)
    private const val REQUEST_CODE = 41_900
}
