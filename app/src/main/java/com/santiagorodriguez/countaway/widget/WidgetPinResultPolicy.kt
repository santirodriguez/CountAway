package com.santiagorodriguez.countaway.widget

internal object WidgetPinResultPolicy {
    fun shouldApply(
        validWidgetId: Boolean,
        eventExists: Boolean,
        existing: WidgetConfiguration?,
    ): Boolean = validWidgetId && eventExists && existing == null

    fun matchesSnapshot(
        existing: WidgetConfiguration,
        snapshot: WidgetPinRequestSnapshot,
    ): Boolean =
        existing.eventSelection == WidgetEventSelection.FIXED &&
            existing.eventId == snapshot.eventId &&
            existing.appearance == snapshot.style.appearance &&
            existing.background == snapshot.style.background
}
