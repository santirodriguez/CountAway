package com.santiagorodriguez.countaway.notification

import android.content.Context
import com.santiagorodriguez.countaway.model.CountdownEvent
import java.time.LocalDate

class ArrivalNotificationState(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun deliveredDate(eventId: String): LocalDate? = preferences.getString(deliveredKey(eventId), null)
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun wasDelivered(event: CountdownEvent, scheduledDate: LocalDate): Boolean =
        deliveredDate(event.id) == scheduledDate

    fun canAttempt(event: CountdownEvent, scheduledDate: LocalDate): Boolean {
        val failedDate = failedDate(event.id)
        if (failedDate != scheduledDate) return true
        return preferences.getInt(failureCountKey(event.id), 0) < MAX_DELIVERY_ATTEMPTS
    }

    fun markDelivered(event: CountdownEvent, scheduledDate: LocalDate) {
        preferences.edit()
            .putString(deliveredKey(event.id), scheduledDate.toString())
            .remove(failedDateKey(event.id))
            .remove(failureCountKey(event.id))
            .apply()
    }

    fun recordFailure(event: CountdownEvent, scheduledDate: LocalDate) {
        val previousDate = failedDate(event.id)
        val previousCount = if (previousDate == scheduledDate) {
            preferences.getInt(failureCountKey(event.id), 0)
        } else {
            0
        }
        preferences.edit()
            .putString(failedDateKey(event.id), scheduledDate.toString())
            .putInt(
                failureCountKey(event.id),
                (previousCount + 1).coerceAtMost(MAX_DELIVERY_ATTEMPTS),
            )
            .apply()
    }

    fun remove(eventId: String) {
        preferences.edit()
            .remove(deliveredKey(eventId))
            .remove(failedDateKey(eventId))
            .remove(failureCountKey(eventId))
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun failedDate(eventId: String): LocalDate? =
        preferences.getString(failedDateKey(eventId), null)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun deliveredKey(eventId: String): String = "$DELIVERED_KEY_PREFIX$eventId"
    private fun failedDateKey(eventId: String): String = "$FAILED_DATE_KEY_PREFIX$eventId"
    private fun failureCountKey(eventId: String): String = "$FAILURE_COUNT_KEY_PREFIX$eventId"

    companion object {
        const val MAX_DELIVERY_ATTEMPTS = 3

        private const val PREFS_NAME = "arrival_notifications"
        private const val DELIVERED_KEY_PREFIX = "delivered_"
        private const val FAILED_DATE_KEY_PREFIX = "failed_date_"
        private const val FAILURE_COUNT_KEY_PREFIX = "failure_count_"
    }
}
