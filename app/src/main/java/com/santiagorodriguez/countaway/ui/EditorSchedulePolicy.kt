package com.santiagorodriguez.countaway.ui

import com.santiagorodriguez.countaway.countdown.CountdownOccurrenceResolver
import com.santiagorodriguez.countaway.countdown.TimedEventCount
import com.santiagorodriguez.countaway.countdown.TimedEventResolver
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.RepeatRule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Display-only draft projection. Reminder delivery/history is deliberately not inferred. */
internal object EditorSchedulePolicy {
    fun nextOccurrenceDate(
        date: LocalDate,
        repeat: RepeatRule,
        time: LocalTime?,
        zone: ZoneId?,
        now: ZonedDateTime,
    ): LocalDate = timedPreview(date, repeat, time, zone, now)?.occurrenceDate
        ?: CountdownOccurrenceResolver.displayDate(date, repeat, now.toLocalDate())

    /** One refresh just after an active recurring target; daily invalidation remains in place. */
    fun nextRefreshBoundary(
        date: LocalDate,
        repeat: RepeatRule,
        time: LocalTime?,
        zone: ZoneId?,
        now: ZonedDateTime,
    ): Instant? {
        if (repeat == RepeatRule.NONE) return null
        return timedPreview(date, repeat, time, zone, now)?.target?.toInstant()?.plusSeconds(1)
    }

    private fun timedPreview(
        date: LocalDate,
        repeat: RepeatRule,
        time: LocalTime?,
        zone: ZoneId?,
        now: ZonedDateTime,
    ): TimedEventCount? {
        if (time == null || zone == null) return null
        val draft = CountdownEvent(
            id = "editor-preview",
            title = "",
            date = date,
            type = EventType.EVENT,
            createdAt = Instant.EPOCH,
            repeatRule = repeat,
            eventTime = time,
            eventZone = zone,
        )
        return TimedEventResolver.resolve(draft, now)
    }
}
