package com.santiagorodriguez.countaway.model

enum class EventIcon(val storageKey: String) {
    AIRPLANE("airplane"),
    BOOK("book"),
    CONFETTI("confetti"),
    CAKE("cake"),
    HEART("heart"),
    MUSIC("music"),
    HOURGLASS("hourglass"),
    CALENDAR("calendar"),
    STAR("star"),
    GIFT("gift"),
    FLAG("flag"),
    PIN("pin");

    companion object {
        val customChoices: List<EventIcon> = listOf(
            STAR,
            GIFT,
            FLAG,
            PIN,
            HEART,
            MUSIC,
            AIRPLANE,
            CALENDAR,
        )

        fun fromStorageKey(value: String): EventIcon? = entries.firstOrNull { it.storageKey == value }

        fun defaultFor(type: EventType): EventIcon = when (type) {
            EventType.TRIP -> AIRPLANE
            EventType.EXAM -> BOOK
            EventType.PARTY -> CONFETTI
            EventType.BIRTHDAY -> CAKE
            EventType.ANNIVERSARY -> HEART
            EventType.CONCERT -> MUSIC
            EventType.DEADLINE -> HOURGLASS
            EventType.EVENT -> CALENDAR
            EventType.CUSTOM -> STAR
        }
    }
}
