package com.santiagorodriguez.countaway.ui

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
    fun canonicalizesChineseLocalesToSimplifiedChinese() {
        assertEquals(LanguageManager.CHINESE, SupportedLanguagePolicy.canonicalSupportedTag(Locale.CHINESE))
        assertEquals(
            LanguageManager.CHINESE,
            SupportedLanguagePolicy.canonicalSupportedTag(Locale.forLanguageTag("zh-CN")),
        )
        assertNull(SupportedLanguagePolicy.canonicalSupportedTag(Locale.forLanguageTag("zh-TW")))
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
