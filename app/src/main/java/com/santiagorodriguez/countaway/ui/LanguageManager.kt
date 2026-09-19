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
    const val SPANISH = "es"
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
        explicitLanguageTag(context)?.let { return it }
        return SupportedLanguagePolicy.firstSupportedTag(systemLocales(context))
            ?: ENGLISH
    }

    fun isFollowingSystem(context: Context): Boolean = explicitLanguageTag(context) == null

    fun setLanguage(activity: Activity, languageTag: String) {
        val canonical = SupportedLanguagePolicy.canonicalSupportedTag(Locale.forLanguageTag(languageTag))
            ?: ENGLISH

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LANGUAGE)
                .apply()
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(canonical)
        } else {
            activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANGUAGE, canonical)
                .apply()
            activity.recreate()
        }
    }

    fun useSystemLanguage(activity: Activity) {
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LANGUAGE)
            .apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.getEmptyLocaleList()
        } else {
            activity.recreate()
        }
    }

    fun syncPlatformLocale(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val localeManager = context.getSystemService(LocaleManager::class.java)
        if (!localeManager.applicationLocales.isEmpty) {
            val canonical = firstSupportedTag(localeManager.applicationLocales)
            if (canonical != null && localeManager.applicationLocales.toLanguageTags() != canonical) {
                localeManager.applicationLocales = LocaleList.forLanguageTags(canonical)
            }
            return
        }

        val stored = storedLanguageTag(context) ?: return
        localeManager.applicationLocales = LocaleList.forLanguageTags(stored)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LANGUAGE)
            .apply()
    }

    private fun explicitLanguageTag(context: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val platformLocales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (!platformLocales.isEmpty) {
                return firstSupportedTag(platformLocales)
            }
        }
        return storedLanguageTag(context)
    }

    private fun storedLanguageTag(context: Context): String? {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = preferences.getString(KEY_LANGUAGE, null)
        val normalized = stored
            ?.let(Locale::forLanguageTag)
            ?.let(SupportedLanguagePolicy::canonicalSupportedTag)
        if (stored != null && normalized != null && stored != normalized) {
            preferences.edit().putString(KEY_LANGUAGE, normalized).apply()
        }
        return normalized
    }

    private fun localizedContext(context: Context, languageTag: String): Context {
        val locale = Locale.forLanguageTag(languageTag)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }

    private fun systemLocales(context: Context): List<Locale> {
        val locales = context.resources.configuration.locales
        return buildList {
            for (index in 0 until locales.size()) {
                add(locales[index])
            }
        }
    }

    private fun firstSupportedTag(locales: LocaleList): String? = buildList {
        for (index in 0 until locales.size()) {
            add(locales[index])
        }
    }.let(SupportedLanguagePolicy::firstSupportedTag)
}
