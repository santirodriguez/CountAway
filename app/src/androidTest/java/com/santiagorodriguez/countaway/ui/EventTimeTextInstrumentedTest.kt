package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class EventTimeTextInstrumentedTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun forcedClockPreferencesAreIndependentOfAppLocale() {
        for (tag in listOf("en-US", "es-AR", "ca-ES")) {
            val locale = Locale.forLanguageTag(tag)
            for (time in listOf(LocalTime.of(0, 5), LocalTime.NOON, LocalTime.of(23, 5))) {
                val twelve = EventTimeText.time(locale, false, time)
                val twentyFour = EventTimeText.time(locale, true, time)
                assertNotEquals("$tag at $time", twelve, twentyFour)
            }
        }
    }

    @Test fun contextFormattingUsesItsLanguageAndTheActualSystemClockPreference() {
        for (tag in listOf("en-US", "es-AR", "ca-ES")) {
            val context = localized(tag)
            val locale = context.resources.configuration.locales[0]
            val target = ZonedDateTime.of(2026, 10, 18, 23, 5, 0, 0,
                ZoneId.of("America/New_York"))
            val expected = EventTimeText.time(locale, DateFormat.is24HourFormat(context), target)
            assertEquals(expected, EventTimeText.time(context, target))
            val nativeTime = target.format(
                DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
            val localizedDateTime = target.format(
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    .withLocale(locale))
            assertTrue(localizedDateTime.contains(nativeTime))
            assertEquals(localizedDateTime.replaceFirst(nativeTime, expected),
                EventTimeText.dateTime(context, target))
        }
    }

    @Test fun ambiguousOverlapLeavesTheTargetOffsetUnchanged() {
        val zone = ZoneId.of("America/New_York")
        val local = LocalDateTime.of(2026, 11, 1, 1, 30)
        val early = ZonedDateTime.ofLocal(local, zone, ZoneOffset.ofHours(-4))
        val late = ZonedDateTime.ofLocal(local, zone, ZoneOffset.ofHours(-5))
        assertNotEquals(early.toInstant(), late.toInstant())
        assertEquals(EventTimeText.time(base, early), EventTimeText.time(base, late))
        assertEquals(ZoneOffset.ofHours(-4), early.offset)
        assertEquals(ZoneOffset.ofHours(-5), late.offset)
    }

    private fun localized(tag: String): Context = base.createConfigurationContext(
        Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(tag))
        })
}
