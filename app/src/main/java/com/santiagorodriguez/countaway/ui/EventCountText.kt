package com.santiagorodriguez.countaway.ui

import android.content.Context
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownStatus
import com.santiagorodriguez.countaway.countdown.CountUpState
import com.santiagorodriguez.countaway.countdown.EventCountValue
import com.santiagorodriguez.countaway.model.CountMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal object EventCountText {
    fun status(context: Context, value: EventCountValue): String {
        value.countUpState?.let { return countUpStatus(context, it, value.magnitude) }
        return when (checkNotNull(value.countdownStatus)) {
            CountdownStatus.FUTURE, CountdownStatus.THREE_DAYS, CountdownStatus.TWO_DAYS ->
                context.resources.getQuantityString(R.plurals.share_days_left, quantity(value.magnitude), value.magnitude)
            CountdownStatus.TOMORROW -> context.getString(R.string.share_tomorrow)
            CountdownStatus.TODAY -> context.getString(R.string.share_today)
            CountdownStatus.DONE ->
                context.resources.getQuantityString(R.plurals.status_days_ago, quantity(value.magnitude), value.magnitude)
        }
    }

    fun countUpStatus(context: Context, state: CountUpState, days: Long): String =
        context.resources.getQuantityString(
            if (state == CountUpState.ELAPSED) R.plurals.count_up_elapsed else R.plurals.count_up_starts_in,
            quantity(days), days,
        )

    fun countUpUnit(state: CountUpState, days: Long): Int = when (state) {
        CountUpState.ELAPSED -> if (days == 1L) R.string.count_up_day_elapsed else R.string.count_up_days_elapsed
        CountUpState.STARTS_IN -> if (days == 1L) R.string.count_up_day_until_start else R.string.count_up_days_until_start
    }

    fun date(context: Context, date: LocalDate, mode: CountMode, style: FormatStyle = FormatStyle.MEDIUM): String {
        val text = date.format(DateTimeFormatter.ofLocalizedDate(style).withLocale(context.resources.configuration.locales[0]))
        return if (mode == CountMode.COUNT_UP) context.getString(R.string.count_up_start_date_format, text) else text
    }

    fun modeLabel(mode: CountMode): Int = if (mode == CountMode.COUNT_UP) R.string.count_mode_up else R.string.count_mode_down

    private fun quantity(days: Long): Int = days.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}
