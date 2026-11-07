package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.text.format.DateFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAccessor
import java.util.Locale

/** Civil clock presentation; never use this for countdown durations or stored HH:mm. */
internal object EventTimeText {
    fun time(context: Context, value: TemporalAccessor): String =
        time(
            context.resources.configuration.locales[0],
            DateFormat.is24HourFormat(context),
            value,
        )

    // Explicit preference mismatch tests do not mutate global device settings.
    internal fun time(locale: Locale, use24Hour: Boolean, value: TemporalAccessor): String {
        val pattern = DateFormat.getBestDateTimePattern(locale, if (use24Hour) "Hm" else "hm")
        return DateTimeFormatter.ofPattern(pattern, locale).format(value)
    }

    fun dateTime(context: Context, target: ZonedDateTime): String {
        val locale = context.resources.configuration.locales[0]
        val dateTime = target.format(
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale),
        )
        val nativeTime = target.format(
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale),
        )
        // Preserve the locale's original date/time order and punctuation.
        // Replace only the clock portion to honor the system's 12/24-hour setting.
        if (dateTime.contains(nativeTime)) {
            return dateTime.replaceFirst(nativeTime, time(context, target))
        }
        val date = target.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        return "$date · ${time(context, target)}"
    }
}
