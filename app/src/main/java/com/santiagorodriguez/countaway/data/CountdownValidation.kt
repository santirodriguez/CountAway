package com.santiagorodriguez.countaway.data

import com.santiagorodriguez.countaway.countdown.CountdownDateDomain
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.ReminderOption
import com.santiagorodriguez.countaway.model.RepeatRule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object CountdownValidation {
    const val MAX_TITLE_LENGTH = 256
    const val MAX_EVENTS = 10_000
    const val MAX_PAYLOAD_BYTES = 5 * 1024 * 1024

    /** Reject a nonexistent user-selected anchor; later recurring DST gaps normalize separately. */
    fun isSelectableTimedOccurrence(date: LocalDate, time: LocalTime, zone: ZoneId): Boolean =
        zone.rules.getValidOffsets(LocalDateTime.of(date, time)).isNotEmpty()

    fun validateStoredEvents(events: List<CountdownEvent>) {
        if (events.size > MAX_EVENTS) throw corruptData()

        val ids = HashSet<String>(events.size)
        events.forEach { event ->
            if (
                event.id.isBlank() ||
                event.title.isBlank() ||
                !CountdownDateDomain.contains(event.date) ||
                !ids.add(event.id) ||
                ((event.eventTime == null) != (event.eventZone == null)) ||
                (event.eventTime?.second ?: 0) != 0 ||
                (event.eventTime?.nano ?: 0) != 0 ||
                (event.eventTime != null && event.eventZone != null &&
                    !isSelectableTimedOccurrence(event.date, event.eventTime, event.eventZone)) ||
                (
                    event.countMode == CountMode.COUNT_UP &&
                        (event.repeatRule != RepeatRule.NONE || event.reminder != ReminderOption.OFF)
                )
            ) {
                throw corruptData()
            }
        }
    }

    fun validateImportedEvents(events: List<CountdownEvent>) {
        // A CountAway backup can legitimately contain historical fields that predate current
        // editor limits. The file-size/event-count limits remain the abuse boundary, while
        // semantic storage validation preserves self-backup round trips without truncation.
        validateStoredEvents(events)
    }

    fun validatePayloadSize(payload: String) {
        if (payload.toByteArray(Charsets.UTF_8).size > MAX_PAYLOAD_BYTES) {
            throw corruptData()
        }
    }

    fun isTitleWithinLimit(title: String): Boolean = title.length <= MAX_TITLE_LENGTH

    private fun corruptData(): CountdownDataException =
        CountdownDataException(CountdownDataProblem.CORRUPT)
}
