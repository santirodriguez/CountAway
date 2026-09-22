package com.santiagorodriguez.countaway.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.model.CountdownEvent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal object WidgetPreviewFactory {
    fun remoteViews(context: Context): RemoteViews {
        val style = WidgetDefaultsPreferences(context.applicationContext).get()
        val dimensions = WidgetPreviewSizing.representative(WidgetSize.STANDARD)
        val theme = WidgetThemeResolver.resolve(context, style.appearance, style.background)
        return RemoteViews(context.packageName, WidgetLayoutResolver.layoutRes(dimensions.size)).apply {
            applyTheme(context, this, style, theme, dimensions)
            setImageViewResource(R.id.widgetIcon, R.drawable.ic_event_calendar)
            setTextViewText(R.id.widgetTitle, context.getString(R.string.widget_select_countdown))
            setTextViewText(R.id.widgetCount, context.getString(R.string.widget_preview_value))
            setTextViewText(R.id.widgetUnit, context.getString(R.string.widget_tap_to_configure))
            setTextViewText(R.id.widgetMilestone, "")
            setTextViewText(R.id.widgetDate, "")
            setViewVisibility(R.id.widgetMilestone, View.GONE)
            setViewVisibility(R.id.widgetDate, View.GONE)
            setContentDescription(
                R.id.widgetRoot,
                listOf(
                    context.getString(R.string.widget_select_countdown),
                    context.getString(R.string.widget_tap_to_configure),
                ).joinToString(", "),
            )
        }
    }

    fun remoteViews(
        context: Context,
        event: CountdownEvent,
        style: WidgetStyleSelection,
        today: LocalDate = CountdownTime.snapshot().today,
    ): RemoteViews {
        val dimensions = WidgetPreviewSizing.representative(WidgetSize.STANDARD)
        val size = dimensions.size
        val content = WidgetEventContentFactory.from(event, today)
        val theme = WidgetThemeResolver.resolve(context, style.appearance, style.background)
        val views = RemoteViews(context.packageName, WidgetLayoutResolver.layoutRes(size))

        applyTheme(context, views, style, theme, dimensions)
        WidgetRemoteViewsPresentation.applyEvent(
            context = context,
            views = views,
            content = content,
            size = size,
            fontScale = context.resources.configuration.fontScale,
        )

        val locale = context.resources.configuration.locales[0]
        val formattedDate = content.date.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale),
        )
        views.setContentDescription(
            R.id.widgetRoot,
            listOf(
                content.title,
                content.countText,
                content.unitRes?.let(context::getString).orEmpty(),
                formattedDate,
            ).filter(String::isNotBlank).joinToString(", "),
        )
        return views
    }

    private fun applyTheme(
        context: Context,
        views: RemoteViews,
        style: WidgetStyleSelection,
        theme: WidgetTheme,
        dimensions: WidgetPreviewDimensions,
    ) {
        views.setImageViewBitmap(
            R.id.widgetBackground,
            WidgetBackgroundRenderer.render(
                context = context.applicationContext,
                background = style.background,
                dark = theme.dark,
                widthDp = dimensions.widthDp,
                heightDp = dimensions.heightDp,
            ),
        )
        views.setInt(R.id.widgetIcon, "setColorFilter", theme.accentTextColor)
        views.setTextColor(R.id.widgetTitle, theme.primaryTextColor)
        views.setTextColor(R.id.widgetCount, theme.accentTextColor)
        views.setTextColor(R.id.widgetMilestone, theme.secondaryTextColor)
        views.setTextColor(R.id.widgetUnit, theme.secondaryTextColor)
        views.setTextColor(R.id.widgetDate, theme.secondaryTextColor)
    }
}
