package com.santiagorodriguez.countaway.widget

import android.content.Context

data class WidgetConfiguration(
    val eventId: String?,
    val appearance: WidgetAppearance,
    val background: WidgetBackground = WidgetBackground.CLASSIC,
    val eventSelection: WidgetEventSelection = WidgetEventSelection.FIXED,
    val timeFormat: TimeWidgetFormat = TimeWidgetFormat.LEGACY,
)

class WidgetPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun get(appWidgetId: Int): WidgetConfiguration? {
        val eventSelection = WidgetEventSelection.fromStorageKey(
            preferences.getString(selectionKey(appWidgetId), null),
        )
        val eventId = preferences.getString(eventKey(appWidgetId), null)
        if (eventSelection == WidgetEventSelection.FIXED && eventId == null) return null

        return WidgetConfiguration(
            eventId = eventId,
            appearance = WidgetAppearance.fromStorageKey(preferences.getString(appearanceKey(appWidgetId), null)),
            background = WidgetBackground.fromStorageKey(preferences.getString(backgroundKey(appWidgetId), null)),
            eventSelection = eventSelection,
            timeFormat = TimeWidgetFormat.fromKey(preferences.getString(formatKey(appWidgetId), null))
                ?: when {
                    preferences.getBoolean(hoursKey(appWidgetId), false) -> TimeWidgetFormat.HOURS
                    preferences.getBoolean(dayKey(appWidgetId), false) -> TimeWidgetFormat.DAYS_HOURS
                    else -> TimeWidgetFormat.LEGACY
                },
        )
    }

    fun save(
        appWidgetId: Int,
        eventId: String?,
        appearance: WidgetAppearance,
        background: WidgetBackground,
        eventSelection: WidgetEventSelection,
        timeFormat: TimeWidgetFormat = TimeWidgetFormat.LEGACY,
    ) {
        preferences.edit()
            .putString(formatKey(appWidgetId), timeFormat.key)
            .remove(dayKey(appWidgetId))
            .remove(hoursKey(appWidgetId))
            .putString(eventKey(appWidgetId), eventId)
            .putString(appearanceKey(appWidgetId), appearance.storageKey)
            .putString(backgroundKey(appWidgetId), background.storageKey)
            .putString(selectionKey(appWidgetId), eventSelection.storageKey)
            .apply()
    }

    fun remap(oldAppWidgetId: Int, newAppWidgetId: Int) {
        if (oldAppWidgetId == newAppWidgetId) return
        val configuration = get(oldAppWidgetId)
        val editor = preferences.edit()
            .remove(formatKey(newAppWidgetId))
            .remove(dayKey(newAppWidgetId))
            .remove(hoursKey(newAppWidgetId))
            .remove(eventKey(newAppWidgetId))
            .remove(appearanceKey(newAppWidgetId))
            .remove(backgroundKey(newAppWidgetId))
            .remove(selectionKey(newAppWidgetId))

        configuration?.let { current ->
            editor
                .putString(formatKey(newAppWidgetId), current.timeFormat.key)
                .putString(eventKey(newAppWidgetId), current.eventId)
                .putString(appearanceKey(newAppWidgetId), current.appearance.storageKey)
                .putString(backgroundKey(newAppWidgetId), current.background.storageKey)
                .putString(selectionKey(newAppWidgetId), current.eventSelection.storageKey)
        }

        editor
            .remove(formatKey(oldAppWidgetId))
            .remove(dayKey(oldAppWidgetId))
            .remove(hoursKey(oldAppWidgetId))
            .remove(eventKey(oldAppWidgetId))
            .remove(appearanceKey(oldAppWidgetId))
            .remove(backgroundKey(oldAppWidgetId))
            .remove(selectionKey(oldAppWidgetId))
            .apply()
    }

    fun remove(appWidgetId: Int) {
        preferences.edit()
            .remove(formatKey(appWidgetId))
            .remove(dayKey(appWidgetId))
            .remove(hoursKey(appWidgetId))
            .remove(eventKey(appWidgetId))
            .remove(appearanceKey(appWidgetId))
            .remove(backgroundKey(appWidgetId))
            .remove(selectionKey(appWidgetId))
            .apply()
    }

    private fun formatKey(id: Int) = "widget_${id}_time_format"
    private fun dayKey(id: Int) = "widget_${id}_day_clock"
    private fun hoursKey(id: Int) = "widget_${id}_hours"
    private fun eventKey(appWidgetId: Int) = "widget_${appWidgetId}_event"
    private fun appearanceKey(appWidgetId: Int) = "widget_${appWidgetId}_appearance"
    private fun backgroundKey(appWidgetId: Int) = "widget_${appWidgetId}_background"
    private fun selectionKey(appWidgetId: Int) = "widget_${appWidgetId}_selection"

    private companion object {
        const val FILE_NAME = "countaway_widgets"
    }
}
