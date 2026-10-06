package com.santiagorodriguez.countaway.data

import com.santiagorodriguez.countaway.countdown.CountdownDateDomain
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.ReminderOption
import com.santiagorodriguez.countaway.model.RepeatRule

object CountdownValidation {
    const val MAX_TITLE_LENGTH = 256
    const val MAX_EVENTS = 10_000
    const val MAX_PAYLOAD_BYTES = 5 * 1024 * 1024

    fun validateStoredEvents(events: List<CountdownEvent>) {
        if (events.size > MAX_EVENTS) throw corruptData()

        val ids = HashSet<String>(events.size)
        events.forEach { event ->
            if (
                event.id.isBlank() ||
                event.title.isBlank() ||
                !CountdownDateDomain.contains(event.date) ||
                !ids.add(event.id) ||
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
