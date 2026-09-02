package com.santiagorodriguez.countaway.widget

import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownCalculator
import com.santiagorodriguez.countaway.countdown.CountdownEventOrder
import com.santiagorodriguez.countaway.countdown.CountdownStatus
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.ui.EventIconPresentation
import java.time.LocalDate

internal data class WidgetEventContent(
    val iconRes: Int,
    val title: String,
    val countText: String,
    val unitRes: Int?,
    val status: CountdownStatus,
) {
    fun countTextFor(size: WidgetSize): String =
        if (size == WidgetSize.COMPACT && status == CountdownStatus.DONE) {
            "✓ $countText"
        } else {
            countText
        }
}

internal object WidgetEventResolver {
    fun resolve(
        selection: WidgetEventSelection,
        eventId: String?,
        events: List<CountdownEvent>,
        today: LocalDate,
    ): CountdownEvent? = when (selection) {
        WidgetEventSelection.FIXED -> eventId?.let { id -> events.firstOrNull { it.id == id } }
        WidgetEventSelection.NEXT -> CountdownEventOrder.sortedForDisplay(events, today)
            .firstOrNull { !it.date.isBefore(today) }
    }
}

internal object WidgetEventContentFactory {
    fun from(event: CountdownEvent, today: LocalDate): WidgetEventContent {
        val value = CountdownCalculator.value(today, event.date)
        val countText = when (value.status) {
            CountdownStatus.FUTURE,
            CountdownStatus.THREE_DAYS,
            CountdownStatus.TWO_DAYS,
            CountdownStatus.TOMORROW,
            CountdownStatus.TODAY,
            -> value.days.coerceAtLeast(0).toString()
            CountdownStatus.DONE -> value.elapsedDays.toString()
        }
        val unitRes = when (value.status) {
            CountdownStatus.FUTURE,
            CountdownStatus.THREE_DAYS,
            CountdownStatus.TWO_DAYS,
            -> R.string.widget_days_left
            CountdownStatus.TOMORROW -> R.string.status_tomorrow
            CountdownStatus.TODAY -> R.string.status_today
            CountdownStatus.DONE -> if (value.elapsedDays == 1L) {
                R.string.widget_day_ago
            } else {
                R.string.widget_days_ago
            }
        }

        return WidgetEventContent(
            iconRes = EventIconPresentation.drawableRes(event.icon),
            title = event.title,
            countText = countText,
            unitRes = unitRes,
            status = value.status,
        )
    }
}
