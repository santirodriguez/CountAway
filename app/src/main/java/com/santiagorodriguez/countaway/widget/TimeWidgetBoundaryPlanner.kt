package com.santiagorodriguez.countaway.widget

import java.time.Instant

/** Pure planning for the next inexact recurrence refresh (never a per-second alarm). */
internal object TimeWidgetBoundaryPlanner {
    fun nextUpcoming(targets: List<Instant>, now: Instant): Instant? =
        targets.asSequence().filter { it.isAfter(now) }.minOrNull()
}
