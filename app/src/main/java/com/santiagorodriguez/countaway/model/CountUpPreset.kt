package com.santiagorodriguez.countaway.model

/** Creation shortcuts only; event JSON retains the existing type and icon keys. */
enum class CountUpPreset(val icon: EventIcon) {
    SMOKE_FREE(EventIcon.HEART),
    NEW_HABIT(EventIcon.STAR),
    TRAINING(EventIcon.FLAG),
    LEARNING(EventIcon.BOOK),
    PROJECT(EventIcon.HOURGLASS),
    NEW_JOB(EventIcon.PIN),
    NEW_HOME(EventIcon.GIFT),
    EVENT(EventIcon.CALENDAR),
    CUSTOM(EventIcon.STAR);

    val type: EventType
        get() = if (this == EVENT) EventType.EVENT else EventType.CUSTOM

    val suggestsTitle: Boolean
        get() = this != EVENT && this != CUSTOM

    fun titleFor(currentTitle: String, suggestion: String): String =
        if (suggestsTitle && currentTitle.isBlank()) suggestion else currentTitle

    companion object {
        fun forStoredType(type: EventType): CountUpPreset? = when (type) {
            EventType.EVENT -> EVENT
            EventType.CUSTOM -> CUSTOM
            else -> null
        }
    }
}
