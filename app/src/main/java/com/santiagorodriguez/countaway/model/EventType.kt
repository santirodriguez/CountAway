package com.santiagorodriguez.countaway.model

enum class EventType(val storageKey: String) {
    TRIP("trip"),
    FIRST_FLIGHT("first_flight"),
    EXAM("exam"),
    PARTY("party"),
    BIRTHDAY("birthday"),
    EVENT("event"),
    CUSTOM("custom");

    companion object {
        fun fromStorageKey(value: String): EventType? = entries.firstOrNull { it.storageKey == value }

        fun fromLegacyName(value: String): EventType? = entries.firstOrNull { it.name == value }
    }
}
