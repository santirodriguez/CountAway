package com.santiagorodriguez.countaway.widget

import kotlin.math.roundToInt

internal data class WidgetHostDimensions(val widthDp: Float, val heightDp: Float)
internal data class WidgetLegacyOptions(
    val minWidthDp: Int?,
    val maxWidthDp: Int?,
    val minHeightDp: Int?,
    val maxHeightDp: Int?,
)
internal data class WidgetLegacyDefaults(
    val minWidthDp: Int,
    val maxWidthDp: Int,
    val minHeightDp: Int,
    val maxHeightDp: Int,
)
internal data class WidgetOrientationDimensions(
    val portrait: WidgetPreviewDimensions,
    val landscape: WidgetPreviewDimensions,
)

/** Host dimensions are orientation-paired; independent extrema are not one rectangle. */
internal object WidgetHostGeometry {
    private const val MAX_DIMENSION_DP = 2_048f
    private const val MAX_BITMAP_EDGE_PX = 360f
    private const val MAX_VARIANTS = 16
    private const val MAX_AGGREGATE_IMAGE_BYTES = 2_500_000L

    fun responsive(sizes: List<WidgetHostDimensions>, density: Float): List<WidgetHostDimensions> {
        if (!density.isFinite() || density <= 0f || density > 8f) return emptyList()
        val valid = sizes.filter { valid(it.widthDp) && valid(it.heightDp) }.distinct()
        if (valid.isEmpty()) return emptyList()
        val anchors = listOfNotNull(
            valid.minByOrNull { it.widthDp * it.heightDp },
            valid.maxByOrNull { it.widthDp * it.heightDp },
            valid.minByOrNull { it.widthDp },
            valid.maxByOrNull { it.widthDp },
            valid.minByOrNull { it.heightDp },
            valid.maxByOrNull { it.heightDp },
        )
        val selected = mutableListOf<WidgetHostDimensions>()
        var bytes = 0L
        for (size in (anchors + valid).distinct()) {
            if (selected.size >= MAX_VARIANTS) break
            val cost = estimatedBitmapBytes(size, density)
            if (bytes + cost > MAX_AGGREGATE_IMAGE_BYTES) continue
            selected += size
            bytes += cost
        }
        return selected.sortedBy(valid::indexOf)
    }

    fun legacy(options: WidgetLegacyOptions, defaults: WidgetLegacyDefaults): WidgetOrientationDimensions {
        fun dimension(value: Int?, fallback: Int): Int =
            value?.takeIf { it > 0 && it <= MAX_DIMENSION_DP.toInt() } ?: fallback
        val minWidth = dimension(options.minWidthDp, defaults.minWidthDp)
        val maxWidth = dimension(options.maxWidthDp, defaults.maxWidthDp)
        val minHeight = dimension(options.minHeightDp, defaults.minHeightDp)
        val maxHeight = dimension(options.maxHeightDp, defaults.maxHeightDp)
        if (maxWidth < minWidth || maxHeight < minHeight) {
            return WidgetOrientationDimensions(
                WidgetPreviewDimensions(defaults.minWidthDp, defaults.maxHeightDp),
                WidgetPreviewDimensions(defaults.maxWidthDp, defaults.minHeightDp),
            )
        }
        return WidgetOrientationDimensions(
            WidgetPreviewDimensions(minWidth, maxHeight),
            WidgetPreviewDimensions(maxWidth, minHeight),
        )
    }

    private fun valid(value: Float): Boolean =
        value.isFinite() && value >= 1f && value <= MAX_DIMENSION_DP

    private fun estimatedBitmapBytes(size: WidgetHostDimensions, density: Float): Long {
        val width = (size.widthDp * density).roundToInt().coerceAtLeast(1)
        val height = (size.heightDp * density).roundToInt().coerceAtLeast(1)
        val scale = minOf(1f, MAX_BITMAP_EDGE_PX / maxOf(width, height))
        val rasterWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val rasterHeight = (height * scale).roundToInt().coerceAtLeast(1)
        return rasterWidth.toLong() * rasterHeight * 4L
    }
}
