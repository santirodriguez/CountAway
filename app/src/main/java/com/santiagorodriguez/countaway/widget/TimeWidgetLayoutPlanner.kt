package com.santiagorodriguez.countaway.widget

/** Choose visual density from the actual host bounds; the clock still ticks natively. */
internal data class TimeWidgetLayoutPlan(
    val horizontalPaddingDp: Int,
    val verticalPaddingDp: Int,
    val titleLines: Int,
    val titleSizeSp: Float,
    val dateSizeSp: Float,
    val clockMaxSizeSp: Float,
    val showDate: Boolean,
    val showAccent: Boolean,
    val showLiveIndicator: Boolean,
)

internal object TimeWidgetLayoutPlanner {
    fun plan(widthDp: Int, heightDp: Int, fontScale: Float): TimeWidgetLayoutPlan {
        val width = widthDp.coerceAtLeast(1)
        val height = heightDp.coerceAtLeast(1)
        val enlarged = fontScale >= 1.6f
        return TimeWidgetLayoutPlan(
            horizontalPaddingDp = if (height < 70) 7 else if (width < 185) 10 else 15,
            verticalPaddingDp = when {
                height < 70 -> 2
                height < 110 -> 8
                height < 146 -> 10
                else -> 13
            },
            titleLines = when {
                width < 200 && height >= 155 -> 3
                height >= 132 -> 2
                else -> 1
            },
            titleSizeSp = when {
                height < 70 -> if (enlarged) 8f else 10f
                enlarged || height < 110 -> 12f
                else -> 14f
            },
            dateSizeSp = if (enlarged) 10f else 11f,
            clockMaxSizeSp = when {
                height < 70 -> if (enlarged) 11f else if (fontScale >= 1.3f) 16f else 22f
                height < 110 -> 32f
                width < 185 -> 37f
                height < 150 -> 38f
                width >= 300 && height >= 195 -> 48f
                else -> 43f
            },
            showDate = height >= 160 && (
                !enlarged || height >= 230 || (height >= 205 && width >= 255)
            ),
            showAccent = height >= 130,
            // A small status dot adds meaning in a wide one-row slot without
            // stealing space from the event label or accessibility-sized clock.
            showLiveIndicator = (width >= 210 && height >= 158) ||
                (height in 55..109 && width >= 210 && fontScale < 1.6f),
        )
    }
}
