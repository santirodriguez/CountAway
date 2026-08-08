package com.santiagorodriguez.countaway.widget

enum class WidgetSize {
    COMPACT,
    STANDARD,
    LARGE;

    companion object {
        fun fromDimensions(minWidthDp: Int, minHeightDp: Int): WidgetSize = when {
            minWidthDp < 120 || minHeightDp < 110 -> COMPACT
            minWidthDp >= 180 && minHeightDp >= 170 -> LARGE
            else -> STANDARD
        }
    }
}
