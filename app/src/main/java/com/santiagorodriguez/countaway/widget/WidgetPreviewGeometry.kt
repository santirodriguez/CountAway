package com.santiagorodriguez.countaway.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.widget.FrameLayout
import android.view.Gravity
import kotlin.math.roundToInt

/** Configuration previews use one real orientation, never independent minimum extrema. */
internal object WidgetPreviewGeometry {
    fun dimensions(options: Bundle, landscape: Boolean, timed: Boolean): WidgetPreviewDimensions {
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
        return if (landscape) pair.landscape else pair.portrait
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
