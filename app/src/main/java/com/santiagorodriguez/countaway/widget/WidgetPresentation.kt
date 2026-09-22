package com.santiagorodriguez.countaway.widget

import android.view.View
import android.widget.RemoteViews
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.ArrivalMood
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal data class WidgetPresentation(
    val countText: String,
    val unitRes: Int?,
    val milestone: String?,
    val showUnit: Boolean,
    val showMilestone: Boolean,
    val showDate: Boolean,
)

internal object WidgetPresentationResolver {
    fun resolve(
        content: WidgetEventContent,
        size: WidgetSize,
        fontScale: Float,
    ): WidgetPresentation {
        val largeFont = fontScale >= LARGE_FONT_SCALE
        val milestone = ArrivalMood.marker(content.status)

        return WidgetPresentation(
            countText = content.countTextFor(size),
            unitRes = content.unitRes,
            milestone = milestone,
            showUnit = when (size) {
                WidgetSize.STANDARD,
                WidgetSize.LARGE,
                -> !largeFont
                WidgetSize.COMPACT,
                WidgetSize.SHORT,
                -> false
            },
            showMilestone = milestone != null &&
                size != WidgetSize.SHORT &&
                !largeFont,
            showDate = size == WidgetSize.LARGE && fontScale < DATE_HIDE_FONT_SCALE,
        )
    }

    private const val LARGE_FONT_SCALE = 1.75f
    private const val DATE_HIDE_FONT_SCALE = 1.50f
}

internal object WidgetLayoutResolver {
    fun layoutRes(size: WidgetSize): Int = when (size) {
        WidgetSize.COMPACT -> R.layout.widget_countdown_compact
        WidgetSize.SHORT -> R.layout.widget_countdown_short
        WidgetSize.STANDARD -> R.layout.widget_countdown_standard
        WidgetSize.LARGE -> R.layout.widget_countdown_large
    }
}

internal object WidgetRemoteViewsPresentation {
    fun applyEvent(
        context: android.content.Context,
        views: RemoteViews,
        content: WidgetEventContent,
        size: WidgetSize,
        fontScale: Float,
    ) {
        val presentation = WidgetPresentationResolver.resolve(content, size, fontScale)
        val locale = context.resources.configuration.locales[0]
        val formattedDate = content.date.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale),
        )

        views.setImageViewResource(R.id.widgetIcon, content.iconRes)
        views.setTextViewText(R.id.widgetTitle, content.title)
        views.setTextViewText(R.id.widgetCount, presentation.countText)
        views.setTextViewText(
            R.id.widgetUnit,
            presentation.unitRes?.let(context::getString).orEmpty(),
        )
        views.setTextViewText(R.id.widgetDate, formattedDate)
        views.setTextViewText(R.id.widgetMilestone, presentation.milestone.orEmpty())
        views.setViewVisibility(
            R.id.widgetUnit,
            if (presentation.showUnit) View.VISIBLE else View.GONE,
        )
        views.setViewVisibility(
            R.id.widgetMilestone,
            if (presentation.showMilestone) View.VISIBLE else View.GONE,
        )
        views.setViewVisibility(
            R.id.widgetDate,
            if (presentation.showDate) View.VISIBLE else View.GONE,
        )
    }
}
