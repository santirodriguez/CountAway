package com.santiagorodriguez.countaway.widget

import com.santiagorodriguez.countaway.R

/** Shared presentation labels; enum storage keys are never UI strings. */
internal object WidgetStyleLabels {
    fun appearance(value: WidgetAppearance): Int = when (value) {
        WidgetAppearance.SYSTEM -> R.string.widget_appearance_system
        WidgetAppearance.LIGHT -> R.string.widget_appearance_light
        WidgetAppearance.DARK -> R.string.widget_appearance_dark
    }

    fun background(value: WidgetBackground): Int = when (value) {
        WidgetBackground.CLASSIC -> R.string.widget_background_classic
        WidgetBackground.MIST -> R.string.widget_background_mist
        WidgetBackground.HORIZON -> R.string.widget_background_horizon
        WidgetBackground.FOREST -> R.string.widget_background_forest
        WidgetBackground.SUNSET -> R.string.widget_background_sunset
        WidgetBackground.PULSE -> R.string.widget_background_pulse
        WidgetBackground.BREEZE -> R.string.widget_background_breeze
        WidgetBackground.EMBER -> R.string.widget_background_ember
        WidgetBackground.MONOGRAM -> R.string.widget_background_six
    }
}
