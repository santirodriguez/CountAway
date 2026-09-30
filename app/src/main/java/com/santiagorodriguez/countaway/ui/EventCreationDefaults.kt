package com.santiagorodriguez.countaway.ui

import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.EventType
import java.time.LocalDate

internal object EventCreationDefaults {
    fun dateForMode(current: LocalDate, mode: CountMode, today: LocalDate,
                    isNew: Boolean, dateChosen: Boolean): LocalDate =
        if (!isNew || dateChosen) current else
            if (mode == CountMode.COUNT_UP) today else today.plusDays(1)

    fun typeForMode(current: EventType, mode: CountMode,
                    isNew: Boolean, typeChosen: Boolean): EventType =
        if (!isNew || typeChosen) current else
            if (mode == CountMode.COUNT_UP) EventType.EVENT else EventType.TRIP
}
