package com.santiagorodriguez.countaway.model

import java.time.Instant
import java.time.LocalDate

data class CountdownEvent(
    val id: String,
    val title: String,
    val date: LocalDate,
    val type: EventType,
    val icon: EventIcon = EventIcon.defaultFor(type),
    val reminder: ReminderOption = ReminderOption.OFF,
    val createdAt: Instant,
)
