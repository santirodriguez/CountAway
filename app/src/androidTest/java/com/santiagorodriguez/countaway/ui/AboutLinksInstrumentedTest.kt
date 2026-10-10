package com.santiagorodriguez.countaway.ui

import android.content.Intent
import android.widget.Button
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutLinksInstrumentedTest {
    @Test fun websiteAndPrivacyUseTheSameGroupedCardAndReadableButtons() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<AboutActivity>(Intent(context, AboutActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                val card = activity.findViewById<LinearLayout>(R.id.projectLinksCard)
                val row = activity.findViewById<LinearLayout>(R.id.projectLinksRow)
                val website = activity.findViewById<Button>(R.id.websiteButton)
                val privacy = activity.findViewById<Button>(R.id.privacyButton)

                assertSame(card, row.parent)
                assertSame(row, website.parent)
                assertSame(row, privacy.parent)
                assertNotNull(card.background)
                assertEquals(activity.getString(R.string.about_website), website.text.toString())
                assertEquals(activity.getString(R.string.about_privacy_policy), privacy.text.toString())
                assertFalse(website.text.contains("cajapersonal.org"))

                val minTouchSize = (48 * activity.resources.displayMetrics.density).toInt()
                for (button in listOf(website, privacy)) {
                    assertTrue(button.isClickable)
                    assertTrue(button.isFocusable)
                    assertTrue(button.minimumHeight >= minTouchSize)
                    val params = button.layoutParams as LinearLayout.LayoutParams
                    assertEquals(0, params.width)
                    assertEquals(1f, params.weight, 0f)
                }
            }
        }
    }
}
