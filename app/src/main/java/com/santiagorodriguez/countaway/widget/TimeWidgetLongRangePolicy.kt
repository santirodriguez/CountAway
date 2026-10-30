package com.santiagorodriguez.countaway.widget

import com.santiagorodriguez.countaway.model.CountMode
import java.time.Duration
import java.time.Instant

/** Exact signed duration split without giving Chronometer a decades-long base.
 *  If a daily widget refresh is delayed, the subday hours may exceed 24 or
 *  change sign, but the two components still add to the correct total.
 */
internal data class TimeWidgetLongRangePlan(
    val signedDays: Long,
    val subdayClock: TimeWidgetClockPlan,
)

internal object TimeWidgetLongRangePolicy {
    const val DAY_MS = 86_400_000L
    private const val DAY_SECONDS = 86_400L

    fun plan(now: Instant, target: Instant, elapsedRealtimeMs: Long, mode: CountMode): TimeWidgetLongRangePlan? {
        val distance = runCatching { Duration.between(now, target) }.getOrNull() ?: return null
        val signed = runCatching {
            if (mode == CountMode.COUNT_DOWN) distance else distance.negated()
        }.getOrNull() ?: return null
        // java.time Duration keeps nanos positive even for negative spans.
        var days = signed.seconds / DAY_SECONDS
        var remainderMs = (signed.seconds % DAY_SECONDS) * 1_000L +
            signed.nano / 1_000_000L
        if (days < 0 && remainderMs > 0L) {
            days += 1
            remainderMs -= DAY_MS
        }
        if (days > 0 && remainderMs < 0L) {
            days -= 1
            remainderMs += DAY_MS
        }
        val countDown = mode == CountMode.COUNT_DOWN
        val base = if (countDown) elapsedRealtimeMs + remainderMs else elapsedRealtimeMs - remainderMs
        return TimeWidgetLongRangePlan(days, TimeWidgetClockPlan(base, countDown))
    }
}
