package com.santiagorodriguez.countaway.model

enum class CountMode(val storageKey: String) {
    COUNT_DOWN("count_down"),
    COUNT_UP("count_up");

    companion object {
        fun fromStorageKey(value: String): CountMode? =
            entries.firstOrNull { it.storageKey == value }
    }
}
