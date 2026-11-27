package com.santiagorodriguez.countaway.widget

import com.santiagorodriguez.countaway.R

enum class TimeWidgetFormat(val key: String, internal val label: Int, internal val help: Int) {
    LEGACY("legacy", R.string.widget_time_legacy, R.string.widget_hours_info),
    DAYS_HOURS("days-hours", R.string.widget_home_time, R.string.widget_dayclock_info),
    HOURS("hours", R.string.widget_only_hours, R.string.widget_hours_info),
    LIVE_COUNTER("live-counter", R.string.widget_live_counter, R.string.widget_live_counter_info);

    companion object {
        internal val selectable = listOf(DAYS_HOURS, HOURS, LIVE_COUNTER)

        internal fun fromKey(key: String?) = entries.firstOrNull { it.key == key }
    }
}
