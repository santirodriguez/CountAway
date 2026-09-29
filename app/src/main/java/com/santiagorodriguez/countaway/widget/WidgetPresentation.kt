package com.santiagorodriguez.countaway.widget

import android.view.View
import android.widget.RemoteViews
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.ArrivalMood
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.ceil

internal data class WidgetPresentation(
    val countText: String,
    val unitRes: Int?,
    val milestone: String?,
    val showUnit: Boolean,
    val showMilestone: Boolean,
    val showDate: Boolean,
    val showIcon: Boolean,
    val showTitle: Boolean,
    val titleMaxLines: Int,
)

internal data class WidgetPlaceholderPresentation(
    val showIcon: Boolean,
    val showTitle: Boolean,
    val showUnit: Boolean,
    val titleMaxLines: Int,
)

internal object WidgetPresentationResolver {
    fun placeholder(size: WidgetSize, fontScale: Float) = WidgetPlaceholderPresentation(
        showIcon = size == WidgetSize.SHORT || (size == WidgetSize.LARGE && fontScale < 1.3f),
        showTitle = size != WidgetSize.COMPACT || fontScale < 1.3f,
        showUnit = when (size) {
            WidgetSize.STANDARD -> fontScale < 1.3f
            WidgetSize.LARGE -> fontScale < 1.75f
            else -> false
        },
        titleMaxLines = if (size == WidgetSize.SHORT) 2 else 1,
    )

    fun resolve(
        content: WidgetEventContent,
        size: WidgetSize,
        fontScale: Float,
        heightDp: Int = WidgetPreviewSizing.representative(size).heightDp,
    ): WidgetPresentation {
        val largeFont = fontScale >= LARGE_FONT_SCALE
        val milestone = ArrivalMood.marker(content.status)
        val showUnit = (size == WidgetSize.STANDARD || size == WidgetSize.LARGE) && !largeFont
        val showMilestone = milestone != null &&
            size != WidgetSize.SHORT && size != WidgetSize.COMPACT && fontScale < 1.3f
        val showDate = size == WidgetSize.LARGE && fontScale < DATE_HIDE_FONT_SCALE
        val compactTitleLines = ((heightDp - 8 - ceil(28 * fontScale).toInt()) /
            ceil(14 * fontScale).toInt()).coerceIn(0, 2)
        val showIcon = when (size) {
            WidgetSize.COMPACT -> compactTitleLines > 0 && heightDp >=
                8 + ceil(28 * fontScale).toInt() +
                compactTitleLines * ceil(14 * fontScale).toInt() + 16
            WidgetSize.SHORT -> true
            else -> fontScale < 1.3f && milestone == null
        }
        // Reserve space for the count and visible details before allowing another title line.
        // Width alone cannot tell a minimum-size widget from a narrow, tall launcher cell.
        val titleMaxLines = when (size) {
            WidgetSize.COMPACT -> compactTitleLines.coerceAtLeast(1)
            WidgetSize.SHORT -> 2
            WidgetSize.STANDARD, WidgetSize.LARGE -> {
                val isLarge = size == WidgetSize.LARGE
                val padding = if (isLarge) 28 else 20
                val count = ceil((if (isLarge) 22 else 20) * fontScale).toInt()
                val icon = if (showIcon) { if (isLarge) 35 else 25 } else 0
                val unit = if (showUnit) ceil((if (isLarge) 20 else 16) * fontScale).toInt() else 0
                val marker = if (showMilestone) ceil((if (isLarge) 17 else 14) * fontScale).toInt() else 0
                val date = if (showDate) ceil(20 * fontScale).toInt() + 4 else 0
                val line = ceil((if (isLarge) 22 else 18) * fontScale).toInt()
                ((heightDp - padding - count - icon - unit - marker - date) / line).coerceIn(1, 2)
            }
        }

        return WidgetPresentation(
            countText = content.countTextFor(size),
            unitRes = content.unitRes,
            milestone = milestone,
            showUnit = showUnit,
            showMilestone = showMilestone,
            showDate = showDate,
            showIcon = showIcon,
            showTitle = size != WidgetSize.COMPACT || compactTitleLines > 0,
            titleMaxLines = titleMaxLines,
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
    fun applyPlaceholder(
        views: RemoteViews,
        size: WidgetSize,
        fontScale: Float,
        title: String,
        action: String,
        countText: String,
    ) {
        val presentation = WidgetPresentationResolver.placeholder(size, fontScale)
        views.setImageViewResource(R.id.widgetIcon, R.drawable.ic_event_calendar)
        views.setTextViewText(R.id.widgetTitle, title)
        views.setTextViewText(R.id.widgetCount, countText)
        views.setTextViewText(R.id.widgetUnit, action)
        views.setTextViewText(R.id.widgetDate, "")
        views.setTextViewText(R.id.widgetMilestone, "")
        views.setInt(R.id.widgetTitle, "setMaxLines", presentation.titleMaxLines)
        views.setViewVisibility(R.id.widgetIcon, if (presentation.showIcon) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widgetTitle, if (presentation.showTitle) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widgetUnit, if (presentation.showUnit) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widgetDate, View.GONE)
        views.setViewVisibility(R.id.widgetMilestone, View.GONE)
    }

    fun applyEvent(
        context: android.content.Context,
        views: RemoteViews,
        content: WidgetEventContent,
        size: WidgetSize,
        fontScale: Float,
        heightDp: Int = WidgetPreviewSizing.representative(size).heightDp,
    ) {
        val presentation = WidgetPresentationResolver.resolve(content, size, fontScale, heightDp)
        val locale = context.resources.configuration.locales[0]
        val formattedDate = content.date.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale),
        )

        views.setViewVisibility(R.id.widgetIcon, if (presentation.showIcon) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widgetTitle, if (presentation.showTitle) View.VISIBLE else View.GONE)
        views.setInt(R.id.widgetTitle, "setMaxLines", presentation.titleMaxLines)
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
