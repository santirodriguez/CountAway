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
import android.view.MotionEvent
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
import com.santiagorodriguez.countaway.data.EventRevision
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
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Full-screen, unmodified Android UI screenshots for visual review only.
 * Based on the six public v1.2.4 composition roles. Never changes Fastlane
 * assets, and never claims the slot-six preview is an actual launcher.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class IsolatedStoreScreenshot

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
    private val visibilityFindings = mutableListOf<String>()

    /**
     * Best-effort extra review evidence from the emulator's actual home screen.
     * A widget preview inside this app is never labelled as a launcher screenshot.
     * This diagnostic is deliberately non-gating: unsupported launchers leave an
     * explicit NOT_CAPTURED receipt instead of substituting rendered artwork.
     */
    @Test fun attemptRealLauncherWidgetCaptureForHumanReview() {
        // Pinning changes launcher state and must not contaminate behavioral tests.
        // Opt in with: -e countawayLauncherReview true
        assumeTrue(
            "Launcher screenshot capture is separate from the standard test suite",
            InstrumentationRegistry.getArguments().getString("countawayLauncherReview") == "true",
        )
        val file = File(app.filesDir, "layout-evidence/release-review/LAUNCHER_CAPTURE.txt")
        checkNotNull(file.parentFile).mkdirs()
        val notes = mutableListOf("source=Android API36 emulator launcher; unapproved review evidence")
        val previous = (repository.loadResult() as CountdownLoadResult.Success).events
        val previousTheme = prefs.getString("theme", null)
        val hadTheme = prefs.contains("theme")
        val locale = app.getSystemService(LocaleManager::class.java)
        val oldLocales = locale.applicationLocales
        val densityBefore = shell("wm density")
        val densityOverride = Regex("Override density: ([0-9]+)")
            .find(densityBefore)?.groupValues?.get(1)
        val oldDefaults = defaults.get()
        val manager = AppWidgetManager.getInstance(app)
        var verifiedLauncherPackage: String? = null
        try {
            if (!manager.isRequestPinAppWidgetSupported) {
                notes += "status=NOT_CAPTURED; reason=launcher_pin_request_unsupported"
                return
            }
            shell("wm density 280")
            locale.applicationLocales = LocaleList.forLanguageTags("en")
            prefs.edit().putString("theme", "DARK").commit()
            val today = LocalDate.now(zone)
            val day = event("launcher-day", "Trip to Barcelona", today.plusDays(12), EventType.TRIP)
            val weekend = event("launcher-weekend", "Beach weekend", today.plusDays(36), EventType.TRIP)
            val newYear = event("launcher-year", "New Year's Eve", today.plusDays(59), EventType.EVENT)
            val timed = event("launcher-time", "Concert night", today.plusDays(12), EventType.CONCERT)
                .copy(eventTime = LocalTime.of(18, 30), eventZone = ZoneId.of("Europe/Madrid"), repeatRule = RepeatRule.WEEKLY)
            repository.save(listOf(day, weekend, newYear, timed))
            drain()

            val classicProvider = ComponentName(app,
                com.santiagorodriguez.countaway.widget.CountdownWidgetProvider::class.java)
            val classicSamples = listOf(
                Triple(day, WidgetAppearance.DARK, WidgetBackground.MONOGRAM),
                Triple(weekend, WidgetAppearance.LIGHT, WidgetBackground.SUNSET),
                Triple(newYear, WidgetAppearance.DARK, WidgetBackground.FOREST),
            )
            val classicIds = mutableListOf<Int>()
            for ((sample, appearance, background) in classicSamples) {
                val id = requestLauncherWidget(classicProvider, notes, false) ?: run {
                    notes += "status=NOT_CAPTURED; reason=classic_pin_failed_${sample.id}"
                    return
                }
                widgets.save(id, sample.id, appearance, background, WidgetEventSelection.FIXED)
                com.santiagorodriguez.countaway.widget.CountdownWidgetProvider.updateWidget(
                    app, manager, id)
                classicIds += id
                notes += "classic_widget_id=$id; event=${sample.title}; style=$background; appearance=$appearance"
            }

            val timeProvider = ComponentName(app, TimeWidgetProvider::class.java)
            val timeId = requestLauncherWidget(timeProvider, notes, true) ?: run {
                notes += "status=NOT_CAPTURED; reason=time_widget_pin_not_confirmed"
                return
            }
            widgets.save(timeId, timed.id, WidgetAppearance.DARK,
                WidgetBackground.HORIZON, WidgetEventSelection.FIXED)
            TimeWidgetProvider.updateWidget(app, manager, timeId)
            notes += "timed_widget_id=$timeId"
            shell("input keyevent KEYCODE_HOME")
            SystemClock.sleep(1_800)
            val foreground = instrumentation.uiAutomation.rootInActiveWindow
                ?.packageName?.toString()
            notes += "foreground_package=$foreground"
            if (foreground !in setOf("com.google.android.apps.nexuslauncher", "com.android.launcher3")) {
                notes += "status=NOT_CAPTURED; reason=launcher_not_foreground"
                return
            }
            verifiedLauncherPackage = foreground
            val classicStillBound = classicIds.all { it in manager.getAppWidgetIds(classicProvider) }
            val timedStillBound = timeId in manager.getAppWidgetIds(timeProvider)
            notes += "bound_classics=${classicIds.size}; expected=3"
            if (!classicStillBound || !timedStillBound) {
                notes += "status=NOT_CAPTURED; reason=widget_binding_lost"
                return
            }
            val launcherTree = instrumentation.uiAutomation.rootInActiveWindow
            val classicVisible = launcherTree?.findAccessibilityNodeInfosByText(day.title)
                ?.any { it.isVisibleToUser } == true
            val timedVisible = launcherTree?.findAccessibilityNodeInfosByText(timed.title)
                ?.any { it.isVisibleToUser } == true
            notes += "classic_title_visible=$classicVisible"
            notes += "timed_title_visible=$timedVisible"
            // Compare authentic launcher layouts, never edit image pixels.
            capture("7-launcher-before-arrangement-review-only")
            val classicDrag = dragHomeWidget(110f, 224f, 335f, 780f)
            val timedDrag = dragHomeWidget(320f, 354f, 660f, 735f)
            notes += "classic_drag_injected=$classicDrag"
            notes += "timed_drag_injected=$timedDrag"
            shell("input keyevent KEYCODE_HOME")
            SystemClock.sleep(900)
            capture("7-launcher-review-only")
            notes += if (classicVisible && timedVisible) {
                "status=CAPTURED_REVIEW_ONLY; both_titles_visible=true"
            } else {
                "status=CAPTURED_UNVERIFIED; both_titles_visible=false"
            }
            notes += "required=human_pixel_review_for_all_four_widgets"
        } catch (failure: Exception) {
            notes += "status=NOT_CAPTURED; reason=${failure.javaClass.simpleName}"
            notes += "detail=${failure.message.orEmpty().replace('\n', ' ').take(180)}"
        } finally {
            // The launcher exists only in the disposable CI emulator. Do not
            // alter any user's real device or the canonical store screenshots.
            verifiedLauncherPackage?.let { launcher ->
                val cleanup = runCatching { shell("pm clear $launcher") }.getOrDefault("failed")
                notes += "launcher_cleanup=$cleanup"
            }
            repository.save(previous)
            prefs.edit().apply {
                if (hadTheme) putString("theme", previousTheme) else remove("theme")
            }.commit()
            defaults.save(oldDefaults)
            locale.applicationLocales = oldLocales
            runCatching {
                shell(if (densityOverride != null) "wm density $densityOverride" else "wm density reset")
            }
            file.writeText(notes.joinToString("\n", postfix = "\n"))
        }
    }

    // Input gestures move real launcher widgets. Successful injection does
    // not prove placement; the native screenshot must be reviewed separately.
    private fun dragHomeWidget(fromX: Float, fromY: Float, toX: Float, toY: Float): Boolean {
        val automation = instrumentation.uiAutomation
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, y: Float): Boolean {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            return try {
                automation.injectInputEvent(event, true)
            } finally {
                event.recycle()
            }
        }
        var success = send(MotionEvent.ACTION_DOWN, fromX, fromY)
        SystemClock.sleep(850)
        for (step in 1..14) {
            val fraction = step.toFloat() / 14f
            success = send(
                MotionEvent.ACTION_MOVE,
                fromX + (toX - fromX) * fraction,
                fromY + (toY - fromY) * fraction,
            ) && success
            SystemClock.sleep(48)
        }
        success = send(MotionEvent.ACTION_UP, toX, toY) && success
        SystemClock.sleep(700)
        return success
    }

    private fun requestLauncherWidget(
        provider: ComponentName, notes: MutableList<String>, timed: Boolean
    ): Int? {
        val manager = AppWidgetManager.getInstance(app)
        val existing = manager.getAppWidgetIds(provider).toSet()
        var requested = false
        ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)).use { scenario ->
            drain()
            scenario.onActivity { activity ->
                requested = manager.requestPinAppWidget(provider, null, null)
            }
            notes += "pin_requested_${provider.shortClassName}=$requested"
            if (!requested) return null
            val approved = clickLauncherLabel(
                listOf("add", "add automatically", "add to home screen", "add widget"),
                exact = true, notes = notes,
            )
            notes += "pin_approved_${provider.shortClassName}=$approved"
            if (timed) {
                val selected = clickLauncherLabel(listOf("Concert night"),
                    exact = false, notes = notes)
                notes += "time_selection_clicked=$selected"
                val saved = clickLauncherLabel(listOf("save"),
                    exact = true, notes = notes)
                notes += "time_config_saved=$saved"
            }
        }
        repeat(12) {
            val added = manager.getAppWidgetIds(provider).firstOrNull { id -> id !in existing }
            if (added != null) return added
            SystemClock.sleep(400)
        }
        return null
    }

    private fun clickLauncherLabel(
        wanted: List<String>, exact: Boolean, notes: MutableList<String>
    ): Boolean {
        var observed: List<String> = emptyList()
        repeat(14) {
            SystemClock.sleep(450)
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: return@repeat
            val queue = java.util.ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
            queue.add(root)
            val labels = mutableListOf<String>()
            while (!queue.isEmpty()) {
                val node = queue.removeFirst()
                val label = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
                if (label.isNotBlank()) labels += label
                if (wanted.any { expected ->
                        if (exact) label.equals(expected, ignoreCase = true)
                        else label.contains(expected, ignoreCase = true)
                    }) {
                    var clickable: android.view.accessibility.AccessibilityNodeInfo? = node
                    while (clickable != null && !clickable.isClickable) clickable = clickable.parent
                    if (clickable != null && clickable.performAction(
                            android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return true
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.addLast(it) }
                }
            }
            observed = labels.take(25)
        }
        notes += "unmatched_ui_labels=${observed.joinToString(" | ").take(360)}"
        return false
    }

    @IsolatedStoreScreenshot
    @Test fun captureSixCandidateViewsForHumanReview() {
        assumeTrue(
            "Store captures are isolated from behavioral instrumentation",
            InstrumentationRegistry.getArguments().getString("countawayScreenshotReview") == "true",
        )
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
            // Use a more legible full-screen display density for genuine review
            // captures. This changes emulator settings, never screenshot pixels.
            // The original density is restored in finally.
            shell("wm density 280")
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
                    val language = activity.findViewById<View>(R.id.languageButton)
                    language.clearFocus()
                    activity.window.decorView.isFocusableInTouchMode = true
                    activity.window.decorView.requestFocus()
                    prepare(activity.window.decorView)
                }
                capture("1-home-dark")
            }

            // The timed-event editor uses real release-candidate data and a
            // neutral focus state; no input caret or soft keyboard on screenshots.
            repository.save(homeEvents + concert)
            drain()
            ActivityScenario.launch<EditorActivity>(
                Intent(app, EditorActivity::class.java)
                    .putExtra(EditorActivity.EXTRA_DUPLICATE_EVENT_ID, concert.id)
                    .putExtra(EditorActivity.EXTRA_SOURCE_REVISION, EventRevision.of(concert))
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
                    val timeButton = activity.findViewById<View>(R.id.timeButton)
                    // Request natural Android scrolling to the actual control,
                    // rather than assuming a fixed offset works on every density.
                    timeButton.requestRectangleOnScreen(
                        Rect(0, 0, timeButton.width, timeButton.height), true
                    )
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    assertEntirelyVisible(activity.findViewById(R.id.editorHeading),
                        "Editor heading")
                    assertEntirelyVisible(activity.findViewById(R.id.timeButton),
                        "Editor optional time")
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.deleteButton).visibility)
                    assertEntirelyVisible(activity.findViewById(R.id.saveButton),
                        "Editor draft save")
                }
                capture("2-editor-timed")
            }
            repository.save(homeEvents)
            drain()

            // Capture the About screen before entering widget setup so no
            // transient platform window layer from the chooser affects its colors.
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
                SystemClock.sleep(3_000)
                capture("4-about-v125")
            }

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
                SystemClock.sleep(1_500)
                capture("3-classic-widget-style")
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
                    val language = activity.findViewById<View>(R.id.languageButton)
                    language.clearFocus()
                    activity.window.decorView.isFocusableInTouchMode = true
                    activity.window.decorView.requestFocus()
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
                "versionName=1.2.5\nversionCode=14\nsource=real Android API36 UI at 1080x1920, density=280\n" +
                "status=UNAPPROVED; no cropping, editing, or pixel reconstruction\n" +
                "slots=1 dark Home; 2 unsaved timed-event copy draft; 3 classic pin-style chooser; " +
                "4 About; 5 light Home; 6 time-widget configuration ALTERNATIVE, NOT launcher\n" +
                "No canonical Fastlane screenshot was replaced. Human review required.\n"
            )
            val geometryReport = File(app.filesDir,
                "layout-evidence/release-review/VISIBILITY_FINDINGS.txt")
            geometryReport.writeText(
                if (visibilityFindings.isEmpty()) "PASS: all checked controls fully visible\n"
                else visibilityFindings.joinToString(separator = "\n", postfix = "\n")
            )
            // Retain all six real review PNGs and their detailed geometry
            // even when a UI is clipped. The overall test still fails closed.
            assertTrue(
                "Review capture has clipped controls: " + visibilityFindings.joinToString(" | "),
                visibilityFindings.isEmpty(),
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
        val visibleBounds = Rect()
        val hasVisibleRect = view.getGlobalVisibleRect(visibleBounds)
        if (hasVisibleRect &&
            visibleBounds.width() == view.width &&
            visibleBounds.height() == view.height
        ) return
        val origin = IntArray(2)
        view.getLocationOnScreen(origin)
        val parentFrames = mutableListOf<String>()
        var ancestor = view.parent
        for (level in 0 until 4) {
            val parentView = ancestor as? View ?: break
            val bounds = Rect()
            parentView.getGlobalVisibleRect(bounds)
            parentFrames +=
                "${parentView.javaClass.simpleName}(height=${parentView.height},visible=${bounds.height()},top=${bounds.top},bottom=${bounds.bottom})"
            ancestor = parentView.parent
        }
        visibilityFindings +=
            "$label: measured=${view.width}x${view.height}, visible=" +
                "${visibleBounds.width()}x${visibleBounds.height()}, " +
                "origin=(${origin[0]},${origin[1]}), " +
                "visibleBounds=$visibleBounds; parents=${parentFrames.joinToString(" > ")}"
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
            // Preserve original failing evidence before enforcing color fidelity.
            if (name == "3-classic-widget-style" || name == "4-about-v125") {
                val actual = bitmap.getPixel(10, 700)
                val expected = Color.rgb(6, 17, 38)
                File(app.filesDir, "layout-evidence/release-review/DARK_CAPTURE_BACKDROP.txt")
                    .appendText("$name actual=${Integer.toHexString(actual)} expected=${Integer.toHexString(expected)}\n")
                assertEquals("$name has an unexpected window tint", expected, actual)
            }
        } finally {
            bitmap.recycle()
        }
    }
}
