package com.santiagorodriguez.countaway.model

import org.junit.Assert.*
import org.junit.Test

class CountUpPresetTest {
    @Test fun catalogHasSevenStartingPointsAndTwoGenericChoices() {
        assertEquals(listOf("SMOKE_FREE", "NEW_HABIT", "TRAINING", "LEARNING", "PROJECT",
            "NEW_JOB", "NEW_HOME", "EVENT", "CUSTOM"), CountUpPreset.entries.map { it.name })
        assertEquals(7, CountUpPreset.entries.count { it.suggestsTitle })
    }

    @Test fun templatesOnlyUseTypesAndIconsReadableByTheBaseline() {
        val baselineIcons = setOf("airplane", "book", "confetti", "cake", "heart", "music",
            "hourglass", "calendar", "star", "gift", "flag", "pin")
        CountUpPreset.entries.forEach {
            assertTrue(it.type.storageKey in setOf("event", "custom"))
            assertTrue(it.icon.storageKey in baselineIcons)
        }
    }

    @Test fun suggestionsNeverReplaceNonblankDraftText() {
        CountUpPreset.entries.forEach {
            assertEquals("My own title 👩🏽‍🚀", it.titleFor("My own title 👩🏽‍🚀", "Suggested"))
            assertEquals("  Keep spaces  ", it.titleFor("  Keep spaces  ", "Suggested"))
            assertEquals(if (it.suggestsTitle) "Suggested" else "", it.titleFor("", "Suggested"))
        }
    }

    @Test fun savedTypesDoNotInferSpecificOrSensitiveTemplateIdentity() {
        assertEquals(CountUpPreset.CUSTOM, CountUpPreset.forStoredType(EventType.CUSTOM))
        assertEquals(CountUpPreset.EVENT, CountUpPreset.forStoredType(EventType.EVENT))
        EventType.entries.filter { it !in setOf(EventType.EVENT, EventType.CUSTOM) }.forEach {
            assertNull(CountUpPreset.forStoredType(it))
        }
    }
}
