package com.santiagorodriguez.countaway.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.ArrivalMood
import com.santiagorodriguez.countaway.countdown.CountdownCalculator
import com.santiagorodriguez.countaway.countdown.CountdownStatus
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.ui.EditorActivity
import com.santiagorodriguez.countaway.ui.EventIconPresentation
import com.santiagorodriguez.countaway.ui.LanguageManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class CountdownWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateScheduler.ensureScheduled(context)
    }

    override fun onDisabled(context: Context) {
        WidgetUpdateScheduler.cancel(context)
        super.onDisabled(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, manager, it) }
        WidgetUpdateScheduler.ensureScheduled(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val preferences = WidgetPreferences(context)
        appWidgetIds.forEach(preferences::remove)
        WidgetUpdateScheduler.ensureScheduled(context)
        super.onDeleted(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateWidget(context, manager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CountdownWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { updateWidget(context, manager, it) }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val deviceContext = context.applicationContext
            val displayContext = LanguageManager.localizedContext(context)
            val configuration = WidgetPreferences(deviceContext).get(appWidgetId)
            val event = configuration?.let { configured ->
                CountdownRepository(deviceContext).load().firstOrNull { it.id == configured.eventId }
            }
            val appearance = configuration?.appearance ?: WidgetAppearance.SYSTEM

            val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                responsiveViews(deviceContext, displayContext, appWidgetId, event, appearance)
            } else {
                val options = manager.getAppWidgetOptions(appWidgetId)
                val size = WidgetSize.fromDimensions(
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 40),
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40),
                )
                createWidgetViews(deviceContext, displayContext, appWidgetId, event, appearance, size)
            }

            manager.updateAppWidget(appWidgetId, views)
        }

        private fun responsiveViews(
            deviceContext: Context,
            displayContext: Context,
            appWidgetId: Int,
            event: CountdownEvent?,
            appearance: WidgetAppearance,
        ): RemoteViews {
            val layouts = WidgetSize.entries.associate { size ->
                SizeF(size.minWidthDp.toFloat(), size.minHeightDp.toFloat()) to createWidgetViews(
                    deviceContext,
                    displayContext,
                    appWidgetId,
                    event,
                    appearance,
                    size,
                )
            }
            return RemoteViews(layouts)
        }

        private fun createWidgetViews(
            deviceContext: Context,
            displayContext: Context,
            appWidgetId: Int,
            event: CountdownEvent?,
            appearance: WidgetAppearance,
            size: WidgetSize,
        ): RemoteViews {
            val layoutId = when (size) {
                WidgetSize.COMPACT -> R.layout.widget_countdown_compact
                WidgetSize.STANDARD -> R.layout.widget_countdown_standard
                WidgetSize.LARGE -> R.layout.widget_countdown_large
            }
            val views = RemoteViews(deviceContext.packageName, layoutId)

            applyAppearance(deviceContext, views, appearance)
            if (event == null) {
                renderUnconfigured(displayContext, deviceContext, views, appWidgetId)
            } else {
                renderEvent(displayContext, deviceContext, views, appWidgetId, event)
            }
            return views
        }

        private fun renderEvent(
            displayContext: Context,
            intentContext: Context,
            views: RemoteViews,
            appWidgetId: Int,
            event: CountdownEvent,
        ) {
            val value = CountdownCalculator.value(LocalDate.now(), event.date)
            val countText = when (value.status) {
                CountdownStatus.FUTURE,
                CountdownStatus.THREE_DAYS,
                CountdownStatus.TWO_DAYS,
                CountdownStatus.TOMORROW,
                CountdownStatus.TODAY,
                -> value.days.coerceAtLeast(0).toString()
                CountdownStatus.DONE -> "✓"
            }
            val unitText = when (value.status) {
                CountdownStatus.FUTURE,
                CountdownStatus.THREE_DAYS,
                CountdownStatus.TWO_DAYS,
                -> displayContext.getString(R.string.widget_days_left)
                CountdownStatus.TOMORROW -> displayContext.getString(R.string.status_tomorrow)
                CountdownStatus.TODAY -> displayContext.getString(R.string.status_today)
                CountdownStatus.DONE -> ""
            }
            val mood = ArrivalMood.marker(value.status)
            val locale = displayContext.resources.configuration.locales[0]
            val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

            views.setImageViewResource(R.id.widgetIcon, EventIconPresentation.drawableRes(event.icon))
            views.setTextViewText(R.id.widgetTitle, event.title)
            views.setTextViewText(R.id.widgetCount, countText)
            views.setTextViewText(R.id.widgetUnit, unitText)
            views.setTextViewText(R.id.widgetDate, event.date.format(dateFormatter))
            views.setTextViewText(R.id.widgetMilestone, mood ?: "")
            views.setViewVisibility(R.id.widgetMilestone, if (mood == null) View.GONE else View.VISIBLE)
            views.setOnClickPendingIntent(R.id.widgetRoot, editPendingIntent(intentContext, appWidgetId, event.id))
        }

        private fun renderUnconfigured(
            displayContext: Context,
            intentContext: Context,
            views: RemoteViews,
            appWidgetId: Int,
        ) {
            views.setImageViewResource(R.id.widgetIcon, R.drawable.ic_event_calendar)
            views.setTextViewText(R.id.widgetTitle, displayContext.getString(R.string.widget_select_countdown))
            views.setTextViewText(R.id.widgetCount, "—")
            views.setTextViewText(R.id.widgetUnit, displayContext.getString(R.string.widget_tap_to_configure))
            views.setTextViewText(R.id.widgetDate, "")
            views.setTextViewText(R.id.widgetMilestone, "")
            views.setViewVisibility(R.id.widgetMilestone, View.GONE)
            views.setOnClickPendingIntent(R.id.widgetRoot, configurePendingIntent(intentContext, appWidgetId))
        }

        private fun applyAppearance(context: Context, views: RemoteViews, appearance: WidgetAppearance) {
            when (appearance) {
                WidgetAppearance.SYSTEM -> applySystemAppearance(context, views)
                WidgetAppearance.LIGHT -> applyTheme(views, widgetTheme(context, dark = false))
                WidgetAppearance.DARK -> applyTheme(views, widgetTheme(context, dark = true))
            }
        }

        private fun applySystemAppearance(context: Context, views: RemoteViews) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                applyTheme(views, widgetTheme(context, dark = isSystemDark(context)))
                return
            }

            val light = widgetTheme(context, dark = false)
            val dark = widgetTheme(context, dark = true)
            views.setIcon(
                R.id.widgetBackground,
                "setImageIcon",
                Icon.createWithResource(context, light.backgroundRes),
                Icon.createWithResource(context, dark.backgroundRes),
            )
            views.setColorInt(R.id.widgetIcon, "setColorFilter", light.accentTextColor, dark.accentTextColor)
            views.setColorInt(R.id.widgetTitle, "setTextColor", light.primaryTextColor, dark.primaryTextColor)
            views.setColorInt(R.id.widgetCount, "setTextColor", light.accentTextColor, dark.accentTextColor)
            views.setColorInt(
                R.id.widgetMilestone,
                "setTextColor",
                light.secondaryTextColor,
                dark.secondaryTextColor,
            )
            views.setColorInt(R.id.widgetUnit, "setTextColor", light.secondaryTextColor, dark.secondaryTextColor)
            views.setColorInt(R.id.widgetDate, "setTextColor", light.secondaryTextColor, dark.secondaryTextColor)
        }

        private fun isSystemDark(context: Context): Boolean =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

        private fun applyTheme(views: RemoteViews, theme: WidgetTheme) {
            views.setImageViewResource(R.id.widgetBackground, theme.backgroundRes)
            views.setInt(R.id.widgetIcon, "setColorFilter", theme.accentTextColor)
            views.setTextColor(R.id.widgetTitle, theme.primaryTextColor)
            views.setTextColor(R.id.widgetCount, theme.accentTextColor)
            views.setTextColor(R.id.widgetMilestone, theme.secondaryTextColor)
            views.setTextColor(R.id.widgetUnit, theme.secondaryTextColor)
            views.setTextColor(R.id.widgetDate, theme.secondaryTextColor)
        }

        private fun widgetTheme(context: Context, dark: Boolean): WidgetTheme = if (dark) {
            WidgetTheme(
                backgroundRes = R.drawable.widget_background_dark,
                primaryTextColor = context.getColor(R.color.widget_dark_text),
                secondaryTextColor = context.getColor(R.color.widget_dark_secondary_text),
                accentTextColor = context.getColor(R.color.widget_dark_accent),
            )
        } else {
            WidgetTheme(
                backgroundRes = R.drawable.widget_background_light,
                primaryTextColor = context.getColor(R.color.widget_light_text),
                secondaryTextColor = context.getColor(R.color.widget_light_secondary_text),
                accentTextColor = context.getColor(R.color.widget_light_accent),
            )
        }

        private fun editPendingIntent(context: Context, appWidgetId: Int, eventId: String): PendingIntent {
            val intent = Intent(context, EditorActivity::class.java)
                .putExtra(EditorActivity.EXTRA_EVENT_ID, eventId)
                .setData(Uri.parse("countaway://widget/$appWidgetId/event/$eventId"))
            return PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun configurePendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, WidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .setData(Uri.parse("countaway://widget/$appWidgetId/configure"))
            return PendingIntent.getActivity(
                context,
                CONFIG_REQUEST_CODE_OFFSET + appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private const val CONFIG_REQUEST_CODE_OFFSET = 100_000
    }

    private data class WidgetTheme(
        val backgroundRes: Int,
        val primaryTextColor: Int,
        val secondaryTextColor: Int,
        val accentTextColor: Int,
    )
}
