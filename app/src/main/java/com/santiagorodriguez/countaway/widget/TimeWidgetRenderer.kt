package com.santiagorodriguez.countaway.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownTimeSnapshot
import com.santiagorodriguez.countaway.countdown.TimedEventResolver
import com.santiagorodriguez.countaway.data.CountdownDataProblem
import com.santiagorodriguez.countaway.data.EventTransport
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.ui.EditorActivity
import com.santiagorodriguez.countaway.ui.EventIconPresentation
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal data class TimeWidgetBackgroundKey(
    val background: WidgetBackground, val dark: Boolean, val width: Int, val height: Int,
)

/** App-widget host compatible: only RemoteViews-native views, with no app-side ticking. */
internal object TimeWidgetRenderer {
    fun render(
        context: Context,
        displayContext: Context,
        widgetId: Int,
        configuration: WidgetConfiguration?,
        event: CountdownEvent?,
        problem: CountdownDataProblem?,
        snapshot: CountdownTimeSnapshot,
        widthDp: Int,
        heightDp: Int,
        cache: MutableMap<TimeWidgetBackgroundKey, Bitmap>,
        interactive: Boolean = true,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_time_live)
        val appearance = configuration?.appearance ?: WidgetAppearance.SYSTEM
        val background = configuration?.background ?: WidgetBackground.HORIZON
        val theme = WidgetThemeResolver.resolve(context, appearance, background)
        val key = TimeWidgetBackgroundKey(background, theme.dark, widthDp, heightDp)
        val bitmap = cache.getOrPut(key) {
            WidgetBackgroundRenderer.render(context.applicationContext, background, theme.dark, widthDp, heightDp)
        }
        views.setImageViewBitmap(R.id.timeWidgetBackground, bitmap)
        views.setInt(R.id.timeWidgetIcon, "setColorFilter", theme.accentTextColor)
        views.setTextColor(R.id.timeWidgetTitle, theme.primaryTextColor)
        views.setTextColor(R.id.timeWidgetCaption, theme.secondaryTextColor)
        views.setTextColor(R.id.timeWidgetClock, theme.accentTextColor)
        views.setTextColor(R.id.timeWidgetFallback, theme.accentTextColor)
        views.setTextColor(R.id.timeWidgetDate, theme.secondaryTextColor)

        val timed = event?.takeIf { it.eventTime != null && it.eventZone != null }
        val count = timed?.let { runCatching { TimedEventResolver.resolve(it, snapshot.now) }.getOrNull() }
        if (count == null) {
            val message = if (problem == CountdownDataProblem.UNSUPPORTED_SCHEMA) {
                R.string.widget_data_newer_version
            } else if (problem != null) {
                R.string.widget_data_error
            } else {
                R.string.time_widget_choose_event
            }
            views.setImageViewResource(R.id.timeWidgetIcon, R.drawable.ic_event_calendar)
            views.setTextViewText(R.id.timeWidgetTitle, displayContext.getString(message))
            views.setTextViewText(R.id.timeWidgetCaption, displayContext.getString(R.string.widget_tap_to_configure))
            views.setViewVisibility(R.id.timeWidgetClock, View.GONE)
            views.setViewVisibility(R.id.timeWidgetFallback, View.GONE)
            views.setViewVisibility(R.id.timeWidgetDate, View.GONE)
            if (interactive) views.setOnClickPendingIntent(R.id.timeWidgetRoot,
                configureIntent(context, widgetId))
            return views
        }

        views.setImageViewResource(R.id.timeWidgetIcon, EventIconPresentation.drawableRes(timed.icon))
        views.setTextViewText(R.id.timeWidgetTitle, EventTransport.preview(timed.title))
        views.setTextViewText(R.id.timeWidgetCaption, displayContext.getString(R.string.time_widget_total_hours))
        val targetText = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(displayContext.resources.configuration.locales[0])
            .format(count.target)
        val zoneSuffix = if (count.target.zone != snapshot.zone) " · " + count.target.zone.id else ""
        views.setTextViewText(R.id.timeWidgetDate, targetText + zoneSuffix)
        views.setViewVisibility(R.id.timeWidgetDate, View.VISIBLE)
        val plan = TimeWidgetClockPolicy.plan(
            snapshot.now.toInstant(), count.target.toInstant(), SystemClock.elapsedRealtime(), timed.countMode,
        )
        if (plan == null) {
            views.setViewVisibility(R.id.timeWidgetClock, View.GONE)
            views.setViewVisibility(R.id.timeWidgetFallback, View.VISIBLE)
            views.setTextViewText(R.id.timeWidgetFallback,
                displayContext.getString(R.string.time_widget_far_target))
        } else {
            views.setViewVisibility(R.id.timeWidgetClock, View.VISIBLE)
            views.setViewVisibility(R.id.timeWidgetFallback, View.GONE)
            views.setChronometer(R.id.timeWidgetClock, plan.elapsedRealtimeBase, null, true)
            views.setChronometerCountDown(R.id.timeWidgetClock, plan.countDown)
        }
        if (interactive) views.setOnClickPendingIntent(R.id.timeWidgetRoot,
            editIntent(context, widgetId, timed.id))
        return views
    }

    private fun editIntent(context: Context, widgetId: Int, eventId: String): PendingIntent {
        val wire = EventTransport.wireId(eventId)
        val intent = Intent(context, EditorActivity::class.java)
            .putExtra(EditorActivity.EXTRA_EVENT_ID, wire)
            .setData(Uri.parse("countaway://time-widget/" + widgetId + "/event/" + wire))
        return PendingIntent.getActivity(context, 300_000 + widgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun configureIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, TimeWidgetConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .setData(Uri.parse("countaway://time-widget/" + widgetId + "/configure"))
        return PendingIntent.getActivity(context, 400_000 + widgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
