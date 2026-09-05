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
import com.santiagorodriguez.countaway.data.CountdownDataProblem
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.ui.EditorActivity
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
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 40)
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40)
            val size = WidgetSize.fromDimensions(widthDp, heightDp)
            val layoutId = when (size) {
                WidgetSize.COMPACT -> R.layout.widget_countdown_compact
                WidgetSize.STANDARD -> R.layout.widget_countdown_standard
                WidgetSize.LARGE -> R.layout.widget_countdown_large
            }
            val views = RemoteViews(context.packageName, layoutId)
            val configuration = WidgetPreferences(context).get(appWidgetId)
            val loadResult = CountdownRepository(context).loadResult()
            val theme = resolveTheme(displayContext, configuration?.appearance ?: WidgetAppearance.SYSTEM)
            val background = configuration?.background ?: WidgetBackground.CLASSIC
            val today = LocalDate.now()

            applyTheme(context, views, theme, background, widthDp, heightDp)
            when (loadResult) {
                is CountdownLoadResult.Failure -> renderDataError(
                    displayContext,
                    views,
                    appWidgetId,
                    loadResult.problem,
                )
                is CountdownLoadResult.Success -> {
                    val event = configuration?.let { configured ->
                        WidgetEventResolver.resolve(
                            selection = configured.eventSelection,
                            eventId = configured.eventId,
                            events = loadResult.events,
                            today = today,
                        )
                    }
                    if (event == null) {
                        renderUnconfigured(
                            displayContext,
                            views,
                            appWidgetId,
                            noUpcoming = configuration?.eventSelection == WidgetEventSelection.NEXT,
                        )
                    } else {
                        renderEvent(displayContext, views, appWidgetId, event, today, size)
                    }
                }
            }
            manager.updateAppWidget(appWidgetId, views)
        }

        private fun renderEvent(
            context: Context,
            views: RemoteViews,
            appWidgetId: Int,
            event: CountdownEvent,
            today: LocalDate,
            size: WidgetSize,
        ) {
            val content = WidgetEventContentFactory.from(event, today)
            val mood = ArrivalMood.marker(content.status)
            val locale = context.resources.configuration.locales[0]
            val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

            views.setImageViewResource(R.id.widgetIcon, content.iconRes)
            views.setTextViewText(R.id.widgetTitle, content.title)
            views.setTextViewText(R.id.widgetCount, content.countTextFor(size))
            views.setTextViewText(R.id.widgetUnit, content.unitRes?.let(context::getString).orEmpty())
            views.setTextViewText(R.id.widgetDate, event.date.format(dateFormatter))
            views.setTextViewText(R.id.widgetMilestone, mood ?: "")
            views.setViewVisibility(R.id.widgetMilestone, if (mood == null) View.GONE else View.VISIBLE)
            views.setOnClickPendingIntent(R.id.widgetRoot, editPendingIntent(context, appWidgetId, event.id))
        }

        private fun renderUnconfigured(
            context: Context,
            views: RemoteViews,
            appWidgetId: Int,
            noUpcoming: Boolean,
        ) {
            views.setImageViewResource(R.id.widgetIcon, R.drawable.ic_event_calendar)
            views.setTextViewText(
                R.id.widgetTitle,
                context.getString(if (noUpcoming) R.string.widget_no_upcoming else R.string.widget_select_countdown),
            )
            views.setTextViewText(R.id.widgetCount, "—")
            views.setTextViewText(R.id.widgetUnit, context.getString(R.string.widget_tap_to_configure))
            views.setTextViewText(R.id.widgetDate, "")
            views.setTextViewText(R.id.widgetMilestone, "")
            views.setViewVisibility(R.id.widgetMilestone, View.GONE)
            views.setOnClickPendingIntent(R.id.widgetRoot, configurePendingIntent(context, appWidgetId))
        }

        private fun renderDataError(
            context: Context,
            views: RemoteViews,
            appWidgetId: Int,
            problem: CountdownDataProblem,
        ) {
            views.setImageViewResource(R.id.widgetIcon, R.drawable.ic_event_calendar)
            views.setTextViewText(
                R.id.widgetTitle,
                context.getString(
                    if (problem == CountdownDataProblem.UNSUPPORTED_SCHEMA) {
                        R.string.widget_data_newer_version
                    } else {
                        R.string.widget_data_error
                    },
                ),
            )
            views.setTextViewText(R.id.widgetCount, "!")
            views.setTextViewText(R.id.widgetUnit, context.getString(R.string.widget_open_app))
            views.setTextViewText(R.id.widgetDate, "")
            views.setTextViewText(R.id.widgetMilestone, "")
            views.setViewVisibility(R.id.widgetMilestone, View.GONE)
            views.setOnClickPendingIntent(R.id.widgetRoot, openAppPendingIntent(context, appWidgetId))
        }

        private fun applyTheme(
            context: Context,
            views: RemoteViews,
            theme: WidgetTheme,
            background: WidgetBackground,
            widthDp: Int,
            heightDp: Int,
        ) {
            views.setImageViewBitmap(
                R.id.widgetBackground,
                WidgetBackgroundRenderer.render(context, background, theme.dark, widthDp, heightDp),
            )
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
            val dark = appearance.resolveDark(systemDark)
            return if (dark) {
                WidgetTheme(
                    dark = true,
                    primaryTextColor = context.getColor(R.color.widget_dark_text),
                    secondaryTextColor = context.getColor(R.color.widget_dark_secondary_text),
                    accentTextColor = context.getColor(R.color.widget_dark_accent),
                )
            } else {
                WidgetTheme(
                    dark = false,
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

        private fun openAppPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, WidgetConfigActivity::class.java)
            intent.setData(Uri.parse("countaway://widget/$appWidgetId/data-error"))
            return PendingIntent.getActivity(
                context,
                DATA_ERROR_REQUEST_CODE_OFFSET + appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private const val CONFIG_REQUEST_CODE_OFFSET = 100_000
        private const val DATA_ERROR_REQUEST_CODE_OFFSET = 200_000
    }

    private data class WidgetTheme(
        val dark: Boolean,
        val primaryTextColor: Int,
        val secondaryTextColor: Int,
        val accentTextColor: Int,
    )
}
