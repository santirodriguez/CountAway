package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.widget.CheckedTextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import org.hamcrest.Matchers.startsWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeChooserInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = context.getSharedPreferences("countaway_ui", Context.MODE_PRIVATE)

    @Test
    fun appearanceChoiceShowsIconsSystemModeAndPersistsSelection() = withThemeState {
        preferences.edit().putString("theme", "DARK").commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var system: String
            lateinit var light: String
            lateinit var dark: String
            lateinit var effectiveTheme: String
            scenario.onActivity {
                system = it.getString(R.string.widget_appearance_system)
                light = it.getString(R.string.widget_appearance_light)
                dark = it.getString(R.string.widget_appearance_dark)
                val systemIsDark = (it.applicationContext.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val systemLabel = if (systemIsDark) dark else light
                effectiveTheme = it.getString(R.string.theme_system_current, systemLabel)
            }
            onView(withId(R.id.themeButton)).perform(click())
            onView(withText("$system\n$effectiveTheme")).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(dark)).inRoot(isDialog()).check(matches(isChecked()))
            for (label in listOf(light, dark)) {
                onView(withText(label)).inRoot(isDialog()).check { view, error ->
                    if (error != null) throw error
                    assertTrue(view is CheckedTextView)
                    assertTrue((view as CheckedTextView).compoundDrawablesRelative[0] != null)
                }
            }
            onView(withText(light)).inRoot(isDialog()).perform(click())
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertEquals(ThemeManager.AppTheme.LIGHT, ThemeManager.currentTheme(it)) }
            onView(withId(R.id.themeButton)).perform(click())
            onView(withText(light)).inRoot(isDialog()).check(matches(isChecked()))
            onView(withText(startsWith("$system\n"))).inRoot(isDialog()).perform(click())
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertEquals(ThemeManager.AppTheme.SYSTEM, ThemeManager.currentTheme(it)) }
        }
    }

    @Test
    fun cancelRestoresFocusWithoutChangingTheme() = withThemeState {
        preferences.edit().putString("theme", "DARK").commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.themeButton)).perform(click())
            pressBack()
            val deadline = SystemClock.uptimeMillis() + 2_000
            var focused = false
            while (!focused && SystemClock.uptimeMillis() < deadline) {
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    focused = it.findViewById<android.view.View>(R.id.themeButton).hasFocus()
                    assertEquals(ThemeManager.AppTheme.DARK, ThemeManager.currentTheme(it))
                }
                if (!focused) SystemClock.sleep(50)
            }
            assertTrue(focused)
            onView(withId(R.id.themeButton)).check(matches(hasFocus()))
        }
    }

    private fun withThemeState(block: () -> Unit) {
        val existed = preferences.contains("theme")
        val previous = preferences.getString("theme", null)
        try {
            block()
        } finally {
            preferences.edit().apply {
                if (existed) putString("theme", previous) else remove("theme")
            }.commit()
        }
    }
}
