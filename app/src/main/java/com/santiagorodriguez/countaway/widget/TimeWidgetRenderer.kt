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
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.ui.EditorActivity
import com.santiagorodriguez.countaway.ui.EventTimeText
import com.santiagorodriguez.countaway.ui.EventIconPresentation
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.text.NumberFormat
import kotlin.math.abs
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
        // A true one-cell-high layout keeps the title and native clock visible.
        // Larger host bounds retain the established full-height presentation.
        val format = configuration?.timeFormat ?: TimeWidgetFormat.LEGACY
        val compact = heightDp < 70
        // One-row/short Time widgets prioritize the actual event over app branding.
        // Roomier Time layouts preserve the established brand presentation.
        val compactIdentity = heightDp < 110
        val views = RemoteViews(context.packageName,
            if (compact) R.layout.widget_time_compact else R.layout.widget_time_live)
        val layout = TimeWidgetLayoutPlanner.plan(widthDp, heightDp,
            displayContext.resources.configuration.fontScale)
        // The pre-API-37 RemoteViews padding overload expects physical pixels.
        val density = displayContext.resources.displayMetrics.density
        val horizontalPaddingPx = (layout.horizontalPaddingDp * density).roundToInt()
        val verticalPaddingPx = (layout.verticalPaddingDp * density).roundToInt()
        // Keep total padding unchanged while lifting oversized text off the bottom edge.
        val liftPx = if (widthDp >= 200 && heightDp in 70..109 &&
            displayContext.resources.configuration.fontScale >= 1.6f
        ) (4 * density).roundToInt() else 0
        views.setViewPadding(R.id.timeWidgetContent,
            horizontalPaddingPx, verticalPaddingPx - liftPx,
            horizontalPaddingPx, verticalPaddingPx + liftPx)
        views.setInt(R.id.timeWidgetTitle, "setMaxLines", layout.titleLines)
        views.setTextViewTextSize(R.id.timeWidgetTitle, TypedValue.COMPLEX_UNIT_SP, layout.titleSizeSp)
        views.setTextViewTextSize(R.id.timeWidgetDate, TypedValue.COMPLEX_UNIT_SP, layout.dateSizeSp)
        if (!compact) views.setInt(R.id.timeWidgetDate, "setMaxLines",
            if (format == TimeWidgetFormat.LEGACY) 2 else 1)
        views.setViewVisibility(R.id.timeWidgetDate,
            if (layout.showDate) View.VISIBLE else View.GONE)
        if (!compact) {
            views.setViewVisibility(R.id.timeWidgetZone, View.GONE)
            views.setViewVisibility(R.id.timeWidgetSubday, View.GONE)
        }
        views.setViewVisibility(R.id.timeWidgetAccentLine,
            if (layout.showAccent) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.timeWidgetLiveDot,
            if (layout.showLiveIndicator) View.VISIBLE else View.GONE)
        // The actual colorful CA artwork is a separate ImageView, never color-filtered.
        // Explicit state changes remain correct when Android re-applies RemoteViews.
        views.setViewVisibility(R.id.timeWidgetBrandLogo, View.GONE)
        views.setViewVisibility(R.id.timeWidgetMark, View.GONE)
        views.setViewVisibility(R.id.timeWidgetIcon, View.VISIBLE)
        views.setViewVisibility(R.id.timeWidgetLongDays, View.GONE)
        views.setContentDescription(R.id.timeWidgetLongDays, null)
        views.setViewVisibility(R.id.timeWidgetDetailHint, View.GONE)
        views.setContentDescription(R.id.timeWidgetDate, null)
        val appearance = configuration?.appearance ?: WidgetAppearance.SYSTEM
        val background = configuration?.background ?: WidgetBackground.HORIZON
        val theme = WidgetThemeResolver.resolve(context, appearance, background)
        val key = TimeWidgetBackgroundKey(background, theme.dark, widthDp, heightDp)
        val bitmap = try {
            cache.getOrPut(key) {
                WidgetBackgroundRenderer.render(context.applicationContext, background, theme.dark, widthDp, heightDp,
                    hideHorizonDisc = widthDp <= 320)
            }
        } catch (failure: RuntimeException) {
            android.util.Log.w("CountAwayTimeWidget", "Background bitmap unavailable; using native drawable", failure)
            null
        }
        if (bitmap == null) {
            views.setImageViewResource(R.id.timeWidgetBackground, background.drawableRes(theme.dark))
        } else {
            views.setImageViewBitmap(R.id.timeWidgetBackground, bitmap)
        }
        views.setInt(R.id.timeWidgetIcon, "setColorFilter", theme.accentTextColor)
        views.setInt(R.id.timeWidgetAccentLine, "setColorFilter", theme.accentTextColor)
        views.setInt(R.id.timeWidgetLiveDot, "setColorFilter", theme.accentTextColor)
        views.setTextColor(R.id.timeWidgetTitle, theme.primaryTextColor)
        views.setTextColor(R.id.timeWidgetCaption, theme.secondaryTextColor)
        views.setTextColor(R.id.timeWidgetClock, theme.accentTextColor)
        views.setTextColor(R.id.timeWidgetLongDays, theme.accentTextColor)
        views.setTextColor(R.id.timeWidgetDetailHint, theme.secondaryTextColor)
        views.setTextColor(R.id.timeWidgetFallback, theme.accentTextColor)
        views.setTextColor(R.id.timeWidgetDate, theme.secondaryTextColor)
        if (!compact) {
            views.setTextColor(R.id.timeWidgetZone, theme.secondaryTextColor)
            views.setTextColor(R.id.timeWidgetSubday, theme.accentTextColor)
        }

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

        views.setImageViewResource(R.id.timeWidgetIcon,
            EventIconPresentation.drawableRes(timed.icon))
        views.setViewVisibility(R.id.timeWidgetIcon,
            if (compactIdentity) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.timeWidgetBrandLogo,
            if (compactIdentity) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.timeWidgetTitle, EventTransport.preview(timed.title))
        // The ticking digits identify this as a live timer; omit the old oversized label.
        views.setViewVisibility(R.id.timeWidgetCaption, View.GONE)
        val targetText = EventTimeText.dateTime(displayContext, count.target)
        val ambiguous = count.target.zone.rules
            .getValidOffsets(count.target.toLocalDateTime()).size > 1
        val zoneSuffix = if (count.target.zone != snapshot.zone || ambiguous) {
            " · " + count.target.zone.id + (if (ambiguous) " (${count.target.offset})" else "")
        } else ""
        val clockNow = Instant.now()
        val plan = TimeWidgetClockPolicy.plan(
            clockNow, count.target.toInstant(), SystemClock.elapsedRealtime(), timed.countMode,
        )
        val showDetailedZone = !compact && layout.showZone && layout.showDate &&
            (format != TimeWidgetFormat.LEGACY || plan != null)
        views.setTextViewText(R.id.timeWidgetDate,
            targetText + if (showDetailedZone) "" else zoneSuffix)
        if (showDetailedZone) {
            // Preserve the exact IANA zone identity from the original date
            // label; a city-only abbreviation can be ambiguous after travel.
            val zoneId = count.target.zone.id
            val offset = count.target.offset.id
            val offsetLabel = if (offset == "Z") "UTC" else "UTC$offset"
            val zoneLabel = if (zoneId == "UTC") offsetLabel
                else "$zoneId · $offsetLabel"
            views.setTextViewText(R.id.timeWidgetZone, zoneLabel)
            views.setViewVisibility(R.id.timeWidgetZone, View.VISIBLE)
        }
        if (format != TimeWidgetFormat.LEGACY) {
            renderFormat(views, displayContext, format, timed, count.target, snapshot.now,
                plan, layout, widthDp, heightDp, targetText + zoneSuffix, showDetailedZone)
        } else if (plan != null) {
            views.setViewVisibility(R.id.timeWidgetClock, View.VISIBLE)
            views.setViewVisibility(R.id.timeWidgetFallback, View.GONE)
            views.setTextViewTextSize(R.id.timeWidgetClock, TypedValue.COMPLEX_UNIT_SP,
                liveClockTextSizeSp(displayContext, widthDp,
                    layout.horizontalPaddingDp, layout.clockMaxSizeSp,
                    clockNow, count.target.toInstant()))
            views.setChronometer(R.id.timeWidgetClock, plan.elapsedRealtimeBase, null, true)
            views.setChronometerCountDown(R.id.timeWidgetClock, plan.countDown)
        } else {
            val longRange = TimeWidgetLongRangePolicy.plan(clockNow, count.target.toInstant(),
                SystemClock.elapsedRealtime(), timed.countMode)
            if (longRange == null) {
                views.setViewVisibility(R.id.timeWidgetLiveDot, View.GONE)
                views.setViewVisibility(R.id.timeWidgetClock, View.GONE)
                views.setViewVisibility(R.id.timeWidgetFallback, View.VISIBLE)
                views.setTextViewText(R.id.timeWidgetFallback,
                    displayContext.getString(R.string.time_widget_far_target))
            } else {
                val fontScale = displayContext.resources.configuration.fontScale
                val largeFont = fontScale >= 1.6f
                val showSubday = heightDp >= 115 && (!largeFont || heightDp >= 175)
                val showDate = heightDp >= 210 && (!largeFont || heightDp >= 255)
                val showAccent = heightDp >= 165 && (!largeFont || heightDp >= 235)
                val showHint = !showSubday && heightDp >= 100 && widthDp >= 200 &&
                    fontScale < 1.6f
                val signedNumber = (if (longRange.signedDays < 0L) "-" else "") +
                    NumberFormat.getIntegerInstance(displayContext.resources.configuration.locales[0])
                        .format(abs(longRange.signedDays))
                val dayLabel = displayContext.getString(R.string.time_widget_long_days, signedNumber)
                views.setTextViewText(R.id.timeWidgetLongDays, dayLabel)
                views.setTextViewTextSize(R.id.timeWidgetLongDays, TypedValue.COMPLEX_UNIT_SP,
                    fittedLongDaySizeSp(displayContext, widthDp, layout.horizontalPaddingDp,
                        dayLabel, when {
                            heightDp < 70 && largeFont -> 11f
                            heightDp < 70 -> 18f
                            heightDp < 110 && largeFont -> 16f
                            largeFont -> 20f
                            heightDp < 120 -> 27f
                            else -> 30f
                        }))
                views.setViewVisibility(R.id.timeWidgetLongDays, View.VISIBLE)
                views.setViewVisibility(R.id.timeWidgetAccentLine,
                    if (showAccent) View.VISIBLE else View.GONE)
                views.setViewVisibility(R.id.timeWidgetDate,
                    if (showDate) View.VISIBLE else View.GONE)
                if (!compact) views.setViewVisibility(R.id.timeWidgetZone, View.GONE)
                views.setViewVisibility(R.id.timeWidgetClock,
                    if (showSubday) View.VISIBLE else View.GONE)
                views.setViewVisibility(R.id.timeWidgetLiveDot,
                    if (showSubday && layout.showLiveIndicator) View.VISIBLE else View.GONE)
                views.setViewVisibility(R.id.timeWidgetFallback, View.GONE)
                views.setViewVisibility(R.id.timeWidgetDetailHint,
                    if (showHint) View.VISIBLE else View.GONE)
                if (showHint) {
                    views.setTextViewText(R.id.timeWidgetDetailHint,
                        displayContext.getString(R.string.time_widget_expand_details))
                }
                if (showSubday) {
                    // Native seconds stay within a single day at render.
                    // The existing daily refresh re-normalizes the day portion.
                    views.setTextViewTextSize(R.id.timeWidgetClock, TypedValue.COMPLEX_UNIT_SP,
                        liveClockTextSizeSp(displayContext, widthDp,
                            layout.horizontalPaddingDp,
                            if (largeFont) 16f else if (heightDp < 150) 18f else 23f,
                            clockNow, clockNow.plusSeconds(36L * 3_600L)))
                    views.setChronometer(R.id.timeWidgetClock,
                        longRange.subdayClock.elapsedRealtimeBase, null, true)
                    views.setChronometerCountDown(R.id.timeWidgetClock,
                        longRange.subdayClock.countDown)
                }
            }
        }
        if (interactive) views.setOnClickPendingIntent(R.id.timeWidgetRoot,
            editIntent(context, widgetId, timed.id))
        return views
    }

    private fun renderFormat(
        views: RemoteViews, context: Context, format: TimeWidgetFormat, event: CountdownEvent,
        target: ZonedDateTime, now: ZonedDateTime, clock: TimeWidgetClockPlan?,
        layout: TimeWidgetLayoutPlan, widthDp: Int, heightDp: Int,
        accessibleTarget: String, showZone: Boolean,
    ) {
        val scale = context.resources.configuration.fontScale
        val compact = heightDp < 70
        val large = scale >= 1.6f
        val padding = layout.horizontalPaddingDp
        val counter = if (format == TimeWidgetFormat.FULL_COUNTER) {
            TimeWidgetCalendarReading.between(now, target)
        } else null
        val targetInstant = target.toInstant()
        val instant = now.toInstant()
        val totalHours = Duration.between(instant, targetInstant).toHours()
        val hoursFallback = clock == null || (widthDp < 220 && abs(totalHours) >= 1000)
        val snapshot = format == TimeWidgetFormat.DAYS_HOURS
        val staticValue = snapshot || counter != null || hoursFallback
        val locale = context.resources.configuration.locales[0]
        val numbers = NumberFormat.getIntegerInstance(locale)
        val maxClock = when {
            !compact -> layout.clockMaxSizeSp
            large -> if (heightDp < 53) 8.5f else 10f
            scale >= 1.3f -> 15f
            else -> 20f
        }
        val dateLabel = if (event.countMode == CountMode.COUNT_UP) R.string.widget_event_from
            else R.string.widget_event_target
        val shortDate = target.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale))
        val date = if (layout.showDate) EventTimeText.dateTime(context, target) else shortDate
        val visibleDate = context.getString(dateLabel, date)
        val accessibleDate = context.getString(dateLabel, accessibleTarget)
        val dateDescription = if (staticValue) {
            val stamp = context.getString(R.string.widget_snapshot_stamp, EventTimeText.dateTime(context, now))
            "$accessibleDate. $stamp"
        } else accessibleDate
        val metrics = context.resources.displayMetrics
        fun pixels(dp: Int) = (dp * metrics.density).roundToInt()
        val widthPx = pixels((widthDp - 2 * padding - 3).coerceAtLeast(1))
        val titleSp = if (compact && large) 7f else layout.titleSizeSp
        val titleLines = if (compact || heightDp < 160 || large) 1 else layout.titleLines.coerceAtMost(2)
        val verticalPaddingDp = if (compact) 1 else layout.verticalPaddingDp
        val showAccent = layout.showAccent && heightDp >= 180 && !large
        val secondaryFace = Typeface.create("sans-serif", Typeface.NORMAL)
        val mainFace = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val dateSp = fitTextSp(visibleDate, secondaryFace, metrics, widthPx, Int.MAX_VALUE,
            if (compact) (if (large) 6.5f else 8f) else layout.dateSizeSp)
        // Give the event date its own space before scaling the primary reading.
        val titleHeight = textLineHeight(Typeface.create("sans-serif-medium", Typeface.NORMAL), titleSp, metrics)
        val dateHeight = textLineHeight(secondaryFace, dateSp, metrics)
        val reservedHeight = maxOf(pixels(if (compact) 16 else 20), titleHeight * titleLines) +
            dateHeight + pixels(2 * verticalPaddingDp + if (compact) 4 else 16) +
            (if (showAccent) pixels(12) else 0) +
            (if (showZone) textLineHeight(secondaryFace, 10f, metrics) + pixels(2) else 0)
        // The host can round line metrics up independently, including fallback glyphs.
        val valueHeight = (pixels(heightDp) - reservedHeight - pixels(2)).coerceAtLeast(1)
        val separateClock = counter != null && (counter.hours > 0 || counter.minutes > 0) && heightDp >= (if (large) 175 else 120) &&
            valueHeight >= pixels(35) && (counter.years > 0 || counter.months > 0 || counter.days > 0)

        views.setViewVisibility(R.id.timeWidgetBrandLogo, View.GONE)
        views.setViewVisibility(R.id.timeWidgetIcon, View.VISIBLE)
        val showMark = widthDp >= 144 && !large && (!compact || EventTransport.preview(event.title).length <= 16)
        views.setViewVisibility(R.id.timeWidgetMark, if (showMark) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.timeWidgetLiveDot, View.GONE)
        views.setViewVisibility(R.id.timeWidgetFallback, View.GONE)
        views.setViewVisibility(R.id.timeWidgetAccentLine, if (showAccent) View.VISIBLE else View.GONE)
        val horizontal = pixels(padding)
        val vertical = pixels(verticalPaddingDp)
        views.setViewPadding(R.id.timeWidgetContent, horizontal, vertical, horizontal, vertical)
        views.setTextViewTextSize(R.id.timeWidgetTitle, TypedValue.COMPLEX_UNIT_SP, titleSp)
        views.setInt(R.id.timeWidgetTitle, "setMaxLines", titleLines)
        views.setTextViewText(R.id.timeWidgetDate, visibleDate)
        views.setTextViewTextSize(R.id.timeWidgetDate, TypedValue.COMPLEX_UNIT_SP, dateSp)
        views.setContentDescription(R.id.timeWidgetDate, dateDescription)
        views.setViewVisibility(R.id.timeWidgetDate, View.VISIBLE)
        views.setViewVisibility(R.id.timeWidgetClock, if (staticValue) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.timeWidgetLongDays, if (staticValue) View.VISIBLE else View.GONE)
        if (counter != null) {
            val hasCalendarUnits = counter.years > 0 || counter.months > 0 || counter.days > 0
            val clockLabel = when {
                counter.hours > 0 -> context.getString(R.string.widget_counter_subday,
                    numbers.format(counter.hours), numbers.format(counter.minutes))
                counter.minutes > 0 -> context.getString(R.string.widget_counter_minutes,
                    numbers.format(counter.minutes))
                instant == targetInstant -> context.getString(R.string.widget_counter_minutes, numbers.format(0))
                !hasCalendarUnits ->
                    context.getString(R.string.widget_counter_under_minute)
                else -> ""
            }
            val calendarLabel = buildList {
                if (counter.years > 0) add(context.getString(R.string.widget_counter_years, numbers.format(counter.years)))
                if (counter.months > 0) add(context.getString(R.string.widget_counter_months, numbers.format(counter.months)))
                if (counter.days > 0) add(context.getString(R.string.widget_counter_days, numbers.format(counter.days)))
            }.joinToString(" ")
            val direction = when {
                instant == targetInstant -> ""
                counter.remaining -> "↓ "
                else -> "↑ "
            }
            val underMinute = !hasCalendarUnits && counter.hours == 0L && counter.minutes == 0L
            val approximate = if (underMinute) "" else "≈ "
            val value = direction + approximate + when {
                separateClock -> calendarLabel
                calendarLabel.isNotEmpty() -> listOf(calendarLabel, clockLabel)
                    .filter(String::isNotEmpty).joinToString(" · ")
                else -> clockLabel
            }
            val spokenValue = if (instant == targetInstant) clockLabel else context.getString(
                if (counter.remaining) R.string.widget_snapshot_remaining else R.string.widget_snapshot_elapsed,
                listOf(calendarLabel, clockLabel).filter(String::isNotEmpty).joinToString(", "),
            )
            val subdayHeight = if (separateClock) valueHeight / 3 else 0
            views.setTextViewText(R.id.timeWidgetLongDays, value)
            views.setContentDescription(R.id.timeWidgetLongDays, spokenValue)
            views.setTextViewTextSize(R.id.timeWidgetLongDays, TypedValue.COMPLEX_UNIT_SP,
                fitTextSp(value, mainFace, metrics, widthPx, valueHeight - subdayHeight, maxClock))
            if (separateClock) {
                views.setViewVisibility(R.id.timeWidgetSubday, View.VISIBLE)
                views.setTextViewText(R.id.timeWidgetSubday, clockLabel)
                views.setTextViewTextSize(R.id.timeWidgetSubday, TypedValue.COMPLEX_UNIT_SP,
                    fitTextSp(clockLabel, mainFace, metrics, widthPx, subdayHeight, maxClock * 0.64f))
            }
        } else if (staticValue) {
            val value = if (snapshot) {
                val reading = TimeWidgetSnapshotPolicy.calculate(instant, targetInstant)
                when {
                    reading.underHour -> context.getString(R.string.widget_snapshot_under_hour)
                    reading.days > 0 -> context.getString(R.string.widget_snapshot_days_hours,
                        numbers.format(reading.days), numbers.format(reading.hours))
                    reading.hours == 0L -> context.getString(R.string.widget_hours_value, numbers.format(0))
                    else -> context.getString(R.string.widget_snapshot_hours, numbers.format(reading.hours))
                }
            } else {
                val signedHours = if (event.countMode == CountMode.COUNT_DOWN) totalHours else -totalHours
                context.getString(R.string.widget_snapshot_hours, numbers.format(signedHours))
            }
            val label = value
            views.setTextViewText(R.id.timeWidgetLongDays, label)
            val spokenValue = if (snapshot && totalHours == 0L && instant == targetInstant) label
                else if (snapshot) context.getString(
                    if (targetInstant.isAfter(instant)) R.string.widget_snapshot_remaining
                    else R.string.widget_snapshot_elapsed, label)
                else label
            views.setContentDescription(R.id.timeWidgetLongDays, spokenValue)
            views.setTextViewTextSize(R.id.timeWidgetLongDays, TypedValue.COMPLEX_UNIT_SP,
                fitTextSp(label, mainFace, metrics, widthPx, valueHeight, maxClock))
        } else if (clock != null) {
            views.setTextViewTextSize(R.id.timeWidgetClock, TypedValue.COMPLEX_UNIT_SP,
                fitTextSp(widestClockLabel(instant, targetInstant), mainFace,
                    metrics, widthPx, valueHeight, maxClock))
            views.setChronometer(R.id.timeWidgetClock, clock.elapsedRealtimeBase, null, true)
            views.setChronometerCountDown(R.id.timeWidgetClock, clock.countDown)
        }
    }

    private fun fitTextSp(
        text: String, face: Typeface, metrics: android.util.DisplayMetrics,
        widthPx: Int, heightPx: Int, maxSp: Float,
    ): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            fontFeatureSettings = "tnum"
        }
        // Measure at each SP size so Android's non-linear font scaling remains in effect.
        var low = 0f
        var high = maxSp
        repeat(10) {
            val candidate = (low + high) / 2f
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, candidate, metrics)
            val font = paint.fontMetricsInt
            if (paint.measureText(text) <= widthPx && font.descent - font.ascent <= heightPx) {
                low = candidate
            } else {
                high = candidate
            }
        }
        return low
    }

    private fun textLineHeight(face: Typeface, sp: Float, metrics: android.util.DisplayMetrics): Int {
        val font = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics)
        }.fontMetricsInt
        return font.descent - font.ascent
    }

    private fun widestClockLabel(now: Instant, target: Instant): String {
        val hoursUntilRefresh = abs(Duration.between(now, target).seconds) / 3_600L + 48L
        return "-" + "8".repeat(hoursUntilRefresh.toString().length.coerceAtLeast(2)) + ":88:88"
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
        val widestLabel = widestClockLabel(now, target)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            fontFeatureSettings = "tnum"
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, maximumSizeSp, metrics)
        }
        val availablePixels = (widthDp - 2 * horizontalPaddingDp - 4)
            .coerceAtLeast(1) * metrics.density
        val fit = availablePixels / paint.measureText(widestLabel).coerceAtLeast(1f)
        // API 26 glyph bounds at enlarged font settings can exceed Paint's estimate.
        // Reserve additional ink margin only for accessibility-scaled text.
        val safetyFactor = if (context.resources.configuration.fontScale >= 1.6f) 0.80f else 0.94f
        return (maximumSizeSp * fit * safetyFactor).coerceAtMost(maximumSizeSp)
    }

    private fun fittedLongDaySizeSp(
        context: Context, widthDp: Int, paddingDp: Int, text: String, maxSp: Float,
    ): Float {
        val metrics = context.resources.displayMetrics
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            fontFeatureSettings = "tnum"
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, maxSp, metrics)
        }
        val available = (widthDp - 2 * paddingDp - 4).coerceAtLeast(1) * metrics.density
        return (maxSp * available / paint.measureText(text).coerceAtLeast(1f) * 0.94f)
            .coerceAtMost(maxSp)
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
