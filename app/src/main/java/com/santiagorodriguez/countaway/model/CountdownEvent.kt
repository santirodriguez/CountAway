package com.santiagorodriguez.countaway.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class CountdownEvent(
    val id: String,
    val title: String,
    val date: LocalDate,
    val type: EventType,
    val icon: EventIcon = EventIcon.defaultFor(type),
    val reminder: ReminderOption = ReminderOption.OFF,
    val createdAt: Instant,
    val repeatRule: RepeatRule = RepeatRule.NONE,
    val countMode: CountMode = CountMode.COUNT_DOWN,
    val eventTime: LocalTime? = null,
    val eventZone: ZoneId? = null,
)
