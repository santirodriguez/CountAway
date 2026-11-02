package com.santiagorodriguez.countaway.ui

import android.app.LocaleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ListView
import android.widget.ScrollView
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
import com.santiagorodriguez.countaway.widget.TimeWidgetConfigActivity
import com.santiagorodriguez.countaway.widget.TimeWidgetProvider
import com.santiagorodriguez.countaway.widget.WidgetAppearance
import com.santiagorodriguez.countaway.widget.WidgetBackground
import com.santiagorodriguez.countaway.widget.WidgetDefaultsPreferences
import com.santiagorodriguez.countaway.widget.WidgetEventSelection
import com.santiagorodriguez.countaway.widget.WidgetPinSetupActivity
import com.santiagorodriguez.countaway.widget.WidgetPreferences
import com.santiagorodriguez.countaway.widget.WidgetStyleSelection
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

/**
 * Full-screen, unmodified Android UI screenshots for visual review only.
 * Based on the six public v1.2.4 composition roles. Never changes Fastlane
 * assets, and never claims the slot-six preview is an actual launcher.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 36)
class ReleaseScreenshotReviewInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val repository = CountdownRepository(app)
    private val prefs = app.getSharedPreferences("countaway_ui", Context.MODE_PRIVATE)
    private val widgets = WidgetPreferences(app)
    private val defaults = WidgetDefaultsPreferences(app)
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")

    @Test fun captureSixCandidateViewsForHumanReview() {
        val previous = (repository.loadResult() as CountdownLoadResult.Success).events
        val oldDefaults = defaults.get()
        val oldTheme = prefs.getString("theme", null)
        val hadTheme = prefs.contains("theme")
        val oldLanguage = prefs.getString("language", null)
        val hadLanguage = prefs.contains("language")
        val locale = app.getSystemService(LocaleManager::class.java)
        val oldLocales = locale.applicationLocales
        val originalDensity = shell("wm density")
        val densityOverride = Regex("Override density: ([0-9]+)")
            .find(originalDensity)?.groupValues?.get(1)
        val host = AppWidgetHost(app, 1_250_125)
        val manager = AppWidgetManager.getInstance(app)
        val userId = shell("am get-current-user").toInt()
        val widgetIds = mutableListOf<Int>()
        try {
            // Match the naturally compact v1.2.4 portrait composition through
            // the real Android emulator display setting, never by editing pixels.
            // The original density is restored in finally.
            shell("wm density 240")
            SystemClock.sleep(500)
            locale.applicationLocales = LocaleList.forLanguageTags("en")
            prefs.edit().putString("theme", "DARK").commit()
            defaults.save(WidgetStyleSelection(WidgetAppearance.DARK, WidgetBackground.MONOGRAM))

            val today = LocalDate.now(zone)
            // Match the balanced public v1.2.4 Home: 2 upcoming, 2 count-up,
            // 1 past. Avoid the crowded four-upcoming list from the first take.
            val match = event("match", "Juega Vélez", today.plusDays(12), EventType.EVENT)
            val birthday = event("birthday", "Sophia's birthday", today.plusDays(65), EventType.BIRTHDAY)
            val smokeFree = event("smoke", "Smoke-free", today.minusDays(6315), EventType.CUSTOM)
                .copy(countMode = CountMode.COUNT_UP)
            val born = event("born", "I was born", today.minusDays(17092), EventType.BIRTHDAY)
                .copy(countMode = CountMode.COUNT_UP, eventTime = LocalTime.of(12, 55), eventZone = zone)
            val trip = event("trip", "Trip to Barcelona", today.minusDays(68), EventType.TRIP)
            val homeEvents = listOf(match, birthday, smokeFree, born, trip)
            val concert = event("concert", "Concert night", today.plusDays(12), EventType.CONCERT)
                .copy(eventTime = LocalTime.of(18, 30), eventZone = zone, repeatRule = RepeatRule.WEEKLY)
            repository.save(homeEvents)
            drain()

            ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)).use { scenario ->
                drain()
                scenario.onActivity { activity ->
                    val list = activity.findViewById<ListView>(R.id.countdownList)
                    // A ListView's retained selector can look like a grey
                    // Upcoming tab overlay. This removes only capture focus.
                    list.selector = ColorDrawable(Color.TRANSPARENT)
                    list.clearChoices()
                    list.isPressed = false
                    for (index in 0 until list.childCount) {
                        list.getChildAt(index).isPressed = false
                        list.getChildAt(index).isActivated = false
                    }
                    prepare(activity.window.decorView)
                }
                capture("1-home-dark")
            }

            // The timed-event editor uses real release-candidate data and a
            // neutral focus state; no input caret or soft keyboard on screenshots.
            repository.save(homeEvents + concert)
            drain()
            ActivityScenario.launch<EditorActivity>(
                Intent(app, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_EVENT_ID, concert.id)
            ).use { scenario ->
                drain()
                scenario.onActivity { activity ->
                    val input = activity.findViewById<EditText>(R.id.titleInput)
                    input.isCursorVisible = false
                    input.clearFocus()
                    activity.window.decorView.isFocusableInTouchMode = true
                    activity.window.decorView.requestFocus()
                    (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(input.windowToken, 0)
                    prepare(activity.window.decorView)
                    // Keep the heading and optional-time control fully in the
                    // native viewport. The ScrollView has 20dp of top padding,
                    // so a tiny natural scroll does not cut off the heading.
                    // This affects only the temporary review screenshot.
                    val scroll = activity.findViewById<ScrollView>(R.id.editorRoot)
                    scroll.scrollTo(0, (12 * activity.resources.displayMetrics.density).toInt())
                    assertEntirelyVisible(activity.findViewById(R.id.editorHeading),
                        "Editor heading")
                    assertEntirelyVisible(activity.findViewById(R.id.timeButton),
                        "Editor optional time")
                }
                capture("2-editor-timed")
            }
            repository.save(homeEvents)
            drain()

            // The v1.2.4 third capture was the REAL pin-style chooser, not the
            // home-screen widget selection list used in the previous attempt.
            ActivityScenario.launch<WidgetPinSetupActivity>(
                Intent(app, WidgetPinSetupActivity::class.java)
                    .putExtra(WidgetPinSetupActivity.EXTRA_EVENT_ID, trip.id)
            ).use { scenario ->
                drain()
                scenario.onActivity { activity ->
                    prepare(activity.window.decorView)
                    assertEntirelyVisible(activity.findViewById(R.id.widgetPinSetupPreviewFrame),
                        "Classic widget preview")
                    assertEntirelyVisible(activity.findViewById(R.id.widgetPinSetupAddButton),
                        "Add widget action")
                    assertEntirelyVisible(activity.findViewById(R.id.widgetPinSetupCancelButton),
                        "Cancel action")
                }
                capture("3-classic-widget-style")
            }

            ActivityScenario.launch<AboutActivity>(Intent(app, AboutActivity::class.java)).use { scenario ->
                drain()
                scenario.onActivity { activity ->
                    val version = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
                    assertEquals("1.2.5", version)
                    assertTrue(activity.findViewById<android.widget.TextView>(R.id.versionText)
                        .text.toString().contains("1.2.5"))
                    prepare(activity.window.decorView)
                    assertEntirelyVisible(activity.findViewById(R.id.aboutAuthorCredit),
                        "About author/footer")
                    assertEntirelyVisible(activity.findViewById(R.id.privacyButton),
                        "About privacy link")
                }
                SystemClock.sleep(1800)
                capture("4-about-v125")
            }

            prefs.edit().putString("theme", "LIGHT").commit()
            ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)).use { scenario ->
                drain()
                scenario.onActivity { activity ->
                    val list = activity.findViewById<ListView>(R.id.countdownList)
                    list.selector = ColorDrawable(Color.TRANSPARENT)
                    list.clearChoices()
                    list.isPressed = false
                    for (index in 0 until list.childCount) list.getChildAt(index).isPressed = false
                    prepare(activity.window.decorView)
                }
                capture("5-home-light")
            }

            // The sixth slot requires actual launcher widgets for historical
            // equivalence. Until those are captured, keep an honestly labeled
            // time-widget configuration alternative (not store-approved).
            prefs.edit().putString("theme", "DARK").commit()
            repository.save(homeEvents + concert)
            drain()
            assertTrue("Widget bind permission failed",
                shell("appwidget grantbind --package ${app.packageName} --user $userId").isBlank())
            host.deleteHost()
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
                scenario.onActivity { activity ->
                    prepare(activity.window.decorView)
                    assertEntirelyVisible(activity.findViewById(R.id.widgetPreviewFrame),
                        "Timed widget preview")
                    assertEntirelyVisible(activity.findViewById(R.id.widgetSaveButton),
                        "Timed widget save")
                }
                capture("6-timed-widget-alternative")
            }

            File(app.filesDir, "layout-evidence/release-review/REVIEW_ONLY.txt").writeText(
                "versionName=1.2.5\nversionCode=14\nsource=real Android API36 UI at 1080x1920, density=240\n" +
                "status=UNAPPROVED; no cropping, editing, or pixel reconstruction\n" +
                "slots=1 dark Home; 2 timed editor; 3 classic pin-style chooser; " +
                "4 About; 5 light Home; 6 time-widget configuration ALTERNATIVE, NOT launcher\n" +
                "No canonical Fastlane screenshot was replaced. Human review required.\n"
            )
        } finally {
            widgetIds.forEach(widgets::remove)
            runCatching { host.stopListening() }
            runCatching { host.deleteHost() }
            runCatching { shell("appwidget revokebind --package ${app.packageName} --user $userId") }
            repository.save(previous)
            defaults.save(oldDefaults)
            prefs.edit().apply {
                if (hadTheme) putString("theme", oldTheme) else remove("theme")
                if (hadLanguage) putString("language", oldLanguage) else remove("language")
            }.commit()
            locale.applicationLocales = oldLocales
            shell(if (densityOverride != null) "wm density $densityOverride" else "wm density reset")
            drain()
        }
    }

    private fun event(id: String, title: String, date: LocalDate, type: EventType) = CountdownEvent(
        id = "release-review-$id", title = title, date = date,
        type = type, createdAt = Instant.EPOCH,
    )

    private fun prepare(root: View) {
        // Remove scrollbar chrome and overscroll glow only in this test's
        // resumed Activity. The shipping layouts remain unchanged.
        root.isVerticalScrollBarEnabled = false
        root.isHorizontalScrollBarEnabled = false
        root.overScrollMode = View.OVER_SCROLL_NEVER
        root.isPressed = false
        if (root is ViewGroup) for (i in 0 until root.childCount) prepare(root.getChildAt(i))
    }

    private fun assertEntirelyVisible(view: View, label: String) {
        val rect = Rect()
        assertTrue("$label has no visible rectangle", view.getGlobalVisibleRect(rect))
        assertEquals("$label is clipped vertically", view.height, rect.height())
        assertEquals("$label is clipped horizontally", view.width, rect.width())
    }

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
        SystemClock.sleep(650)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) {
            "Android screenshot unavailable: $name"
        }
        try {
            assertEquals("Full 9:16 width", 1080, bitmap.width)
            assertEquals("Full 9:16 height", 1920, bitmap.height)
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
