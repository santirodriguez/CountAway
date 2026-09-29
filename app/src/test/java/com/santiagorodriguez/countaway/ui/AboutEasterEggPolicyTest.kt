package com.santiagorodriguez.countaway.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AboutEasterEggPolicyTest {
    @Test
    fun messagesOnlyChangeAtDefinedMilestones() {
        val expected = mapOf(
            1 to 1,
            3 to 3,
            7 to 7,
            10 to 10,
            20 to 20,
            50 to 50,
            100 to 100,
        )

        (1..100).forEach { tap ->
            assertEquals(expected[tap], AboutEasterEggPolicy.messageMilestoneForTap(tap))
        }
    }

    @Test
    fun visibleReactionKeepsTheLastReachedMilestone() {
        assertNull(AboutEasterEggPolicy.visibleMilestoneFor(0))
        assertEquals(1, AboutEasterEggPolicy.visibleMilestoneFor(2))
        assertEquals(7, AboutEasterEggPolicy.visibleMilestoneFor(9))
        assertEquals(50, AboutEasterEggPolicy.visibleMilestoneFor(99))
        assertEquals(100, AboutEasterEggPolicy.visibleMilestoneFor(100))
    }

    @Test
    fun tapCounterStopsAt100() {
        assertEquals(1, AboutEasterEggPolicy.nextTapCount(0))
        assertEquals(100, AboutEasterEggPolicy.nextTapCount(99))
        assertEquals(100, AboutEasterEggPolicy.nextTapCount(100))
        assertEquals(100, AboutEasterEggPolicy.nextTapCount(140))
    }
}
