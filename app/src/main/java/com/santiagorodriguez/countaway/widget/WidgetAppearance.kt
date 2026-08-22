package com.santiagorodriguez.countaway.widget

enum class WidgetAppearance(val storageKey: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    fun resolveDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStorageKey(value: String?): WidgetAppearance =
            entries.firstOrNull { it.storageKey == value } ?: SYSTEM
    }
}
