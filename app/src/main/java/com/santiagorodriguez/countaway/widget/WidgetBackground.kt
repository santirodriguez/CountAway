package com.santiagorodriguez.countaway.widget

import com.santiagorodriguez.countaway.R

enum class WidgetBackground(val storageKey: String) {
    CLASSIC("classic"),
    MIST("mist"),
    HORIZON("horizon"),
    FOREST("forest"),
    SUNSET("sunset"),
    MONOGRAM("monogram");

    fun drawableRes(dark: Boolean): Int = when (this) {
        CLASSIC -> if (dark) R.drawable.widget_background_dark else R.drawable.widget_background_light
        MIST -> if (dark) R.drawable.widget_background_mist_dark else R.drawable.widget_background_mist_light
        HORIZON -> if (dark) R.drawable.widget_background_horizon_dark else R.drawable.widget_background_horizon_light
        FOREST -> if (dark) R.drawable.widget_background_forest_dark else R.drawable.widget_background_forest_light
        SUNSET -> if (dark) R.drawable.widget_background_sunset_dark else R.drawable.widget_background_sunset_light
        MONOGRAM -> if (dark) R.drawable.widget_background_monogram_dark else R.drawable.widget_background_monogram_light
    }

    companion object {
        fun fromStorageKey(value: String?): WidgetBackground =
            entries.firstOrNull { it.storageKey == value } ?: CLASSIC
    }
}
