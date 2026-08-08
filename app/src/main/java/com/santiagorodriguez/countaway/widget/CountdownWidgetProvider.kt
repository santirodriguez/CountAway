package com.santiagorodriguez.countaway.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
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
            val displayContext = LanguageManager.localizedContext(context)
            val options = manager.getAppWidgetOptions(appWidgetId)
            val size = WidgetSize.fromDimensions(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 40),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40),
            )
            val layoutId = when (size) {
                WidgetSize.COMPACT -> R.layout.widget_countdown_compact
                WidgetSize.STANDARD -> R.layout.widget_countdown_standard
                WidgetSize.LARGE -> R.layout.widget_countdown_large
            }
            val views = RemoteViews(context.packageName, layoutId)
            val configuration = WidgetPreferences(context).get(appWidgetId)
            val event = configuration?.let { configured ->
                CountdownRepository(context).load().firstOrNull { it.id == configured.eventId }
            }
            val theme = resolveTheme(
                context.applicationContext,
                configuration?.appearance ?: WidgetAppearance.SYSTEM,
            )

            applyTheme(views, theme)
            if (event == null) {
                renderUnconfigured(displayContext, views, appWidgetId)
            } else {
                renderEvent(displayContext, views, appWidgetId, event)
            }
            manager.updateAppWidget(appWidgetId, views)
        }

        private fun renderEvent(
            context: Context,
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
                -> context.getString(R.string.widget_days_left)
                CountdownStatus.TOMORROW -> context.getString(R.string.status_tomorrow)
                CountdownStatus.TODAY -> context.getString(R.string.status_today)
                CountdownStatus.DONE -> ""
            }
            val mood = ArrivalMood.marker(value.status)
            val locale = context.resources.configuration.locales[0]
            val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

            views.setImageViewResource(R.id.widgetIcon, EventIconPresentation.drawableRes(event.icon))
            views.setTextViewText(R.id.widgetTitle, event.title)
            views.setTextViewText(R.id.widgetCount, countText)
            views.setTextViewText(R.id.widgetUnit, unitText)
            views.setTextViewText(R.id.widgetDate, event.date.format(dateFormatter))
            views.setTextViewText(R.id.widgetMilestone, mood ?: "")
            views.setViewVisibility(R.id.widgetMilestone, if (mood == null) View.GONE else View.VISIBLE)
            views.setOnClickPendingIntent(R.id.widgetRoot, editPendingIntent(context, appWidgetId, event.id))
        }

        private fun renderUnconfigured(context: Context, views: RemoteViews, appWidgetId: Int) {
            views.setImageViewResource(R.id.widgetIcon, R.drawable.ic_event_calendar)
            views.setTextViewText(R.id.widgetTitle, context.getString(R.string.widget_select_countdown))
            views.setTextViewText(R.id.widgetCount, "—")
            views.setTextViewText(R.id.widgetUnit, context.getString(R.string.widget_tap_to_configure))
            views.setTextViewText(R.id.widgetDate, "")
            views.setTextViewText(R.id.widgetMilestone, "")
            views.setViewVisibility(R.id.widgetMilestone, View.GONE)
            views.setOnClickPendingIntent(R.id.widgetRoot, configurePendingIntent(context, appWidgetId))
        }

        private fun applyTheme(views: RemoteViews, theme: WidgetTheme) {
            views.setImageViewResource(R.id.widgetBackground, theme.backgroundRes)
            views.setInt(R.id.widgetIcon, "setColorFilter", theme.accentTextColor)
            views.setTextColor(R.id.widgetTitle, theme.primaryTextColor)
            views.setTextColor(R.id.widgetCount, theme.accentTextColor)
            views.setTextColor(R.id.widgetMilestone, theme.secondaryTextColor)
            views.setTextColor(R.id.widgetUnit, theme.secondaryTextColor)
            views.setTextColor(R.id.widgetDate, theme.secondaryTextColor)
        }

        private fun resolveTheme(context: Context, appearance: WidgetAppearance): WidgetTheme {
            val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
            val dark = when (appearance) {
                WidgetAppearance.SYSTEM -> systemDark
                WidgetAppearance.LIGHT -> false
                WidgetAppearance.DARK -> true
            }
            return if (dark) {
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
