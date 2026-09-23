package com.santiagorodriguez.countaway.model

enum class RepeatRule(val storageKey: String) {
    NONE("none"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    YEARLY("yearly");

    companion object {
        fun fromStorageKey(value: String): RepeatRule? =
            entries.firstOrNull { it.storageKey == value }
    }
}
