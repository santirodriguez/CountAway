package com.santiagorodriguez.countaway.widget

import android.annotation.TargetApi
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.countdown.CountdownTimeSnapshot
import com.santiagorodriguez.countaway.countdown.TimedEventResolver
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.RepeatRule
import com.santiagorodriguez.countaway.ui.LanguageManager
import java.time.Instant
import kotlin.math.roundToInt

/** Separate opt-in provider. The existing classic 1x1 component and metadata are unchanged. */
class TimeWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        WidgetUpdateScheduler.ensureScheduled(context.applicationContext)
        super.onEnabled(context)
    }

    override fun onDisabled(context: Context) {
        TimeWidgetBoundaryScheduler.cancel(context.applicationContext)
        WidgetUpdateScheduler.ensureScheduled(context.applicationContext)
        super.onDisabled(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        val appContext = context.applicationContext
        val ids = appWidgetIds.clone()
        CountdownIo.execute {
            try {
                updateWidgets(appContext, manager, ids, CountdownTime.snapshot())
                WidgetUpdateScheduler.ensureScheduled(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val appContext = context.applicationContext
        val preferences = WidgetPreferences(appContext)
        appWidgetIds.forEach(preferences::remove)
        CountdownIo.execute {
            updateAllWidgets(appContext)
            WidgetUpdateScheduler.ensureScheduled(appContext)
        }
        super.onDeleted(context, appWidgetIds)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val appContext = context.applicationContext
        val preferences = WidgetPreferences(appContext)
        val manager = AppWidgetManager.getInstance(appContext)
        val count = minOf(oldWidgetIds.size, newWidgetIds.size)
        for (index in 0 until count) preferences.remap(oldWidgetIds[index], newWidgetIds[index])
        for (index in count until newWidgetIds.size) preferences.remove(newWidgetIds[index])
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            newWidgetIds.forEach { id ->
                runCatching {
                    manager.updateAppWidgetOptions(id, Bundle().apply {
                        putBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED, true)
                    })
                }
            }
        }
        CountdownIo.execute {
            updateAllWidgets(appContext)
            WidgetUpdateScheduler.ensureScheduled(appContext)
        }
        super.onRestored(context, oldWidgetIds, newWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager,
        appWidgetId: Int, newOptions: Bundle) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CountdownIo.execute {
            try { updateWidgets(appContext, manager, intArrayOf(appWidgetId), CountdownTime.snapshot()) }
            finally { pending.finish() }
        }
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
    }

    companion object {
        fun updateAllWidgets(context: Context, snapshot: CountdownTimeSnapshot = CountdownTime.snapshot()) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, TimeWidgetProvider::class.java)
            updateWidgets(appContext, manager, manager.getAppWidgetIds(component), snapshot)
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int,
            snapshot: CountdownTimeSnapshot = CountdownTime.snapshot()) =
            updateWidgets(context.applicationContext, manager, intArrayOf(widgetId), snapshot)

        private fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray,
            snapshot: CountdownTimeSnapshot) {
            val allIds = manager.getAppWidgetIds(ComponentName(context, TimeWidgetProvider::class.java))
            if (allIds.isEmpty()) {
                TimeWidgetBoundaryScheduler.cancel(context)
                return
            }
            val result = CountdownRepository(context).loadResult()
            val displayContext = LanguageManager.localizedContext(context)
            val preferences = WidgetPreferences(context)
            val eventsById = (result as? CountdownLoadResult.Success)?.events
                ?.associateBy(CountdownEvent::id).orEmpty()
            val problem = (result as? CountdownLoadResult.Failure)?.problem
            val backgroundCache = HashMap<TimeWidgetBackgroundKey, Bitmap>()
            WidgetBatchUpdate.forEach(
                ids = ids,
                update = { id ->
                    val configuration = preferences.get(id)
                    val event = configuration?.takeIf { it.eventSelection == WidgetEventSelection.FIXED }
                        ?.eventId?.let(eventsById::get)
                    val options = manager.getAppWidgetOptions(id)
                    val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        responsive(context, displayContext, id, configuration, event, problem, snapshot,
                            options, backgroundCache)
                    } else null
                    val fallback = views ?: TimeWidgetRenderer.render(
                        context, displayContext, id, configuration, event, problem, snapshot,
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 150).coerceAtLeast(1),
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 115).coerceAtLeast(1),
                        backgroundCache,
                    )
                    manager.updateAppWidget(id, fallback)
                },
                onFailure = { id, error ->
                    android.util.Log.e("CountAwayTimeWidget", "Failed to update widget " + id, error)
                },
            )
            val repeatingTargets = allIds.asList().mapNotNull { id ->
                val config = preferences.get(id)?.takeIf { it.eventSelection == WidgetEventSelection.FIXED }
                val event = config?.eventId?.let(eventsById::get)
                    ?.takeIf { it.eventTime != null && it.eventZone != null && it.repeatRule != RepeatRule.NONE }
                event?.let { TimedEventResolver.resolve(it, snapshot.now)?.target?.toInstant() }
            }
            TimeWidgetBoundaryScheduler.schedule(context, repeatingTargets, snapshot.now.toInstant())
        }

        @TargetApi(Build.VERSION_CODES.S)
        @Suppress("DEPRECATION")
        private fun responsive(context: Context, displayContext: Context, id: Int,
            configuration: WidgetConfiguration?, event: CountdownEvent?,
            problem: com.santiagorodriguez.countaway.data.CountdownDataProblem?,
            snapshot: CountdownTimeSnapshot, options: Bundle,
            backgroundCache: MutableMap<TimeWidgetBackgroundKey, Bitmap>): RemoteViews? {
            val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                .orEmpty().distinct()
            if (sizes.isEmpty()) return null
            val mapping = LinkedHashMap<SizeF, RemoteViews>()
            for (size in sizes) {
                mapping[size] = TimeWidgetRenderer.render(
                    context, displayContext, id, configuration, event, problem, snapshot,
                    size.width.roundToInt().coerceAtLeast(1),
                    size.height.roundToInt().coerceAtLeast(1), backgroundCache,
                )
            }
            return RemoteViews(mapping)
        }
    }
}

/** One inexact occurrence-boundary wakeup, never a one-second app update loop. */
internal object TimeWidgetBoundaryScheduler {
    const val ACTION_TARGET_BOUNDARY = "com.santiagorodriguez.countaway.widget.TIME_TARGET_BOUNDARY"
    private const val WINDOW_MS = 15L * 60L * 1000L

    fun schedule(context: Context, targets: List<Instant>, now: Instant) {
        val next = TimeWidgetBoundaryPlanner.nextUpcoming(targets, now) ?: run {
            cancel(context)
            return
        }
        val trigger = runCatching { next.toEpochMilli() }.getOrNull() ?: return
        val manager = context.getSystemService(AlarmManager::class.java)
        runCatching {
            manager.setWindow(AlarmManager.RTC, trigger, WINDOW_MS, pendingIntent(context))
        }.onFailure { android.util.Log.w("CountAwayTimeWidget", "Timed boundary refresh unavailable", it) }
    }

    fun cancel(context: Context) {
        runCatching {
            context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 1, Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION_TARGET_BOUNDARY),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
