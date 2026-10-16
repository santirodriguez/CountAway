package com.santiagorodriguez.countaway.share

import android.content.Context
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.*
import com.santiagorodriguez.countaway.model.*
import java.time.*
import java.time.format.*

internal data class TimedShareParts(val date: LocalDate, val primary: String,
    val secondary: String, val summary: String, val timestamp: String)

internal object TimedSharePresenter {
    fun present(ctx: Context, date: LocalDate, repeat: RepeatRule, mode: CountMode,
        time: LocalTime, zone: ZoneId, now: ZonedDateTime): TimedShareParts {
        val e = CountdownEvent("share", "", date, EventType.EVENT, createdAt = Instant.EPOCH,
            repeatRule = repeat, countMode = mode, eventTime = time, eventZone = zone)
        val t = checkNotNull(TimedEventResolver.resolve(e, now))
        val gap = if (t.direction == TimedCountDirection.ELAPSED)
            Duration.between(t.target.toInstant(), now.toInstant())
        else Duration.between(now.toInstant(), t.target.toInstant())
        val min = gap.toMinutes()
        val days = min / 1440L
        val hours = min % 1440L / 60L
        val minutes = min % 60L
        val label = when (t.direction) {
            TimedCountDirection.REMAINING -> R.string.timed_remaining
            TimedCountDirection.STARTS_IN -> R.string.timed_starts_in
            TimedCountDirection.ELAPSED -> R.string.timed_elapsed
        }
        val value = when {
            gap.isZero -> ctx.getString(R.string.share_timed_now)
            min == 0L -> ctx.getString(R.string.share_timed_less_than_minute)
            else -> "$days d · $hours h · $minutes min"
        }
        val summary = if (gap.isZero) value else ctx.getString(label, "≈ $value")
        val primary = if (min == 0L) value else "≈ $days d · $hours h"
        val secondary = ctx.getString(label, if (min == 0L) "" else "$minutes min").trimEnd(' ', '·')
        val locale = ctx.resources.configuration.locales[0]
        val stamp = t.target.format(DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))
        val ambiguous = zone.rules.getValidOffsets(t.target.toLocalDateTime()).size > 1
        val dateText = if (zone != now.zone || ambiguous)
            "$stamp · ${zone.id} (${t.target.offset})" else stamp
        return TimedShareParts(t.occurrenceDate, primary, secondary, summary, dateText)
    }
}
