package com.santiagorodriguez.countaway.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration

object ThemeManager {
    enum class AppTheme {
        DARK,
        LIGHT,
    }

    private const val PREFS_NAME = "countaway_ui"
    private const val KEY_THEME = "theme"

    fun wrap(context: Context): Context {
        val configuration = Configuration(context.resources.configuration)
        val nightMode = when (currentTheme(context)) {
            AppTheme.DARK -> Configuration.UI_MODE_NIGHT_YES
            AppTheme.LIGHT -> Configuration.UI_MODE_NIGHT_NO
        }
        configuration.uiMode =
            (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        return context.createConfigurationContext(configuration)
    }

    fun currentTheme(context: Context): AppTheme {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, null)
        return AppTheme.entries.firstOrNull { it.name == stored } ?: AppTheme.DARK
    }

    fun toggle(activity: Activity) {
        val next = when (currentTheme(activity)) {
            AppTheme.DARK -> AppTheme.LIGHT
            AppTheme.LIGHT -> AppTheme.DARK
        }
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, next.name)
            .apply()
        activity.recreate()
    }
}
