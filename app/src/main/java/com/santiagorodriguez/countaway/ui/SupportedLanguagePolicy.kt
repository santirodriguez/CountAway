package com.santiagorodriguez.countaway.ui

import java.util.Locale

internal object SupportedLanguagePolicy {
    private val supported = setOf(
        LanguageManager.ENGLISH,
        LanguageManager.SPANISH,
        LanguageManager.CATALAN,
        LanguageManager.CHINESE,
    )

    fun canonicalSupportedTag(locale: Locale): String? {
        if (locale.language == Locale.CHINESE.language) {
            return LanguageManager.CHINESE.takeIf {
                locale.country.isEmpty() || locale.country == Locale.CHINA.country
            }
        }
        return supported.firstOrNull { Locale.forLanguageTag(it).language == locale.language }
    }

    fun firstSupportedTag(locales: List<Locale>): String? =
        locales.firstNotNullOfOrNull(::canonicalSupportedTag)
}
