package com.santiagorodriguez.countaway.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownTimeSnapshot
import com.santiagorodriguez.countaway.countdown.TimedEventResolver
import com.santiagorodriguez.countaway.data.CountdownDataProblem
import com.santiagorodriguez.countaway.data.EventTransport
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.ui.EditorActivity
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

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
        val layout = TimeWidgetLayoutPlanner.plan(widthDp, heightDp,
            displayContext.resources.configuration.fontScale)
        // The pre-API-37 RemoteViews padding overload expects physical pixels.
        val density = displayContext.resources.displayMetrics.density
        val horizontalPaddingPx = (layout.horizontalPaddingDp * density).roundToInt()
        val verticalPaddingPx = (layout.verticalPaddingDp * density).roundToInt()
        views.setViewPadding(R.id.timeWidgetContent,
            horizontalPaddingPx, verticalPaddingPx,
            horizontalPaddingPx, verticalPaddingPx)
        views.setInt(R.id.timeWidgetTitle, "setMaxLines", layout.titleLines)
        views.setTextViewTextSize(R.id.timeWidgetTitle, TypedValue.COMPLEX_UNIT_SP, layout.titleSizeSp)
        views.setTextViewTextSize(R.id.timeWidgetDate, TypedValue.COMPLEX_UNIT_SP, layout.dateSizeSp)
        views.setViewVisibility(R.id.timeWidgetDate,
            if (layout.showDate) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.timeWidgetAccentLine,
            if (layout.showAccent) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.timeWidgetLiveDot,
            if (layout.showLiveIndicator) View.VISIBLE else View.GONE)
        // The actual colorful CA artwork is a separate ImageView, never color-filtered.
        // Explicit state changes remain correct when Android re-applies RemoteViews.
        views.setViewVisibility(R.id.timeWidgetBrandLogo, View.GONE)
        views.setViewVisibility(R.id.timeWidgetIcon, View.VISIBLE)
        val appearance = configuration?.appearance ?: WidgetAppearance.SYSTEM
        val background = configuration?.background ?: WidgetBackground.HORIZON
        val theme = WidgetThemeResolver.resolve(context, appearance, background)
        val key = TimeWidgetBackgroundKey(background, theme.dark, widthDp, heightDp)
        val bitmap = cache.getOrPut(key) {
            WidgetBackgroundRenderer.render(context.applicationContext, background, theme.dark, widthDp, heightDp,
                hideHorizonDisc = widthDp <= 320)
        }
        views.setImageViewBitmap(R.id.timeWidgetBackground, bitmap)
        views.setInt(R.id.timeWidgetIcon, "setColorFilter", theme.accentTextColor)
        views.setInt(R.id.timeWidgetAccentLine, "setColorFilter", theme.accentTextColor)
        views.setInt(R.id.timeWidgetLiveDot, "setColorFilter", theme.accentTextColor)
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
            views.setViewVisibility(R.id.timeWidgetCaption, View.VISIBLE)
            views.setViewVisibility(R.id.timeWidgetAccentLine, View.GONE)
            views.setViewVisibility(R.id.timeWidgetLiveDot, View.GONE)
            views.setViewVisibility(R.id.timeWidgetClock, View.GONE)
            views.setViewVisibility(R.id.timeWidgetFallback, View.GONE)
            views.setViewVisibility(R.id.timeWidgetDate, View.GONE)
            if (interactive) views.setOnClickPendingIntent(R.id.timeWidgetRoot,
                if (problem != null) openAppIntent(context, widgetId) else configureIntent(context, widgetId))
            return views
        }

        views.setViewVisibility(R.id.timeWidgetIcon, View.GONE)
        views.setViewVisibility(R.id.timeWidgetBrandLogo, View.VISIBLE)
        views.setTextViewText(R.id.timeWidgetTitle, EventTransport.preview(timed.title))
        // The ticking digits identify this as a live timer; omit the old oversized label.
        views.setViewVisibility(R.id.timeWidgetCaption, View.GONE)
        val targetText = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(displayContext.resources.configuration.locales[0])
            .format(count.target)
        val zoneSuffix = if (count.target.zone != snapshot.zone) " · " + count.target.zone.id else ""
        views.setTextViewText(R.id.timeWidgetDate, targetText + zoneSuffix)
        val clockNow = Instant.now()
        val plan = TimeWidgetClockPolicy.plan(
            clockNow, count.target.toInstant(), SystemClock.elapsedRealtime(), timed.countMode,
        )
        if (plan == null) {
            views.setViewVisibility(R.id.timeWidgetLiveDot, View.GONE)
            views.setViewVisibility(R.id.timeWidgetCaption, View.GONE)
            views.setViewVisibility(R.id.timeWidgetClock, View.GONE)
            views.setViewVisibility(R.id.timeWidgetFallback, View.VISIBLE)
            views.setTextViewText(R.id.timeWidgetFallback,
                displayContext.getString(R.string.time_widget_far_target))
        } else {
            views.setViewVisibility(R.id.timeWidgetClock, View.VISIBLE)
            views.setViewVisibility(R.id.timeWidgetFallback, View.GONE)
            views.setTextViewTextSize(R.id.timeWidgetClock, TypedValue.COMPLEX_UNIT_SP,
                liveClockTextSizeSp(displayContext, widthDp,
                    layout.horizontalPaddingDp, layout.clockMaxSizeSp,
                    clockNow, count.target.toInstant()))
            views.setChronometer(R.id.timeWidgetClock, plan.elapsedRealtimeBase, null, true)
            views.setChronometerCountDown(R.id.timeWidgetClock, plan.countDown)
        }
        if (interactive) views.setOnClickPendingIntent(R.id.timeWidgetRoot,
            editIntent(context, widgetId, timed.id))
        return views
    }

    /**
     * Chronometer changes its text in the launcher after the initial layout.
     * Fit a deliberately wider future sample once, instead of relying on
     * TextView auto-size measured against a shorter initial tick.
     */
    private fun liveClockTextSizeSp(
        context: Context, widthDp: Int, horizontalPaddingDp: Int, maximumSizeSp: Float,
        now: Instant, target: Instant,
    ): Float {
        val metrics = context.resources.displayMetrics
        val hoursUntilRefresh = kotlin.math.abs(Duration.between(now, target).seconds) / 3_600L + 48L
        val hourDigits = hoursUntilRefresh.toString().length.coerceAtLeast(2)
        val widestLabel = "-" + "8".repeat(hourDigits) + ":88:88"
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            fontFeatureSettings = "tnum"
            textSize = maximumSizeSp * metrics.scaledDensity
        }
        val availablePixels = (widthDp - 2 * horizontalPaddingDp - 4)
            .coerceAtLeast(1) * metrics.density
        val fit = availablePixels / paint.measureText(widestLabel).coerceAtLeast(1f)
        // API 26 glyph bounds at enlarged font settings can exceed Paint's estimate.
        // Reserve additional ink margin only for accessibility-scaled text.
        val safetyFactor = if (context.resources.configuration.fontScale >= 1.6f) 0.80f else 0.94f
        return (maximumSizeSp * fit * safetyFactor).coerceAtMost(maximumSizeSp)
    }

    private fun openAppIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, TimeWidgetConfigActivity::class.java)
        intent.setData(Uri.parse("countaway://time-widget/" + widgetId + "/data-error"))
        return PendingIntent.getActivity(context, 500_000 + widgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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
