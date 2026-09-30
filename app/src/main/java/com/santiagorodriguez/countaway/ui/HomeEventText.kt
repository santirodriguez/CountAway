package com.santiagorodriguez.countaway.ui

import android.content.Context
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownStatus
import com.santiagorodriguez.countaway.countdown.CountUpState
import com.santiagorodriguez.countaway.countdown.EventCountValue
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Compact Home copy; widget and sharing strings deliberately keep their own contracts. */
internal object HomeEventText {
    fun status(context: Context, value: EventCountValue): String {
        if (value.countdownStatus == CountdownStatus.TODAY) return context.getString(R.string.status_today)
        val resource = when {
            value.countUpState == CountUpState.STARTS_IN -> R.plurals.home_days_until_start
            value.countUpState == CountUpState.ELAPSED -> R.plurals.home_days_since
            value.countdownStatus == CountdownStatus.DONE -> R.plurals.home_days_ago
            else -> R.plurals.home_days
        }
        val number = NumberFormat.getIntegerInstance(context.resources.configuration.locales[0]).format(value.magnitude)
        return context.resources.getQuantityString(resource,
            value.magnitude.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), number)
    }

    fun startDate(context: Context, value: EventCountValue): String {
        val date = value.displayDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(context.resources.configuration.locales[0]))
        return context.getString(if (value.countUpState == CountUpState.STARTS_IN)
            R.string.home_start_date else R.string.home_since_date, date)
    }
}
