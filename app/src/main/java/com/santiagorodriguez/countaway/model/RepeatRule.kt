package com.santiagorodriguez.countaway.model

enum class RepeatRule(val storageKey: String) {
    NONE("none"),
    YEARLY("yearly");

    companion object {
        fun fromStorageKey(value: String): RepeatRule? =
            entries.firstOrNull { it.storageKey == value }
    }
}
