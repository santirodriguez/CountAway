package com.santiagorodriguez.countaway.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class SupportedLanguagePolicyTest {
    @Test
    fun unsupportedFirstLocaleFallsThroughToSupportedLaterLocale() {
        assertEquals(
            LanguageManager.SPANISH,
            SupportedLanguagePolicy.firstSupportedTag(
                listOf(Locale.GERMAN, Locale.forLanguageTag("es-AR")),
            ),
        )
    }

    @Test
    fun regionalSupportedLocaleKeepsTheExistingLanguageChoice() {
        assertEquals(
            LanguageManager.SPANISH,
            SupportedLanguagePolicy.canonicalSupportedTag(Locale.forLanguageTag("es-AR")),
        )
    }

    @Test
    fun unsupportedLocaleListHasNoImplicitSupportedMatch() {
        assertNull(
            SupportedLanguagePolicy.firstSupportedTag(
                listOf(Locale.GERMAN, Locale.JAPANESE),
            ),
        )
    }
}
