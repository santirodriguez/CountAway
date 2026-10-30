package com.santiagorodriguez.countaway.share

import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class TimedShareContractInstrumentedTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    private val now = ZonedDateTime.of(2026, 10, 15, 12, 0, 45, 0, zone)
    private val date = LocalDate.of(2026, 10, 18)

    @Test fun cardAndTextUseSameMinuteSnapshotAcrossLanguages() {
        for (lang in listOf("en", "es", "ca")) {
            val ctx = localized(lang)
            val time = LocalTime.of(19, 13)
            val content = ShareCardContentFactory.create(ctx, "Trip", date, EventIcon.AIRPLANE,
                RepeatRule.NONE, now.toLocalDate(), CountMode.COUNT_DOWN, time, zone, now)
            val text = ShareCardContentFactory.text(ctx, "Trip", date, RepeatRule.NONE,
                now.toLocalDate(), CountMode.COUNT_DOWN, time, zone, now)
            assertTrue(text.contains(content.dateText))
            assertTrue(content.primaryText.contains("d"))
            assertTrue(content.secondaryText!!.contains("min"))
            assertFalse(text.contains("05 s"))
            val bitmap = ShareCardRenderer.render(ctx, content, dark = false)
            try { assertEquals(768, bitmap.width); assertEquals(768, bitmap.height) }
            finally { bitmap.recycle() }
        }
    }

    @Test fun positiveSubminuteNeverClaimsZeroAndExactArrivalIsDistinct() {
        val ctx = localized("en")
        val target = now.toLocalTime().plusSeconds(30)
        val near = TimedSharePresenter.present(ctx, now.toLocalDate(), RepeatRule.NONE,
            CountMode.COUNT_DOWN, target, zone, now)
        assertTrue(near.summary.contains(ctx.getString(R.string.share_timed_less_than_minute)))
        val exact = TimedSharePresenter.present(ctx, now.toLocalDate(), RepeatRule.NONE,
            CountMode.COUNT_DOWN, target, zone, now.plusSeconds(30))
        assertEquals(ctx.getString(R.string.share_timed_now), exact.summary)
        val elapsed = TimedSharePresenter.present(ctx, now.toLocalDate(), RepeatRule.NONE,
            CountMode.COUNT_DOWN, target, zone, now.plusSeconds(40))
        assertTrue(elapsed.summary.contains(ctx.getString(R.string.share_timed_less_than_minute)))
    }

    @Test fun differingZoneAndRecurringOccurrenceUseEffectiveTimestamp() {
        val ctx = localized("es")
        val otherZone = ZoneId.of("America/New_York")
        val effective = TimedSharePresenter.present(ctx, LocalDate.of(2026, 1, 31),
            RepeatRule.MONTHLY, CountMode.COUNT_DOWN, LocalTime.of(18, 30),
            otherZone, now)
        assertEquals(LocalDate.of(2026, 10, 31), effective.date)
        assertTrue(effective.timestamp.contains(otherZone.id))
        assertTrue(effective.timestamp.contains("18:30") ||
            effective.timestamp.contains("6:30"))
    }

    private fun localized(tag: String): Context = base.createConfigurationContext(
        Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(tag))
        })
}
