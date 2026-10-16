package com.santiagorodriguez.countaway.notification

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

internal object ArrivalReminderTiming {
    fun triggerTime(now: ZonedDateTime, date: LocalDate, lastFailure: Instant? = null): ZonedDateTime {
        val morning = date.atTime(LocalTime.of(9, 0)).atZone(now.zone)
        if (date != now.toLocalDate() || morning.isAfter(now)) return morning
        if (lastFailure == null) return now
        val retry = lastFailure.atZone(now.zone).plusMinutes(15)
        // A late failure gets a bounded same-day attempt, never a planned next-day catch-up.
        return if (retry.toLocalDate() != date || !retry.isAfter(now)) now else retry
    }

    fun eventTriggerTime(now: ZonedDateTime, date: LocalDate, time: LocalTime,
        zone: java.time.ZoneId, lastFailure: Instant? = null): ZonedDateTime {
        val localNow = now.withZoneSameInstant(zone)
        val target = date.atTime(time).atZone(zone)
        if (date != localNow.toLocalDate() || target.toInstant().isAfter(now.toInstant())) return target
        if (lastFailure == null) return localNow
        val retry = lastFailure.atZone(zone).plusMinutes(15)
        return if (retry.toLocalDate() != date || !retry.isAfter(localNow)) localNow else retry
    }
    data class Plan(val triggerMillis: Long, val date: LocalDate, val zoneId: String)

    fun keepEarlier(previous: Plan?, proposed: Plan): Boolean = previous != null &&
        previous.date == proposed.date && previous.zoneId == proposed.zoneId &&
        previous.triggerMillis <= proposed.triggerMillis
}
