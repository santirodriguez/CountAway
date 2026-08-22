package com.santiagorodriguez.countaway.widget

enum class WidgetEventSelection(val storageKey: String) {
    FIXED("fixed"),
    NEXT("next");

    companion object {
        fun fromStorageKey(value: String?): WidgetEventSelection =
            entries.firstOrNull { it.storageKey == value } ?: FIXED
    }
}
