package com.santiagorodriguez.countaway.ui

import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.ReminderOption
import java.time.Instant

/** A copy is a new unsaved record, never a second identity for the source. */
internal object EventCopyDraft {
    fun create(source: CountdownEvent, id: String, createdAt: Instant): CountdownEvent {
        require(id.isNotBlank() && id != source.id)
        return source.copy(id = id, createdAt = createdAt, reminder = ReminderOption.OFF)
    }
}
