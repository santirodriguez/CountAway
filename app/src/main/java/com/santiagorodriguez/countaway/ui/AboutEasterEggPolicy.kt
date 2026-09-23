package com.santiagorodriguez.countaway.ui

internal object AboutEasterEggPolicy {
    const val MAX_TAPS = 100

    private val milestones = intArrayOf(1, 3, 7, 10, 20, 50, 100)

    fun nextTapCount(current: Int): Int =
        (current.coerceAtLeast(0) + 1).coerceAtMost(MAX_TAPS)

    fun messageMilestoneForTap(tapCount: Int): Int? =
        tapCount.takeIf { it in milestones }

    fun visibleMilestoneFor(tapCount: Int): Int? =
        milestones.lastOrNull { it <= tapCount.coerceIn(0, MAX_TAPS) }
}
