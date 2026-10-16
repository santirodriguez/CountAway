package com.santiagorodriguez.countaway.widget

import com.santiagorodriguez.countaway.model.CountMode
import java.time.Duration
import java.time.Instant

internal data class TimeWidgetClockPlan(val elapsedRealtimeBase: Long, val countDown: Boolean)

/**
 * Keep native seconds only inside a range with reliable second granularity on
 * Android's Chronometer implementations (including those using float seconds).
 * A distant date remains visible, without falsely presenting frozen seconds.
 */
internal object TimeWidgetClockPolicy {
    private const val MAX_NATIVE_DISTANCE_MS = 180L * 24L * 60L * 60L * 1000L

    fun plan(now: Instant, target: Instant, elapsedRealtimeMs: Long, mode: CountMode): TimeWidgetClockPlan? {
        val distanceMs = runCatching { Duration.between(now, target).toMillis() }.getOrNull()
            ?: return null
        if (distanceMs !in -MAX_NATIVE_DISTANCE_MS..MAX_NATIVE_DISTANCE_MS) return null
        return TimeWidgetClockPlan(
            elapsedRealtimeBase = elapsedRealtimeMs + distanceMs,
            countDown = mode == CountMode.COUNT_DOWN,
        )
    }
}
