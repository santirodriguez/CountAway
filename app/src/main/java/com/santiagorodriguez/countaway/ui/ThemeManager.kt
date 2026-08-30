package com.santiagorodriguez.countaway.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration

object ThemeManager {
    enum class AppTheme {
        SYSTEM,
        LIGHT,
        DARK,
    }

    private const val PREFS_NAME = "countaway_ui"
    private const val KEY_THEME = "theme"

    fun wrap(context: Context): Context {
        val configuration = Configuration(context.resources.configuration)
        val nightMode = when (currentTheme(context)) {
            AppTheme.SYSTEM -> configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            AppTheme.LIGHT -> Configuration.UI_MODE_NIGHT_NO
            AppTheme.DARK -> Configuration.UI_MODE_NIGHT_YES
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

    fun setTheme(activity: Activity, theme: AppTheme) {
        if (currentTheme(activity) == theme) return
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme.name)
            .apply()
        activity.recreate()
    }
}
