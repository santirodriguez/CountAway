package com.santiagorodriguez.countaway.ui

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LanguageManager {
    const val ENGLISH = "en"
    const val SPANISH = "es-AR"
    const val CATALAN = "ca"

    private const val PREFS_NAME = "countaway_ui"
    private const val KEY_LANGUAGE = "language"

    fun wrap(context: Context): Context {
        val languageTag = explicitLanguageTag(context) ?: return context
        return localizedContext(context, languageTag)
    }

    fun localizedContext(context: Context): Context =
        localizedContext(context, currentLanguageTag(context))

    fun currentLanguageTag(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val platformLocales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (!platformLocales.isEmpty) {
                return canonicalTag(platformLocales[0])
            }
        }

        storedLanguageTag(context)?.let { return it }
        return canonicalTag(context.resources.configuration.locales[0])
    }

    fun setLanguage(activity: Activity, languageTag: String) {
        val canonical = canonicalTag(Locale.forLanguageTag(languageTag))
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, canonical)
            .apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(canonical)
        } else {
            activity.recreate()
        }
    }

    fun syncPlatformLocale(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val localeManager = context.getSystemService(LocaleManager::class.java)
        if (!localeManager.applicationLocales.isEmpty) return

        val stored = storedLanguageTag(context) ?: return
        localeManager.applicationLocales = LocaleList.forLanguageTags(stored)
    }

    private fun explicitLanguageTag(context: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val platformLocales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (!platformLocales.isEmpty) {
                return canonicalTag(platformLocales[0])
            }
        }
        return storedLanguageTag(context)
    }

    private fun storedLanguageTag(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
            ?.takeIf { it in setOf(ENGLISH, SPANISH, CATALAN) }

    private fun localizedContext(context: Context, languageTag: String): Context {
        val locale = Locale.forLanguageTag(languageTag)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }

    private fun canonicalTag(locale: Locale): String = when (locale.language) {
        "es" -> SPANISH
        "ca" -> CATALAN
        else -> ENGLISH
    }
}
