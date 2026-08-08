package com.santiagorodriguez.countaway.widget

enum class WidgetSize(
    val minWidthDp: Int,
    val minHeightDp: Int,
) {
    COMPACT(1, 1),
    STANDARD(110, 165),
    LARGE(180, 230);

    companion object {
        fun fromDimensions(minWidthDp: Int, minHeightDp: Int): WidgetSize = when {
            minWidthDp >= LARGE.minWidthDp && minHeightDp >= LARGE.minHeightDp -> LARGE
            minWidthDp >= STANDARD.minWidthDp && minHeightDp >= STANDARD.minHeightDp -> STANDARD
            else -> COMPACT
        }
    }
}
