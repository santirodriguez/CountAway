package com.santiagorodriguez.countaway.widget

import com.santiagorodriguez.countaway.countdown.CountdownEventOrder
import com.santiagorodriguez.countaway.countdown.CountdownTimeSnapshot
import com.santiagorodriguez.countaway.countdown.TimedEventResolver
import com.santiagorodriguez.countaway.data.EventTransport
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.RepeatRule
import java.time.Instant
import java.time.ZonedDateTime

internal data class TimeWidgetChoice(val event: CountdownEvent, val target: ZonedDateTime)

/** The same domain ordering and target resolution used by the rendered widget. */
internal object TimeWidgetChoiceResolver {
    fun choices(events: List<CountdownEvent>, snapshot: CountdownTimeSnapshot): List<TimeWidgetChoice> {
        val eligible = events.filter { it.eventTime != null && it.eventZone != null }
        return CountdownEventOrder.sortedForDisplay(eligible, snapshot.today, snapshot.now)
            .mapNotNull { event ->
                runCatching { TimedEventResolver.resolve(event, snapshot.now) }.getOrNull()
                    ?.let { TimeWidgetChoice(event, it.target) }
            }
    }

    fun selectedId(choices: List<TimeWidgetChoice>, reference: String?): String? =
        EventTransport.resolve(choices.map { it.event }, reference)?.id

    /** Refresh only when a recurring target rolls over; no periodic UI timer. */
    fun nextRecurringBoundary(choices: List<TimeWidgetChoice>, snapshot: CountdownTimeSnapshot): Instant? =
        choices.asSequence()
            .filter { it.event.countMode == CountMode.COUNT_DOWN && it.event.repeatRule != RepeatRule.NONE }
            .map { it.target.toInstant().plusSeconds(1) }
            .filter { it.isAfter(snapshot.now.toInstant()) }
            .minOrNull()
}
