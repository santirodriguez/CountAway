package com.santiagorodriguez.countaway.widget

import android.content.Context
import android.content.res.Configuration
import com.santiagorodriguez.countaway.R

internal data class WidgetTheme(
    val dark: Boolean,
    val primaryTextColor: Int,
    val secondaryTextColor: Int,
    val accentTextColor: Int,
)

internal object WidgetThemeResolver {
    fun resolve(context: Context, appearance: WidgetAppearance): WidgetTheme {
        val systemContext = context.applicationContext
        val systemDark = systemContext.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val dark = appearance.resolveDark(systemDark)

        return if (dark) {
            WidgetTheme(
                dark = true,
                primaryTextColor = systemContext.getColor(R.color.widget_dark_text),
                secondaryTextColor = systemContext.getColor(R.color.widget_dark_secondary_text),
                accentTextColor = systemContext.getColor(R.color.widget_dark_accent),
            )
        } else {
            WidgetTheme(
                dark = false,
                primaryTextColor = systemContext.getColor(R.color.widget_light_text),
                secondaryTextColor = systemContext.getColor(R.color.widget_light_secondary_text),
                accentTextColor = systemContext.getColor(R.color.widget_light_accent),
            )
        }
    }
}
