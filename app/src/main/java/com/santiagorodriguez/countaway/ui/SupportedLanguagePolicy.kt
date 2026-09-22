package com.santiagorodriguez.countaway.ui

import java.util.Locale

internal object SupportedLanguagePolicy {
    private val supported = setOf(
        LanguageManager.ENGLISH,
        LanguageManager.SPANISH,
        LanguageManager.CATALAN,
    )

    fun canonicalSupportedTag(locale: Locale): String? =
        locale.language.takeIf(supported::contains)

    fun firstSupportedTag(locales: List<Locale>): String? =
        locales.firstNotNullOfOrNull(::canonicalSupportedTag)
}
