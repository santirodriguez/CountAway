package com.santiagorodriguez.countaway.widget

import java.time.Duration
import java.time.ZonedDateTime

internal data class TimeWidgetCalendarReading(
    val years: Long,
    val months: Long,
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val remaining: Boolean,
) {
    companion object {
        fun between(now: ZonedDateTime, target: ZonedDateTime): TimeWidgetCalendarReading {
            val remaining = target.isAfter(now)
            val localNow = now.withZoneSameInstant(target.zone)
            val start = if (remaining) localNow else target
            val end = if (remaining) target else localNow

            // Anchor whole months to the earlier date in the event's time zone.
            // plusMonths clamps dates such as January 31 to the end of February.
            var months = (end.year.toLong() - start.year) * 12 + end.monthValue - start.monthValue
            if (start.plusMonths(months).isAfter(end)) months--
            val anchor = start.plusMonths(months)
            // Past whole months, use elapsed 24-hour days so the clock never shows 24 hours.
            val seconds = Duration.between(anchor, end).seconds
            return TimeWidgetCalendarReading(
                years = months / 12, months = months % 12, days = seconds / 86_400,
                hours = seconds / 3_600 % 24, minutes = seconds / 60 % 60,
                remaining = remaining,
            )
        }
    }
}
