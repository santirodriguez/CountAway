package com.santiagorodriguez.countaway.ui

import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.model.EventType

object EventTypePresentation {
    fun labelRes(type: EventType): Int = when (type) {
        EventType.TRIP -> R.string.event_type_trip
        EventType.FIRST_FLIGHT -> R.string.event_type_first_flight
        EventType.EXAM -> R.string.event_type_exam
        EventType.PARTY -> R.string.event_type_party
        EventType.BIRTHDAY -> R.string.event_type_birthday
        EventType.EVENT -> R.string.event_type_event
        EventType.CUSTOM -> R.string.event_type_custom
    }

    fun icon(type: EventType): String = when (type) {
        EventType.TRIP -> "✈"
        EventType.FIRST_FLIGHT -> "✈"
        EventType.EXAM -> "✎"
        EventType.PARTY -> "★"
        EventType.BIRTHDAY -> "◇"
        EventType.EVENT -> "◆"
        EventType.CUSTOM -> "•"
    }
}
