package com.santiagorodriguez.countaway.ui

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.view.KeyEvent
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import org.hamcrest.Matchers.startsWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LanguageChooserInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = context.getSharedPreferences("countaway_ui", Context.MODE_PRIVATE)

    @Test
    fun platformChoiceConsumesLegacyBeforeSystemReset() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        withLanguageState {
            val manager = context.getSystemService(LocaleManager::class.java)
            preferences.edit().putString("language", "en").commit()
            manager.applicationLocales = LocaleList.forLanguageTags("es")
            LanguageManager.syncPlatformLocale(context)
            assertEquals("es", LanguageManager.currentLanguageTag(context))
            assertFalse(preferences.contains("language"))
            manager.applicationLocales = LocaleList.getEmptyLocaleList()
            LanguageManager.syncPlatformLocale(context)
            assertTrue(manager.applicationLocales.isEmpty)
            assertTrue(LanguageManager.isFollowingSystem(context))
            assertEquals(LanguageManager.systemLanguageTag(context), LanguageManager.currentLanguageTag(context))
        }
    }

    @Test
    fun legacyChoiceMigratesOnceAndRegionalPlatformChoiceIsPreserved() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        withLanguageState {
            val manager = context.getSystemService(LocaleManager::class.java)
            manager.applicationLocales = LocaleList.getEmptyLocaleList()
            preferences.edit().putString("language", "ca-ES").commit()
            LanguageManager.syncPlatformLocale(context)
            assertEquals("ca", manager.applicationLocales.toLanguageTags())
            assertFalse(preferences.contains("language"))
            preferences.edit().putString("language", "en").commit()
            manager.applicationLocales = LocaleList.forLanguageTags("es-AR")
            LanguageManager.syncPlatformLocale(context)
            assertEquals("es", manager.applicationLocales.toLanguageTags())
            assertFalse(preferences.contains("language"))
        }
    }

    @Test
    fun systemChoiceIgnoresAnAppLocalizedContext() {
        val expected = LanguageManager.systemLanguageTag(context)
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale("ca")) }
        val wrapped = context.createConfigurationContext(config)
        assertEquals(expected, LanguageManager.systemLanguageTag(wrapped))
    }

    @Test
    fun clickLongPressAndKeyboardOpenTheSameChooserWithoutWritingOnCancel() = withLanguageState {
        selectEnglish()
        val before = preferences.all.toMap()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val button = activity.findViewById<Button>(R.id.languageButton)
                assertEquals(1, button.compoundDrawablesRelative.count { it != null })
                assertTrue(button.performLongClick())
            }
            onView(withText("Choose language")).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText("Español")).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText("Català")).inRoot(isDialog()).check(matches(isDisplayed()))
            pressBack()
            onView(withId(R.id.languageButton)).perform(click())
            onView(withText("Choose language")).inRoot(isDialog()).check(matches(isDisplayed()))
            pressBack()
            // Enter real keyboard-navigation mode before requesting keyboard focus.
            instrumentation.setInTouchMode(false)
            scenario.onActivity { assertTrue(it.findViewById<Button>(R.id.languageButton).requestFocus()) }
            onView(withId(R.id.languageButton)).check(matches(hasFocus()))
            onView(withId(R.id.languageButton)).perform(pressKey(KeyEvent.KEYCODE_DPAD_CENTER))
            onView(withText("Choose language")).inRoot(isDialog()).check(matches(isDisplayed()))
            pressBack()
            scenario.onActivity {
                assertEquals("en", LanguageManager.currentLanguageTag(it))
                assertFalse(LanguageManager.isFollowingSystem(it))
            }
        }
        assertEquals(before, preferences.all)
    }

    @Test
    fun explicitSameLanguageIsNoOpAndAllLanguagesAndSystemAreReachable() = withLanguageState {
        selectEnglish()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var original: MainActivity? = null
            scenario.onActivity { original = it }
            onView(withId(R.id.languageButton)).perform(click())
            onView(withText("English · In use")).inRoot(isDialog()).perform(click())
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertSame(original, it)
                assertFalse(LanguageManager.isFollowingSystem(it))
            }
            for ((name, tag) in listOf("Español" to "es", "Català" to "ca", "English" to "en")) {
                onView(withId(R.id.languageButton)).perform(click())
                onView(withText(name)).inRoot(isDialog()).perform(click())
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertEquals(tag, LanguageManager.currentLanguageTag(it)) }
            }
            onView(withId(R.id.languageButton)).perform(click())
            onView(withText(startsWith("Use system language"))).inRoot(isDialog()).perform(click())
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertTrue(LanguageManager.isFollowingSystem(it)) }
        }
    }

    private fun selectEnglish() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            preferences.edit().remove("language").commit()
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en")
        } else {
            preferences.edit().putString("language", "en").commit()
        }
    }

    private fun withLanguageState(block: () -> Unit) {
        val hadLanguage = preferences.contains("language")
        val originalLanguage = preferences.getString("language", null)
        val originalLocales = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales
        } else null
        try {
            block()
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && originalLocales != null) {
                context.getSystemService(LocaleManager::class.java).applicationLocales = originalLocales
            }
            preferences.edit().apply {
                if (hadLanguage) putString("language", originalLanguage) else remove("language")
            }.commit()
        }
    }
}
