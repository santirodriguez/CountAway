package com.santiagorodriguez.countaway.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.data.EventTransport

internal enum class WidgetPinFinalizeResult {
    APPLIED,
    ALREADY_MATCHING,
    CONFLICT,
    EVENT_MISSING,
    INVALID_WIDGET_ID,
}

internal object WidgetPinFinalizer {
    @Synchronized
    fun finalize(
        context: Context,
        appWidgetId: Int,
        snapshot: WidgetPinRequestSnapshot,
    ): WidgetPinFinalizeResult {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return WidgetPinFinalizeResult.INVALID_WIDGET_ID
        }

        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val provider = snapshot.family.provider(appContext)
        val actual = runCatching { manager.getAppWidgetInfo(appWidgetId)?.provider }.getOrNull()
        if (actual != null && actual != provider) return WidgetPinFinalizeResult.CONFLICT

        val resolved = run {
            val events = (CountdownRepository(appContext).loadResult() as? CountdownLoadResult.Success)
                ?.events ?: return WidgetPinFinalizeResult.EVENT_MISSING
            val event = EventTransport.resolve(events, snapshot.eventId)
                ?: return WidgetPinFinalizeResult.EVENT_MISSING
            if (!snapshot.family.accepts(event)) return WidgetPinFinalizeResult.EVENT_MISSING
            snapshot.copy(eventId = event.id)
        }
        val preferences = WidgetPreferences(appContext)
        val existing = preferences.get(appWidgetId)
        if (existing != null) {
            return if (WidgetPinResultPolicy.matchesSnapshot(existing, resolved)) {
                WidgetDefaultsPreferences(appContext).save(snapshot.style)
                WidgetPinFinalizeResult.ALREADY_MATCHING
            } else {
                WidgetPinFinalizeResult.CONFLICT
            }
        }

        preferences.save(
            appWidgetId = appWidgetId,
            eventId = resolved.eventId,
            appearance = snapshot.style.appearance,
            background = snapshot.style.background,
            eventSelection = WidgetEventSelection.FIXED,
            onlyHours = snapshot.onlyHours,
            dayClock = snapshot.family == WidgetFamily.TIME && !snapshot.onlyHours,
        )
        WidgetDefaultsPreferences(appContext).save(snapshot.style)

        if (WidgetInstanceValidator.isOwnedBy(manager, appWidgetId, provider)) {
            val timeSnapshot = CountdownTime.snapshot()
            when (snapshot.family) {
                WidgetFamily.DAYS -> CountdownWidgetProvider.updateWidget(appContext, manager, appWidgetId, timeSnapshot)
                WidgetFamily.TIME -> TimeWidgetProvider.updateWidget(appContext, manager, appWidgetId, timeSnapshot)
            }
            WidgetUpdateScheduler.ensureScheduled(appContext, timeSnapshot)
        } else {
            WidgetUpdateScheduler.ensureScheduled(appContext, CountdownTime.snapshot())
        }

        return WidgetPinFinalizeResult.APPLIED
    }
}
