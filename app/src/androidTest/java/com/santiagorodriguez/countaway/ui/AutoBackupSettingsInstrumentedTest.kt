package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.filters.SdkSuppress
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.santiagorodriguez.countaway.backup.AutoBackupEngine
import com.santiagorodriguez.countaway.backup.AutoBackupFiles
import com.santiagorodriguez.countaway.backup.AutoBackupScheduler
import com.santiagorodriguez.countaway.backup.AutoBackupStore
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.ScrollView
import java.io.File
import java.util.Locale
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoBackupSettingsInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun settingsRemainScrollableWithLargeTextInAllThreeLanguagesAndThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val scales = listOf(1.3f, 2f)
            for (language in listOf("en", "es", "ca")) for (dark in listOf(false, true)) {
                for (scale in scales) for (widthDp in listOf(320, 390)) {
                    val config = Configuration(context.resources.configuration).apply {
                        setLocale(Locale.forLanguageTag(language))
                        fontScale = scale
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                            if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    }
                    val themed = ContextThemeWrapper(
                        context.createConfigurationContext(config), context.applicationInfo.theme)
                    val root = LayoutInflater.from(themed)
                        .inflate(R.layout.activity_auto_backup_settings, null) as ScrollView
                    val density = themed.resources.displayMetrics.density
                    val w = (widthDp * density).toInt()
                    val h = (520 * density).toInt()
                    root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, w, h)
                    val child = root.getChildAt(0)
                    assertTrue("Settings content wider than viewport", child.measuredWidth <= w)
                    for (id in listOf(R.id.autoBackupDefault, R.id.autoBackupChoose)) {
                        val button = root.findViewById<Button>(id)
                        assertTrue("Target too short for touch", button.height >= (48 * density).toInt())
                        assertTrue("Target exceeds viewport width", button.width <= w - (40 * density).toInt())
                    }
                    assertTrue("Privacy notes must be scrollable", child.height > root.height)
                    root.scrollTo(0, child.height - root.height)
                    val image = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    try {
                        // Detached View renders lack a real window surface: fill the actual
                        // themed background before drawing diagnostic pixels.
                        val canvas = Canvas(image)
                        canvas.drawColor(themed.getColor(R.color.background))
                        root.draw(canvas)
                        val name = "auto-backup-$language-$dark-$scale-$widthDp.png"
                        val file = File(context.filesDir, "layout-evidence/$name")
                        file.parentFile!!.mkdirs()
                        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } finally { image.recycle() }
                }
            }
        }
    }

    @Test fun actualSettingsWindowRendersReadableInLightAndDarkThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appearance = context.getSharedPreferences("countaway_ui", Context.MODE_PRIVATE)
        val originalTheme = appearance.getString("theme", null)
        val folder = context.getSharedPreferences("countaway_auto_backups", Context.MODE_PRIVATE)
        val originalBackup = folder.getString("configuration_v1", null)
        try {
            folder.edit().remove("configuration_v1").commit()
            for (name in listOf("DARK", "LIGHT")) {
                appearance.edit().putString("theme", name).commit()
                ActivityScenario.launch<AutoBackupSettingsActivity>(
                    Intent(context, AutoBackupSettingsActivity::class.java)
                ).use { scenario ->
                    scenario.onActivity { screen ->
                        assertEquals(screen.getString(R.string.auto_backup_off),
                            screen.findViewById<TextView>(R.id.autoBackupStatus).text.toString())
                        assertTrue(screen.findViewById<View>(R.id.autoBackupDefault).isShown)
                        assertTrue(screen.findViewById<View>(R.id.autoBackupChoose).isShown ||
                            screen.findViewById<ScrollView>(R.id.autoBackupRoot).getChildAt(0)
                                .height > screen.findViewById<ScrollView>(R.id.autoBackupRoot).height)
                    }
                    instrumentation.waitForIdleSync()
                    saveActualWindow(instrumentation, "auto-backup-window-$name-top.png")
                    scenario.onActivity { screen ->
                        screen.findViewById<ScrollView>(R.id.autoBackupRoot)
                            .fullScroll(View.FOCUS_DOWN)
                    }
                    instrumentation.waitForIdleSync()
                    saveActualWindow(instrumentation, "auto-backup-window-$name-bottom.png")
                }
            }
        } finally {
            val restoreTheme = appearance.edit()
            if (originalTheme == null) restoreTheme.remove("theme")
            else restoreTheme.putString("theme", originalTheme)
            restoreTheme.commit()
            val restoreFolder = folder.edit()
            if (originalBackup == null) restoreFolder.remove("configuration_v1")
            else restoreFolder.putString("configuration_v1", originalBackup)
            restoreFolder.commit()
        }
    }

    private fun saveActualWindow(
        instrumentation: android.app.Instrumentation,
        name: String,
    ) {
        // ActivityScenario can report RESUMED before Android draws its first
        // frame. Retain the original screenshot of every rejected attempt;
        // never accept a blank OS surface as visual evidence of a usable UI.
        val evidence = File(context.filesDir, "layout-evidence")
        evidence.mkdirs()
        var lastDistinctColors = 0
        repeat(4) { attempt ->
            instrumentation.waitForIdleSync()
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) {
                "Cannot capture actual Android settings window"
            }
            try {
                val colors = HashSet<Int>()
                for (y in bitmap.height / 5 until bitmap.height * 17 / 20
                    step maxOf(1, bitmap.height / 35)) {
                    for (x in bitmap.width / 10 until bitmap.width * 9 / 10
                        step maxOf(1, bitmap.width / 35)) {
                        colors += bitmap.getPixel(x, y) and 0x00ffffff
                    }
                }
                lastDistinctColors = colors.size
                val usable = lastDistinctColors >= 8
                val file = File(evidence,
                    if (usable) name else "$name.blank-attempt-${attempt + 1}.png")
                file.outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                if (usable) return
            } finally {
                bitmap.recycle()
            }
            Thread.sleep(150)
        }
        fail("Native window remained blank after four captures: $name; " +
            "distinct colors=$lastDistinctColors")
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test fun verifiedOptInShowsEnabledStateAndDisableKeepsTheActualBackup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val preferences = context.getSharedPreferences("countaway_auto_backups", Context.MODE_PRIVATE)
        val original = preferences.getString("configuration_v1", null)
        val appearance = context.getSharedPreferences("countaway_ui", Context.MODE_PRIVATE)
        val originalTheme = appearance.getString("theme", null)
        val repository = CountdownRepository(context)
        val initialEvents = (repository.loadResult() as CountdownLoadResult.Success).events
        var savedUri: Uri? = null
        try {
            AutoBackupScheduler.cancel(context)
            preferences.edit().remove("configuration_v1").commit()
            appearance.edit().putString("theme", "DARK").commit()
            repository.save(emptyList())
            AutoBackupEngine.enable(context,
                AutoBackupStore.Destination(AutoBackupStore.Kind.DEFAULT_DOWNLOADS))
            val copy = AutoBackupStore.read(context).owned.single()
            savedUri = Uri.parse(copy.uri)
            assertTrue(AutoBackupFiles.verify(context, requireNotNull(savedUri), copy.sha256))
            ActivityScenario.launch<AutoBackupSettingsActivity>(
                Intent(context, AutoBackupSettingsActivity::class.java)
            ).use { scenario ->
                scenario.onActivity { activity ->
                    assertEquals(activity.getString(R.string.auto_backup_on),
                        activity.findViewById<TextView>(R.id.autoBackupStatus).text.toString())
                    assertEquals(View.VISIBLE,
                        activity.findViewById<View>(R.id.autoBackupNow).visibility)
                    assertEquals(View.VISIBLE,
                        activity.findViewById<View>(R.id.autoBackupDisable).visibility)
                    assertTrue(activity.findViewById<TextView>(R.id.autoBackupDestination)
                        .text.toString().contains("Downloads/CountAway"))
                }
                instrumentation.waitForIdleSync()
                saveActualWindow(instrumentation, "auto-backup-window-verified-DARK.png")
                scenario.onActivity { activity ->
                    activity.findViewById<Button>(R.id.autoBackupDisable).performClick()
                }
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                scenario.onActivity { activity ->
                    assertEquals(activity.getString(R.string.auto_backup_off),
                        activity.findViewById<TextView>(R.id.autoBackupStatus).text.toString())
                }
            }
            assertFalse(AutoBackupStore.read(context).enabled)
            assertTrue("Opting out must preserve the latest verified file",
                AutoBackupFiles.verify(context, requireNotNull(savedUri), copy.sha256))
        } finally {
            AutoBackupScheduler.cancel(context)
            savedUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            repository.save(initialEvents)
            val edit = preferences.edit()
            if (original == null) edit.remove("configuration_v1")
            else edit.putString("configuration_v1", original)
            edit.commit()
            val appearanceEdit = appearance.edit()
            if (originalTheme == null) appearanceEdit.remove("theme")
            else appearanceEdit.putString("theme", originalTheme)
            appearanceEdit.commit()
        }
    }

    @SdkSuppress(maxSdkVersion = 28)
    @Test fun legacyDefaultRequiresExplicitPickerAndCancelKeepsBackupsOff() {
        val preferences = context.getSharedPreferences("countaway_auto_backups", Context.MODE_PRIVATE)
        val old = preferences.getString("configuration_v1", null)
        try {
            preferences.edit().remove("configuration_v1").commit()
            ActivityScenario.launch<AutoBackupSettingsActivity>(
                Intent(context, AutoBackupSettingsActivity::class.java)
            ).use { scenario ->
                scenario.onActivity { screen ->
                    screen.findViewById<Button>(R.id.autoBackupDefault).performClick()
                }
                onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
                scenario.onActivity { screen ->
                    assertEquals(screen.getString(R.string.auto_backup_off),
                        screen.findViewById<TextView>(R.id.autoBackupStatus).text.toString())
                }
                assertFalse(AutoBackupStore.read(context).enabled)
            }
        } finally {
            val edit = preferences.edit()
            if (old == null) edit.remove("configuration_v1")
            else edit.putString("configuration_v1", old)
            edit.commit()
        }
    }

    @Test fun settingsExposeBothDestinationsButStayOffUntilUserOptsIn() {
        val prefs = context.getSharedPreferences("countaway_auto_backups", Context.MODE_PRIVATE)
        val original = prefs.getString("configuration_v1", null)
        prefs.edit().remove("configuration_v1").commit()
        try {
            ActivityScenario.launch<AutoBackupSettingsActivity>(
                Intent(context, AutoBackupSettingsActivity::class.java)
            ).use { scenario ->
                scenario.onActivity { screen ->
                    assertEquals(screen.getString(R.string.auto_backup_off),
                        screen.findViewById<TextView>(R.id.autoBackupStatus).text.toString())
                    assertTrue(screen.findViewById<Button>(R.id.autoBackupDefault).isEnabled)
                    assertTrue(screen.findViewById<Button>(R.id.autoBackupChoose).isEnabled)
                    assertEquals(View.GONE, screen.findViewById<View>(R.id.autoBackupNow).visibility)
                    assertEquals(View.GONE, screen.findViewById<View>(R.id.autoBackupDisable).visibility)
                    assertNotNull(screen.findViewById<View>(R.id.autoBackupBack).contentDescription)
                }
            }
        } finally {
            val edit = prefs.edit()
            if (original == null) edit.remove("configuration_v1")
            else edit.putString("configuration_v1", original)
            edit.commit()
        }
    }
}
