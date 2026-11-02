package com.santiagorodriguez.countaway.ui

import android.app.LocaleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.widget.ListView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.RepeatRule
import com.santiagorodriguez.countaway.widget.CountdownWidgetProvider
import com.santiagorodriguez.countaway.widget.TimeWidgetConfigActivity
import com.santiagorodriguez.countaway.widget.TimeWidgetProvider
import com.santiagorodriguez.countaway.widget.WidgetAppearance
import com.santiagorodriguez.countaway.widget.WidgetBackground
import com.santiagorodriguez.countaway.widget.WidgetConfigActivity
import com.santiagorodriguez.countaway.widget.WidgetEventSelection
import com.santiagorodriguez.countaway.widget.WidgetPreferences
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exact app UI screenshots for human review, not approved store assets.
 * Never overwrites the existing accepted Fastlane images.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 36)
class ReleaseScreenshotReviewInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val repository = CountdownRepository(app)
    private val prefs = app.getSharedPreferences("countaway_ui", Context.MODE_PRIVATE)
    private val widgets = WidgetPreferences(app)
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")

    @Test fun captureSixCandidateViewsForHumanReview() {
        val previous = (repository.loadResult() as CountdownLoadResult.Success).events
        val oldTheme = prefs.getString("theme", null)
        val hadTheme = prefs.contains("theme")
        val oldLanguage = prefs.getString("language", null)
        val hadLanguage = prefs.contains("language")
        val locale = app.getSystemService(LocaleManager::class.java)
        val oldLocales = locale.applicationLocales
        val host = AppWidgetHost(app, 1_250_125)
        val manager = AppWidgetManager.getInstance(app)
        val userId = shell("am get-current-user").toInt()
        val widgetIds = mutableListOf<Int>()
        try {
            locale.applicationLocales = LocaleList.forLanguageTags("en")
            prefs.edit().putString("theme", "DARK").commit()
            val today = LocalDate.now(zone)
            val trip = event("trip", "Trip to Barcelona", today.plusDays(68), EventType.TRIP)
            val birthday = event("birthday", "Sophia's birthday", today.plusDays(65), EventType.BIRTHDAY)
            val concert = event("concert", "Concert night", today.plusDays(12), EventType.CONCERT)
                .copy(eventTime = LocalTime.of(18, 30), eventZone = zone, repeatRule = RepeatRule.WEEKLY)
            val elapsed = event("habit", "New habit", today.minusDays(420), EventType.CUSTOM)
                .copy(countMode = CountMode.COUNT_UP)
            val milestone = event("milestone", "The big day", today.plusDays(30), EventType.EVENT)
            repository.save(listOf(trip, birthday, concert, elapsed, milestone))
            drain()

            ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)).use {
                drain()
                capture("1-home-dark")
            }
            ActivityScenario.launch<EditorActivity>(
                Intent(app, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_EVENT_ID, concert.id)
            ).use {
                drain()
                capture("2-editor-timed")
            }

            assertTrue("Widget bind permission failed",
                shell("appwidget grantbind --package ${app.packageName} --user $userId").isBlank())
            host.deleteHost()
            val classicId = host.allocateAppWidgetId()
            widgetIds.add(classicId)
            assertTrue(manager.bindAppWidgetIdIfAllowed(classicId,
                ComponentName(app, CountdownWidgetProvider::class.java), Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 240)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
                }))
            widgets.save(classicId, trip.id, WidgetAppearance.DARK,
                WidgetBackground.MONOGRAM, WidgetEventSelection.FIXED)
            ActivityScenario.launch<WidgetConfigActivity>(
                Intent(app, WidgetConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, classicId)
            ).use { scenario ->
                drain()
                scenario.onActivity {
                    val list = it.findViewById<ListView>(R.id.widgetEventList)
                    assertTrue("No classic options", list.count > 3)
                    list.setSelection(list.count - 1)
                }
                drain()
                capture("3-classic-widget-editor")
            }

            ActivityScenario.launch<AboutActivity>(Intent(app, AboutActivity::class.java)).use { scenario ->
                drain()
                scenario.onActivity {
                    val version = it.packageManager.getPackageInfo(it.packageName, 0).versionName
                    assertEquals("1.2.5", version)
                    assertTrue(it.findViewById<android.widget.TextView>(R.id.versionText)
                        .text.toString().contains("1.2.5"))
                }
                // A preceding integration test may have left an Android toast.
                SystemClock.sleep(3500)
                capture("4-about-v125")
            }

            prefs.edit().putString("theme", "LIGHT").commit()
            ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)).use {
                drain()
                capture("5-home-light")
            }
            prefs.edit().putString("theme", "DARK").commit()
            val timeId = host.allocateAppWidgetId()
            widgetIds.add(timeId)
            assertTrue(manager.bindAppWidgetIdIfAllowed(timeId,
                ComponentName(app, TimeWidgetProvider::class.java), Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 240)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
                }))
            widgets.save(timeId, concert.id, WidgetAppearance.DARK,
                WidgetBackground.HORIZON, WidgetEventSelection.FIXED)
            ActivityScenario.launch<TimeWidgetConfigActivity>(
                Intent(app, TimeWidgetConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, timeId)
            ).use { scenario ->
                drain()
                scenario.onActivity {
                    val list = it.findViewById<ListView>(R.id.widgetEventList)
                    assertTrue("No timed options", list.count > 2)
                    list.setSelection(list.count - 1)
                }
                drain()
                capture("6-live-hours-widget-editor")
            }
            File(app.filesDir, "layout-evidence/release-review/REVIEW_ONLY.txt").writeText(
                "versionName=1.2.5\nversionCode=14\nsource=Android API 36 UiAutomation\n" +
                    "status=UNAPPROVED\n" +
                    "slots=1 Home dark; 2 timed editor; 3 classic widget config; " +
                    "4 About; 5 Home light; 6 time widget config (not launcher)\n" +
                    "Never replace accepted screenshots without explicit visual approval.\n"
            )
        } finally {
            widgetIds.forEach(widgets::remove)
            runCatching { host.stopListening() }
            runCatching { host.deleteHost() }
            runCatching { shell("appwidget revokebind --package ${app.packageName} --user $userId") }
            repository.save(previous)
            prefs.edit().apply {
                if (hadTheme) putString("theme", oldTheme) else remove("theme")
                if (hadLanguage) putString("language", oldLanguage) else remove("language")
            }.commit()
            locale.applicationLocales = oldLocales
            drain()
        }
    }

    private fun event(id: String, title: String, date: LocalDate, type: EventType) = CountdownEvent(
        id = "release-review-$id", title = title, date = date,
        type = type, createdAt = Instant.EPOCH,
    )

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)
        ).use { it.readBytes().toString(Charsets.UTF_8).trim() }

    private fun drain() {
        val latch = CountDownLatch(1)
        CountdownIo.submit({ Unit }) { latch.countDown() }
        assertTrue(latch.await(15, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(250)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) {
            "Android screenshot unavailable: $name"
        }
        try {
            assertEquals(1080, bitmap.width)
            assertEquals(1920, bitmap.height)
            val file = File(app.filesDir, "layout-evidence/release-review/$name.png")
            checkNotNull(file.parentFile).mkdirs()
            file.outputStream().use { stream ->
                assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
