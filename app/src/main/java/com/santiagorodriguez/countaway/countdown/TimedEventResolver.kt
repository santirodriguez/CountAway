package com.santiagorodriguez.countaway.countdown

import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.RepeatRule
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

enum class TimedCountDirection { REMAINING, STARTS_IN, ELAPSED }

data class TimedEventCount(
    val occurrenceDate: LocalDate,
    val target: ZonedDateTime,
    val direction: TimedCountDirection,
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
) {
    fun compact(): String =
        days.toString() + "d · " + hours.toString().padStart(2, '0') + "h · " +
            minutes.toString().padStart(2, '0') + "m · " +
            seconds.toString().padStart(2, '0') + "s"

    fun subday(): String =
        hours.toString().padStart(2, '0') + "h · " +
            minutes.toString().padStart(2, '0') + "m · " +
            seconds.toString().padStart(2, '0') + "s"
}

/** Resolves explicitly timed events; date-only events keep their legacy day resolver. */
object TimedEventResolver {
    fun resolve(event: CountdownEvent, now: ZonedDateTime): TimedEventCount? {
        val time = event.eventTime ?: return null
        val zone = event.eventZone ?: return null
        val localNow = now.withZoneSameInstant(zone)
        val today = localNow.toLocalDate()
        var date = if (event.countMode == CountMode.COUNT_DOWN) {
            CountdownOccurrenceResolver.displayDate(event, today)
        } else event.date
        var target = date.atTime(time).atZone(zone)
        if (event.countMode == CountMode.COUNT_DOWN &&
            event.repeatRule != RepeatRule.NONE &&
            target.toInstant().isBefore(now.toInstant())) {
            val next = runCatching {
                CountdownOccurrenceResolver.nextOccurrenceOnOrAfter(event, date.plusDays(1))
            }.getOrNull()
            if (next != null) {
                date = next
                target = date.atTime(time).atZone(zone)
            }
        }
        val future = target.toInstant().isAfter(now.toInstant())
        val direction = when {
            event.countMode == CountMode.COUNT_UP && future -> TimedCountDirection.STARTS_IN
            future -> TimedCountDirection.REMAINING
            else -> TimedCountDirection.ELAPSED
        }
        val gapMillis = if (future) {
            Duration.between(now.toInstant(), target.toInstant()).toMillis()
        } else Duration.between(target.toInstant(), now.toInstant()).toMillis()
        val totalSeconds = if (future) (gapMillis + 999L) / 1000L else gapMillis / 1000L
        return TimedEventCount(
            occurrenceDate = date, target = target, direction = direction,
            days = totalSeconds / 86_400L,
            hours = totalSeconds % 86_400L / 3_600L,
            minutes = totalSeconds % 3_600L / 60L,
            seconds = totalSeconds % 60L,
        )
    }
}
