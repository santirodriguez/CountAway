package com.santiagorodriguez.countaway.notification

object ArrivalNotificationIdentity {
    const val ID = 1

    fun tag(eventId: String): String = eventId
}
