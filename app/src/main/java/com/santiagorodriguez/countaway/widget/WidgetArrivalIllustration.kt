package com.santiagorodriguez.countaway.widget

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.ArrivalStage
import com.santiagorodriguez.countaway.ui.ArrivalIllustration
import kotlin.math.ceil

/** An overlay inside the existing count slot, never a new text row or a smaller count. */
internal object WidgetArrivalIllustration {
    fun supports(size: WidgetSize) = size == WidgetSize.STANDARD || size == WidgetSize.LARGE

    fun resource(context: Context, content: WidgetEventContent, size: WidgetSize, widthDp: Int, heightDp: Int): Int {
        val stage = ArrivalStage.from(content.status)
        val scale = context.resources.configuration.fontScale
        if (stage == ArrivalStage.NONE || !supports(size) || scale > 1.3f) return 0
        val large = size == WidgetSize.LARGE
        val presentation = WidgetPresentationResolver.resolve(content, size, scale, heightDp)
        val fixed = (if (large) 28 else 20) +
            presentation.titleMaxLines * ceil((if (large) 22 else 18) * scale).toInt() +
            (if (presentation.showIcon) { if (large) 35 else 25 } else 0) +
            (if (presentation.showUnit) 2 * ceil((if (large) 20 else 16) * scale).toInt() else 0) +
            (if (presentation.showDate) 2 * ceil(20 * scale).toInt() + 4 else 0)
        if (heightDp - fixed < 24) return 0
        val metrics = context.resources.displayMetrics
        val paint = Paint().apply {
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, if (large) 60f else 42f, metrics)
        }
        val countWidthDp = paint.measureText(presentation.countText) / metrics.density
        val innerWidth = widthDp - if (large) 28 else 20
        // The count remains centered. Reserve an equal safety margin on either side
        // using its maximum font size, even when Android currently draws it smaller.
        if (innerWidth - countWidthDp < 2 * (20 + 6 + 8)) return 0
        return ArrivalIllustration.resource(stage)
    }

    fun apply(context: Context, views: RemoteViews, content: WidgetEventContent,
        size: WidgetSize, widthDp: Int, heightDp: Int) {
        if (!supports(size)) return
        val resource = resource(context, content, size, widthDp, heightDp)
        if (resource != 0) views.setImageViewResource(R.id.widgetArrival, resource)
        views.setViewVisibility(R.id.widgetArrival, if (resource == 0) View.GONE else View.VISIBLE)
    }

    fun clear(views: RemoteViews, size: WidgetSize) {
        if (supports(size)) views.setViewVisibility(R.id.widgetArrival, View.GONE)
    }
}
