package com.santiagorodriguez.countaway.notification

import android.content.Context
import com.santiagorodriguez.countaway.model.CountdownEvent
import java.time.LocalDate

class ArrivalNotificationState(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun deliveredDate(eventId: String): LocalDate? = preferences.getString(key(eventId), null)
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun wasDelivered(event: CountdownEvent): Boolean = deliveredDate(event.id) == event.date

    fun markDelivered(event: CountdownEvent) {
        preferences.edit().putString(key(event.id), event.date.toString()).apply()
    }

    fun remove(eventId: String) {
        preferences.edit().remove(key(eventId)).apply()
    }

    private fun key(eventId: String): String = "$KEY_PREFIX$eventId"

    private companion object {
        const val PREFS_NAME = "arrival_notifications"
        const val KEY_PREFIX = "delivered_"
    }
}
