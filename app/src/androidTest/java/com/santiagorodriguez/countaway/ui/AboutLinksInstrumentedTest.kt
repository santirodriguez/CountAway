package com.santiagorodriguez.countaway.ui

import android.content.Intent
import android.graphics.Bitmap
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AboutLinksInstrumentedTest {
    @Test fun websiteAndPrivacyAreCompactActionsBelowSupport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ActivityScenario.launch<AboutActivity>(Intent(context, AboutActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                val card = activity.findViewById<LinearLayout>(R.id.projectLinksCard)
                val row = activity.findViewById<LinearLayout>(R.id.projectLinksRow)
                val website = activity.findViewById<Button>(R.id.websiteButton)
                val privacy = activity.findViewById<Button>(R.id.privacyButton)
                val attribution = activity.findViewById<TextView>(R.id.aboutAuthorCredit)

                assertSame(card, row.parent)
                assertEquals(1, card.childCount)
                assertSame(row, card.getChildAt(0))
                assertSame(row, website.parent)
                assertSame(row, privacy.parent)
                assertSame(card.parent, attribution.parent)
                val parent = card.parent as LinearLayout
                assertTrue(parent.indexOfChild(attribution) > parent.indexOfChild(card))
                assertEquals(activity.getString(R.string.about_author_name), attribution.text.toString())
                val installedVersion = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
                assertEquals(activity.getString(R.string.about_version_compact, installedVersion),
                    activity.findViewById<TextView>(R.id.versionText).text.toString())
                assertEquals(Gravity.CENTER_HORIZONTAL, attribution.gravity and Gravity.HORIZONTAL_GRAVITY_MASK)

                assertNotNull(card.background)
                assertEquals(activity.getString(R.string.about_website), website.text.toString())
                assertEquals(activity.getString(R.string.about_privacy_policy), privacy.text.toString())
                assertFalse(website.text.contains("cajapersonal.org"))

                val density = activity.resources.displayMetrics.density
                val minTouchSize = (48 * density).toInt()
                for (button in listOf(website, privacy)) {
                    assertTrue(button.isClickable)
                    assertTrue(button.isFocusable)
                    assertTrue(button.minimumHeight >= minTouchSize)
                    val icon = button.compoundDrawablesRelative[0] ?: throw AssertionError("Missing action icon")
                    assertTrue(icon.intrinsicWidth in 1..(20 * density).toInt())
                    assertTrue(icon.intrinsicHeight in 1..(20 * density).toInt())
                    val params = button.layoutParams as LinearLayout.LayoutParams
                    assertEquals(0, params.width)
                    assertEquals(1f, params.weight, 0f)
                }
            }
            instrumentation.waitForIdleSync()
            val evidenceDir = File(context.filesDir, "layout-evidence").apply { mkdirs() }
            val captured = runCatching {
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                    ?: error("UiAutomation screenshot unavailable")
                try {
                    File(evidenceDir, "candidate-about-preview.png").outputStream().use { stream ->
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            captured.exceptionOrNull()?.let { failure ->
                File(evidenceDir, "candidate-about-preview-unavailable.txt")
                    .writeText("screenshot_status=unavailable type=${failure.javaClass.simpleName}\n")
            }
        }
    }
}
