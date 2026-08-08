package com.santiagorodriguez.countaway.countdown

object ArrivalMood {
    fun marker(status: CountdownStatus): String? = when (status) {
        CountdownStatus.THREE_DAYS -> "🙂"
        CountdownStatus.TWO_DAYS -> "😬"
        CountdownStatus.TOMORROW -> "😱"
        CountdownStatus.TODAY -> "🎉"
        CountdownStatus.FUTURE,
        CountdownStatus.DONE,
        -> null
    }
}
