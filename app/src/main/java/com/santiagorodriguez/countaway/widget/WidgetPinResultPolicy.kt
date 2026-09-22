package com.santiagorodriguez.countaway.widget

internal object WidgetPinResultPolicy {
    fun shouldApply(
        owned: Boolean,
        eventExists: Boolean,
        existing: WidgetConfiguration?,
    ): Boolean = owned && eventExists && existing == null
}
