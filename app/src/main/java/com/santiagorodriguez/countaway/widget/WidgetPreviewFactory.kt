package com.santiagorodriguez.countaway.widget

import android.content.Context
import android.widget.RemoteViews
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.model.CountdownEvent
import java.time.LocalDate

internal object WidgetPreviewFactory {
    fun remoteViews(context: Context): RemoteViews {
        val style = WidgetDefaultsPreferences(context.applicationContext).get()
        val dimensions = WidgetPreviewSizing.representative(WidgetSize.COMPACT)
        val theme = WidgetThemeResolver.resolve(context, style.appearance, style.background)
        return RemoteViews(context.packageName, WidgetLayoutResolver.layoutRes(dimensions.size)).apply {
            applyTheme(context, this, style, theme, dimensions)
            WidgetRemoteViewsPresentation.applyPlaceholder(
                this, dimensions.size, context.resources.configuration.fontScale,
                context.getString(R.string.widget_select_event),
                context.getString(R.string.widget_tap_to_configure),
                context.getString(R.string.widget_preview_value), dimensions.heightDp,
            )
            setContentDescription(R.id.widgetRoot, listOf(
                context.getString(R.string.widget_select_event),
                context.getString(R.string.widget_tap_to_configure),
            ).joinToString(", "))
        }
    }

    fun remoteViews(
        context: Context,
        event: CountdownEvent,
        style: WidgetStyleSelection,
        today: LocalDate = CountdownTime.snapshot().today,
        dimensions: WidgetPreviewDimensions = WidgetPreviewSizing.representative(WidgetSize.COMPACT),
    ): RemoteViews {
        val content = WidgetEventContentFactory.from(event, today)
        val theme = WidgetThemeResolver.resolve(context, style.appearance, style.background)
        val views = RemoteViews(context.packageName, WidgetLayoutResolver.layoutRes(dimensions.size))
        applyTheme(context, views, style, theme, dimensions)
        WidgetRemoteViewsPresentation.applyEvent(
            context = context,
            views = views,
            content = content,
            size = dimensions.size,
            fontScale = context.resources.configuration.fontScale,
            heightDp = dimensions.heightDp,
            widthDp = dimensions.widthDp,
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
        views.setImageViewBitmap(R.id.widgetBackground, WidgetBackgroundRenderer.render(
            context = context.applicationContext,
            background = style.background,
            dark = theme.dark,
            widthDp = dimensions.widthDp,
            heightDp = dimensions.heightDp,
        ))
        views.setInt(R.id.widgetIcon, "setColorFilter", theme.accentTextColor)
        views.setTextColor(R.id.widgetTitle, theme.primaryTextColor)
        views.setTextColor(R.id.widgetCount, theme.accentTextColor)
        views.setTextColor(R.id.widgetMilestone, theme.secondaryTextColor)
        views.setTextColor(R.id.widgetUnit, theme.secondaryTextColor)
        views.setTextColor(R.id.widgetDate, theme.secondaryTextColor)
    }
}
