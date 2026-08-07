package com.santiagorodriguez.countaway.widget

import android.content.Context

data class WidgetConfiguration(
    val eventId: String,
    val appearance: WidgetAppearance,
)

class WidgetPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun get(appWidgetId: Int): WidgetConfiguration? {
        val eventId = preferences.getString(eventKey(appWidgetId), null) ?: return null
        return WidgetConfiguration(
            eventId = eventId,
            appearance = WidgetAppearance.fromStorageKey(preferences.getString(appearanceKey(appWidgetId), null)),
        )
    }

    fun save(appWidgetId: Int, eventId: String, appearance: WidgetAppearance) {
        preferences.edit()
            .putString(eventKey(appWidgetId), eventId)
            .putString(appearanceKey(appWidgetId), appearance.storageKey)
            .apply()
    }

    fun remove(appWidgetId: Int) {
        preferences.edit()
            .remove(eventKey(appWidgetId))
            .remove(appearanceKey(appWidgetId))
            .apply()
    }

    private fun eventKey(appWidgetId: Int) = "widget_${appWidgetId}_event"
    private fun appearanceKey(appWidgetId: Int) = "widget_${appWidgetId}_appearance"

    private companion object {
        const val FILE_NAME = "countaway_widgets"
    }
}
