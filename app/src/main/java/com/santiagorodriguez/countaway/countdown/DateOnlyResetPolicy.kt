package com.santiagorodriguez.countaway.countdown

import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.ReminderOption
import com.santiagorodriguez.countaway.model.RepeatRule
import java.time.LocalDate

/** A date-only shortcut, deliberately excluding timed count-up and recurrence semantics. */
object DateOnlyResetPolicy {
    fun canReset(event: CountdownEvent, today: LocalDate): Boolean =
        event.countMode == CountMode.COUNT_UP &&
            event.eventTime == null &&
            event.eventZone == null &&
            event.repeatRule == RepeatRule.NONE &&
            event.reminder == ReminderOption.OFF &&
            event.date != today

    fun reset(event: CountdownEvent, today: LocalDate): CountdownEvent? =
        if (canReset(event, today)) event.copy(date = today) else null
}
