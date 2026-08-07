package com.santiagorodriguez.countaway.widget

enum class WidgetAppearance(val storageKey: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorageKey(value: String?): WidgetAppearance =
            entries.firstOrNull { it.storageKey == value } ?: SYSTEM
    }
}
