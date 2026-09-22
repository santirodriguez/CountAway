package com.santiagorodriguez.countaway.widget

import android.content.Context
import android.widget.RemoteViews
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.model.CountdownEvent
import java.time.LocalDate

internal object WidgetPreviewFactory {
    fun remoteViews(context: Context): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_countdown_preview)

    fun remoteViews(
        context: Context,
        event: CountdownEvent,
        style: WidgetStyleSelection,
        today: LocalDate = CountdownTime.snapshot().today,
    ): RemoteViews {
        val content = WidgetEventContentFactory.from(event, today)
        val theme = WidgetThemeResolver.resolve(context, style.appearance)
        return RemoteViews(context.packageName, R.layout.widget_countdown_preview).apply {
            setImageViewBitmap(
                R.id.widgetPinPreviewBackground,
                WidgetBackgroundRenderer.render(
                    context = context.applicationContext,
                    background = style.background,
                    dark = theme.dark,
                    widthDp = PREVIEW_WIDTH_DP,
                    heightDp = PREVIEW_HEIGHT_DP,
                ),
            )
            setImageViewResource(R.id.widgetPinPreviewIcon, content.iconRes)
            setTextViewText(R.id.widgetPinPreviewTitle, content.title)
            setTextViewText(R.id.widgetPinPreviewCount, content.countText)
            setTextViewText(
                R.id.widgetPinPreviewUnit,
                content.unitRes?.let(context::getString).orEmpty(),
            )
            setInt(R.id.widgetPinPreviewIcon, "setColorFilter", theme.accentTextColor)
            setTextColor(R.id.widgetPinPreviewTitle, theme.primaryTextColor)
            setTextColor(R.id.widgetPinPreviewCount, theme.accentTextColor)
            setTextColor(R.id.widgetPinPreviewUnit, theme.secondaryTextColor)
            setContentDescription(
                R.id.widgetPinPreviewRoot,
                listOf(
                    content.title,
                    content.countText,
                    content.unitRes?.let(context::getString).orEmpty(),
                ).filter(String::isNotBlank).joinToString(", "),
            )
        }
    }

    private const val PREVIEW_WIDTH_DP = 180
    private const val PREVIEW_HEIGHT_DP = 110
}
