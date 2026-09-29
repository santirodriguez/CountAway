package com.santiagorodriguez.countaway.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AboutPrivacyContractTest {
    @Test
    fun helpExposesStableNeutralPrivacyPolicy() {
        val support = File("src/main/res/layout/view_about_support.xml").readText()
        val strings = File("src/main/res/values/about_polish_strings.xml").readText()
        val activity = File("src/main/java/com/santiagorodriguez/countaway/ui/AboutActivity.kt").readText()
        val policy = File("../PRIVACY.md").readText()

        assertTrue(support.contains("android:id=\"@+id/privacyButton\""))
        assertTrue(support.contains("@string/about_privacy_policy"))
        assertTrue(strings.contains("https://github.com/santirodriguez/CountAway/blob/main/PRIVACY.md"))
        assertTrue(activity.contains("R.id.privacyButton"))
        assertTrue(activity.contains("R.string.privacy_policy_url"))
        assertTrue(policy.contains("# CountAway Privacy Policy"))
        assertTrue(policy.contains("## English"))
        assertTrue(policy.contains("## Español"))
        assertTrue(policy.contains("## Català"))
        assertFalse(policy.contains("Google Play"))
    }
}
