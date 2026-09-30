package com.santiagorodriguez.countaway.widget

import android.content.Context
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownEventOrder
import com.santiagorodriguez.countaway.countdown.CountdownOccurrenceResolver
import com.santiagorodriguez.countaway.countdown.CountdownStatus
import com.santiagorodriguez.countaway.countdown.CountUpState
import com.santiagorodriguez.countaway.countdown.EventCountResolver
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.ui.EventCountText
import com.santiagorodriguez.countaway.ui.EventIconPresentation
import java.time.LocalDate

internal data class WidgetEventContent(
    val iconRes: Int,
    val title: String,
    val date: LocalDate,
    val countText: String,
    val unitRes: Int?,
    val status: CountdownStatus?,
    val countUpState: CountUpState? = null,
) {
    fun countTextFor(size: WidgetSize, unitVisible: Boolean = true): String = when {
        countUpState != null && (size == WidgetSize.COMPACT || size == WidgetSize.SHORT || !unitVisible) ->
            (if (countUpState == CountUpState.ELAPSED) "+" else "−") + countText
        size == WidgetSize.COMPACT && status == CountdownStatus.DONE -> "✓ $countText"
        else -> countText
    }

    fun statusDescription(context: Context): String {
        val days = countText.toLongOrNull() ?: 0L
        countUpState?.let { return EventCountText.countUpStatus(context, it, days) }
        return when (checkNotNull(status)) {
            CountdownStatus.FUTURE, CountdownStatus.THREE_DAYS, CountdownStatus.TWO_DAYS ->
                context.getString(R.string.status_days, days)
            CountdownStatus.TOMORROW -> context.getString(R.string.status_tomorrow)
            CountdownStatus.TODAY -> context.getString(R.string.status_today)
            CountdownStatus.DONE -> context.resources.getQuantityString(
                R.plurals.status_days_ago, days.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(), days,
            )
        }
    }

    fun dateText(context: Context): String = EventCountText.date(context, date,
        if (countUpState == null) CountMode.COUNT_DOWN else CountMode.COUNT_UP)

    fun description(context: Context): String = listOf(
        title, statusDescription(context), dateText(context), context.getString(R.string.widget_open_event),
    ).joinToString(", ")
}

internal object WidgetEventResolver {
    fun resolveNext(events: List<CountdownEvent>, today: LocalDate): CountdownEvent? =
        CountdownEventOrder.sortedForDisplay(events.filter { it.countMode == CountMode.COUNT_DOWN }, today)
            .firstOrNull { event -> !CountdownOccurrenceResolver.displayDate(event, today).isBefore(today) }

    fun resolve(selection: WidgetEventSelection, eventId: String?, events: List<CountdownEvent>, today: LocalDate): CountdownEvent? =
        when (selection) {
            WidgetEventSelection.FIXED -> eventId?.let { id -> events.firstOrNull { it.id == id } }
            WidgetEventSelection.NEXT -> resolveNext(events, today)
        }
}

internal object WidgetEventContentFactory {
    fun from(event: CountdownEvent, today: LocalDate): WidgetEventContent {
        val value = EventCountResolver.resolve(event, today)
        val unitRes = value.countUpState?.let { EventCountText.countUpUnit(it, value.magnitude) }
            ?: when (checkNotNull(value.countdownStatus)) {
                CountdownStatus.FUTURE, CountdownStatus.THREE_DAYS, CountdownStatus.TWO_DAYS -> R.string.widget_days_left
                CountdownStatus.TOMORROW -> R.string.status_tomorrow
                CountdownStatus.TODAY -> R.string.status_today
                CountdownStatus.DONE -> if (value.magnitude == 1L) R.string.widget_day_ago else R.string.widget_days_ago
            }
        return WidgetEventContent(
            iconRes = EventIconPresentation.drawableRes(event.icon),
            title = event.title,
            date = value.displayDate,
            countText = value.magnitude.toString(),
            unitRes = unitRes,
            status = value.countdownStatus,
            countUpState = value.countUpState,
        )
    }
}
