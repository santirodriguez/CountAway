package com.santiagorodriguez.countaway.model

enum class ReminderOption(
    val storageKey: String,
    val daysBefore: Int?,
) {
    OFF("off", null),
    ON_DAY("on_day", 0),
    ONE_DAY("one_day", 1),
    THREE_DAYS("three_days", 3),
    SEVEN_DAYS("seven_days", 7);

    companion object {
        fun fromStorageKey(value: String): ReminderOption? =
            entries.firstOrNull { it.storageKey == value }
    }
}
