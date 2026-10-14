package com.santiagorodriguez.countaway.notification

import com.santiagorodriguez.countaway.data.EventTransport

object ArrivalNotificationIdentity {
    const val ID = 1

    fun tag(eventId: String): String = EventTransport.wireId(eventId)
}
