package com.santiagorodriguez.countaway.widget

import java.time.Duration
import java.time.Instant

internal data class TimeWidgetSnapshot(
    val days: Long,
    val hours: Long,
    val underHour: Boolean,
    val calculatedAt: Instant,
)

internal object TimeWidgetSnapshotPolicy {
    fun calculate(now: Instant, target: Instant): TimeWidgetSnapshot {
        val distance = Duration.between(now, target)
        val magnitude = distance.abs()
        // Remaining time rounds up; elapsed time uses completed hours.
        val wholeHours = magnitude.toHours()
        val hours = if (distance > Duration.ZERO && magnitude > Duration.ofHours(wholeHours)) {
            wholeHours + 1
        } else wholeHours
        return TimeWidgetSnapshot(
            days = hours / 24,
            hours = hours % 24,
            underHour = !distance.isZero && magnitude < Duration.ofHours(1),
            calculatedAt = now,
        )
    }
}
