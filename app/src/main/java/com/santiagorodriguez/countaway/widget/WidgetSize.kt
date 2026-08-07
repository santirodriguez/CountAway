package com.santiagorodriguez.countaway.widget

enum class WidgetSize {
    COMPACT,
    STANDARD,
    LARGE;

    companion object {
        fun fromDimensions(minWidthDp: Int, minHeightDp: Int): WidgetSize = when {
            minWidthDp < 110 || minHeightDp < 70 -> COMPACT
            minWidthDp >= 180 && minHeightDp >= 120 -> LARGE
            else -> STANDARD
        }
    }
}
