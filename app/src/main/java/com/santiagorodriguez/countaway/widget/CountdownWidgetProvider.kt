package com.santiagorodriguez.countaway.widget

import android.annotation.TargetApi
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.ArrivalMood
import com.santiagorodriguez.countaway.countdown.CountdownStatus
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.countdown.CountdownTimeSnapshot
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.ui.EditorActivity
import com.santiagorodriguez.countaway.ui.LanguageManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

class CountdownWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateScheduler.ensureScheduled(context.applicationContext, CountdownTime.snapshot())
    }

    override fun onDisabled(context: Context) {
        WidgetUpdateScheduler.cancel(context.applicationContext)
        super.onDisabled(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        val appContext = context.applicationContext
        val ids = appWidgetIds.clone()
        val snapshot = CountdownTime.snapshot()
        CountdownIo.execute {
            try {
                updateWidgets(appContext, manager, ids, snapshot)
                WidgetUpdateScheduler.ensureScheduled(appContext, snapshot)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val appContext = context.applicationContext
        val preferences = WidgetPreferences(appContext)
        appWidgetIds.forEach(preferences::remove)
        WidgetUpdateScheduler.ensureScheduled(appContext, CountdownTime.snapshot())
        super.onDeleted(context, appWidgetIds)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val appContext = context.applicationContext
        val preferences = WidgetPreferences(appContext)
        val manager = AppWidgetManager.getInstance(appContext)
        val pairCount = minOf(oldWidgetIds.size, newWidgetIds.size)

        for (index in 0 until pairCount) {
            preferences.remap(oldWidgetIds[index], newWidgetIds[index])
        }
        for (index in pairCount until newWidgetIds.size) {
            preferences.remove(newWidgetIds[index])
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            newWidgetIds.forEach { appWidgetId ->
                runCatching {
                    manager.updateAppWidgetOptions(
                        appWidgetId,
                        Bundle().apply {
                            putBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED, true)
                        },
                    )
                }
            }
        }

        WidgetUpdateScheduler.ensureScheduled(appContext, CountdownTime.snapshot())
        super.onRestored(context, oldWidgetIds, newWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        val pending = goAsync()
        val appContext = context.applicationContext
        val snapshot = CountdownTime.snapshot()
        CountdownIo.execute {
            try {
                updateWidget(appContext, manager, appWidgetId, snapshot)
            } finally {
                pending.finish()
            }
        }
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
    }

    companion object {
        fun updateAllWidgets(
            context: Context,
            snapshot: CountdownTimeSnapshot = CountdownTime.snapshot(),
        ) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, CountdownWidgetProvider::class.java)
            updateWidgets(appContext, manager, manager.getAppWidgetIds(component), snapshot)
        }

        fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
            snapshot: CountdownTimeSnapshot = CountdownTime.snapshot(),
        ) {
            updateWidgets(context.applicationContext, manager, intArrayOf(appWidgetId), snapshot)
        }

        private fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            appWidgetIds: IntArray,
            snapshot: CountdownTimeSnapshot,
        ) {
            if (appWidgetIds.isEmpty()) return

            val displayContext = LanguageManager.localizedContext(context)
            val preferences = WidgetPreferences(context)
            val renderData = WidgetRenderData.from(
                CountdownRepository(context).loadResult(),
                snapshot.today,
            )
            val backgroundCache = HashMap<BackgroundKey, Bitmap>()

            appWidgetIds.forEach { appWidgetId ->
                val views = buildRemoteViews(
                    context = context,
                    displayContext = displayContext,
                    manager = manager,
                    appWidgetId = appWidgetId,
                    today = snapshot.today,
                    configuration = preferences.get(appWidgetId),
                    renderData = renderData,
                    backgroundCache = backgroundCache,
                )
                manager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun buildRemoteViews(
            context: Context,
            displayContext: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
            today: LocalDate,
            configuration: WidgetConfiguration?,
            renderData: WidgetRenderData,
            backgroundCache: MutableMap<BackgroundKey, Bitmap>,
        ): RemoteViews {
            val options = manager.getAppWidgetOptions(appWidgetId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val responsive = responsiveViews(
                    context,
                    displayContext,
                    appWidgetId,
                    today,
                    configuration,
                    renderData,
                    options,
                    backgroundCache,
                )
                if (responsive != null) return responsive
            }

            val widthDp = options
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_MIN_WIDTH_DP)
                .takeIf { it > 0 } ?: DEFAULT_MIN_WIDTH_DP
            val heightDp = options
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_MIN_HEIGHT_DP)
                .takeIf { it > 0 } ?: DEFAULT_MIN_HEIGHT_DP
            return renderForSize(
                context,
                displayContext,
                appWidgetId,
                today,
                configuration,
                renderData,
                widthDp,
                heightDp,
                backgroundCache,
            )
        }

        @TargetApi(Build.VERSION_CODES.S)
        @Suppress("DEPRECATION")
        private fun responsiveViews(
            context: Context,
            displayContext: Context,
            appWidgetId: Int,
            today: LocalDate,
            configuration: WidgetConfiguration?,
            renderData: WidgetRenderData,
            options: Bundle,
            backgroundCache: MutableMap<BackgroundKey, Bitmap>,
        ): RemoteViews? {
            val sizes = options
                .getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                .orEmpty()
                .distinct()
            if (sizes.isEmpty()) return null

            val mapping = LinkedHashMap<SizeF, RemoteViews>(sizes.size)
            sizes.forEach { size ->
                mapping[size] = renderForSize(
                    context,
                    displayContext,
                    appWidgetId,
                    today,
                    configuration,
                    renderData,
                    size.width.roundToInt().coerceAtLeast(MIN_RENDER_DIMENSION_DP),
                    size.height.roundToInt().coerceAtLeast(MIN_RENDER_DIMENSION_DP),
                    backgroundCache,
                )
            }
            return RemoteViews(mapping)
        }

        private fun renderForSize(
            context: Context,
            displayContext: Context,
            appWidgetId: Int,
            today: LocalDate,
            configuration: WidgetConfiguration?,
            renderData: WidgetRenderData,
            widthDp: Int,
            heightDp: Int,
            backgroundCache: MutableMap<BackgroundKey, Bitmap>,
        ): RemoteViews {
            val size = WidgetSize.fromDimensions(widthDp, heightDp)
            val layoutId = when (size) {
                WidgetSize.COMPACT -> R.layout.widget_countdown_compact
                WidgetSize.SHORT -> R.layout.widget_countdown_short
                WidgetSize.STANDARD -> R.layout.widget_countdown_standard
                WidgetSize.LARGE -> R.layout.widget_countdown_large
            }
            val views = RemoteViews(context.packageName, layoutId)
            val appearance = configuration?.appearance ?: WidgetAppearance.SYSTEM
            val theme = WidgetThemeResolver.resolve(context, appearance)
            val background = configuration?.background ?: WidgetBackground.CLASSIC

            applyTheme(
                context,
                views,
                theme,
                background,
                widthDp,
                heightDp,
                backgroundCache,
            )

            when (renderData) {
                is WidgetRenderData.Failure -> renderDataError(
                    displayContext,
                    views,
                    appWidgetId,
                    renderData.problem,
                )
                is WidgetRenderData.Ready -> {
                    val event = renderData.resolve(configuration)
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
            return views
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
            val formattedDate = content.date.format(dateFormatter)

            views.setImageViewResource(R.id.widgetIcon, content.iconRes)
            views.setTextViewText(R.id.widgetTitle, content.title)
            views.setTextViewText(R.id.widgetCount, content.countTextFor(size))
            views.setTextViewText(R.id.widgetUnit, content.unitRes?.let(context::getString).orEmpty())
            views.setTextViewText(R.id.widgetDate, formattedDate)
            views.setTextViewText(R.id.widgetMilestone, mood ?: "")
            views.setViewVisibility(R.id.widgetMilestone, if (mood == null) View.GONE else View.VISIBLE)
            views.setContentDescription(
                R.id.widgetRoot,
                listOf(
                    content.title,
                    statusDescription(context, content),
                    formattedDate,
                    context.getString(R.string.widget_open_countdown),
                ).joinToString(", "),
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, editPendingIntent(context, appWidgetId, event.id))
        }

        private fun statusDescription(context: Context, content: WidgetEventContent): String {
            val count = content.countText.toLongOrNull()?.coerceAtMost(Int.MAX_VALUE.toLong()) ?: 0L
            val quantity = count.toInt()
            return when (content.status) {
                CountdownStatus.FUTURE,
                CountdownStatus.THREE_DAYS,
                CountdownStatus.TWO_DAYS,
                -> context.getString(R.string.status_days, count)
                CountdownStatus.TOMORROW -> context.getString(R.string.status_tomorrow)
                CountdownStatus.TODAY -> context.getString(R.string.status_today)
                CountdownStatus.DONE ->
                    context.resources.getQuantityString(R.plurals.status_days_ago, quantity, count)
            }
        }

        private fun renderUnconfigured(
            context: Context,
            views: RemoteViews,
            appWidgetId: Int,
            noUpcoming: Boolean,
        ) {
            val title = context.getString(
                if (noUpcoming) R.string.widget_no_upcoming else R.string.widget_select_countdown,
            )
            val action = context.getString(R.string.widget_tap_to_configure)
            views.setImageViewResource(R.id.widgetIcon, R.drawable.ic_event_calendar)
            views.setTextViewText(R.id.widgetTitle, title)
            views.setTextViewText(R.id.widgetCount, "—")
            views.setTextViewText(R.id.widgetUnit, action)
            views.setTextViewText(R.id.widgetDate, "")
            views.setTextViewText(R.id.widgetMilestone, "")
            views.setViewVisibility(R.id.widgetMilestone, View.GONE)
            views.setContentDescription(R.id.widgetRoot, "$title, $action")
            views.setOnClickPendingIntent(R.id.widgetRoot, configurePendingIntent(context, appWidgetId))
        }

        private fun renderDataError(
            context: Context,
            views: RemoteViews,
            appWidgetId: Int,
            problem: com.santiagorodriguez.countaway.data.CountdownDataProblem,
        ) {
            val title = context.getString(
                if (problem == com.santiagorodriguez.countaway.data.CountdownDataProblem.UNSUPPORTED_SCHEMA) {
                    R.string.widget_data_newer_version
                } else {
                    R.string.widget_data_error
                },
            )
            val action = context.getString(R.string.widget_open_app)
            views.setImageViewResource(R.id.widgetIcon, R.drawable.ic_event_calendar)
            views.setTextViewText(R.id.widgetTitle, title)
            views.setTextViewText(R.id.widgetCount, "!")
            views.setTextViewText(R.id.widgetUnit, action)
            views.setTextViewText(R.id.widgetDate, "")
            views.setTextViewText(R.id.widgetMilestone, "")
            views.setViewVisibility(R.id.widgetMilestone, View.GONE)
            views.setContentDescription(R.id.widgetRoot, "$title, $action")
            views.setOnClickPendingIntent(R.id.widgetRoot, openAppPendingIntent(context, appWidgetId))
        }

        private fun applyTheme(
            context: Context,
            views: RemoteViews,
            theme: WidgetTheme,
            background: WidgetBackground,
            widthDp: Int,
            heightDp: Int,
            backgroundCache: MutableMap<BackgroundKey, Bitmap>,
        ) {
            val key = BackgroundKey(background, theme.dark, widthDp, heightDp)
            val bitmap = backgroundCache.getOrPut(key) {
                WidgetBackgroundRenderer.render(
                    context.applicationContext,
                    background,
                    theme.dark,
                    widthDp,
                    heightDp,
                )
            }
            views.setImageViewBitmap(R.id.widgetBackground, bitmap)
            views.setInt(R.id.widgetIcon, "setColorFilter", theme.accentTextColor)
            views.setTextColor(R.id.widgetTitle, theme.primaryTextColor)
            views.setTextColor(R.id.widgetCount, theme.accentTextColor)
            views.setTextColor(R.id.widgetMilestone, theme.secondaryTextColor)
            views.setTextColor(R.id.widgetUnit, theme.secondaryTextColor)
            views.setTextColor(R.id.widgetDate, theme.secondaryTextColor)
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

        private const val DEFAULT_MIN_WIDTH_DP = 56
        private const val DEFAULT_MIN_HEIGHT_DP = 50
        private const val MIN_RENDER_DIMENSION_DP = 1
        private const val CONFIG_REQUEST_CODE_OFFSET = 100_000
        private const val DATA_ERROR_REQUEST_CODE_OFFSET = 200_000
    }

    private data class BackgroundKey(
        val background: WidgetBackground,
        val dark: Boolean,
        val widthDp: Int,
        val heightDp: Int,
    )
}
