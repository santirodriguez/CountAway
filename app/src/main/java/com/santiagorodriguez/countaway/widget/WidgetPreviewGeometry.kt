package com.santiagorodriguez.countaway.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.os.Build
import android.util.SizeF
import android.widget.FrameLayout
import android.view.Gravity
import kotlin.math.roundToInt

/** Configuration previews use one real orientation, never independent minimum extrema. */
internal object WidgetPreviewGeometry {
    fun dimensions(options: Bundle, landscape: Boolean, timed: Boolean, density: Float = 1f): WidgetPreviewDimensions {
        val fallback = if (timed) WidgetPreviewDimensions(150, 50) else WidgetPreviewSizing.defaultDays
        fun value(key: String): Int? = if (options.containsKey(key)) options.getInt(key) else null
        val minWidth = value(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = value(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val pair = WidgetHostGeometry.legacy(
            WidgetLegacyOptions(minWidth, value(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
                minHeight, value(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)),
            WidgetLegacyDefaults(minWidth?.takeIf { it in 1..2048 } ?: fallback.widthDp,
                minWidth?.takeIf { it in 1..2048 } ?: fallback.widthDp,
                minHeight?.takeIf { it in 1..2048 } ?: fallback.heightDp,
                minHeight?.takeIf { it in 1..2048 } ?: fallback.heightDp),
        )
        val legacy = if (landscape) pair.landscape else pair.portrait
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val candidates = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                .orEmpty().map { WidgetHostDimensions(it.width, it.height) }
            // Select an actual provider variant nearest the orientation's legacy slot.
            // Never synthesize a rectangle from separate size-list extrema.
            val sizes = WidgetHostGeometry.responsive(candidates, density)
            val nearest = sizes.minByOrNull {
                val dx = it.widthDp - legacy.widthDp
                val dy = it.heightDp - legacy.heightDp
                dx * dx + dy * dy
            }
            if (nearest != null) return WidgetPreviewDimensions(
                nearest.widthDp.roundToInt().coerceAtLeast(1),
                nearest.heightDp.roundToInt().coerceAtLeast(1))
        }
        return legacy
    }

    fun fit(container: FrameLayout, frame: FrameLayout, dimensions: WidgetPreviewDimensions) {
        container.post {
            if (container.width <= 0 || container.height <= 0) return@post
            val density = frame.resources.displayMetrics.density
            val width = (dimensions.widthDp * density).roundToInt().coerceAtLeast(1)
            val height = (dimensions.heightDp * density).roundToInt().coerceAtLeast(1)
            val fitted = WidgetPreviewSizing.fit(container.width, container.height, dimensions)
            frame.layoutParams = FrameLayout.LayoutParams(width, height, Gravity.CENTER)
            frame.pivotX = width / 2f
            frame.pivotY = height / 2f
            val scale = minOf(fitted.width.toFloat() / width, fitted.height.toFloat() / height)
            frame.scaleX = scale
            frame.scaleY = scale
        }
    }
}
