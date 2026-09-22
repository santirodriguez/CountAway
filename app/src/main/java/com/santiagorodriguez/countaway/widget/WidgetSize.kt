package com.santiagorodriguez.countaway.widget

enum class WidgetSize {
    COMPACT,
    SHORT,
    STANDARD,
    LARGE;

    companion object {
        fun fromDimensions(widthDp: Int, heightDp: Int): WidgetSize = when {
            heightDp < 70 && widthDp >= 110 -> SHORT
            widthDp < 110 || heightDp < 70 -> COMPACT
            widthDp >= 180 && heightDp >= 120 -> LARGE
            else -> STANDARD
        }
    }
}
