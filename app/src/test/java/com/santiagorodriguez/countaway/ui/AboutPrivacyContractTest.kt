package com.santiagorodriguez.countaway.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AboutPrivacyContractTest {
    @Test
    fun helpExposesStableProjectAndPrivacyLinks() {
        val support = File("src/main/res/layout/view_about_support.xml").readText()
        val strings = File("src/main/res/values/about_polish_strings.xml").readText()
        val activity = File("src/main/java/com/santiagorodriguez/countaway/ui/AboutActivity.kt").readText()
        val policy = File("../PRIVACY.md").readText()

        assertTrue(support.contains("android:id=\"@+id/projectLinksCard\""))
        assertTrue(support.contains("android:id=\"@+id/projectLinksRow\""))
        assertTrue(support.contains("android:id=\"@+id/websiteButton\""))
        assertTrue(support.contains("android:id=\"@+id/privacyButton\""))
        assertTrue(support.contains("android:id=\"@+id/aboutAuthorCredit\""))
        assertTrue(support.contains("@string/about_author_name"))
        assertFalse(support.contains("@string/about_project_links_title"))
        assertTrue(support.contains("android:drawableStart=\"@drawable/ic_about_website\""))
        assertTrue(support.contains("android:drawableStart=\"@drawable/ic_about_privacy\""))
        assertTrue(File("src/main/res/drawable/ic_about_website.xml").readText().contains("android:pathData"))
        assertTrue(File("src/main/res/drawable/ic_about_privacy.xml").readText().contains("android:pathData"))
        assertTrue(support.contains("@string/about_website"))
        assertTrue(support.contains("@string/about_privacy_policy"))
        assertFalse(support.contains("countaway.cajapersonal.org"))
        assertTrue(strings.contains("<string name=\"about_website\">Website</string>"))
        assertTrue(File("src/main/res/values-es/about_polish_strings.xml").readText()
            .contains("<string name=\"about_website\">Sitio web</string>"))
        assertTrue(File("src/main/res/values-ca/about_polish_strings.xml").readText()
            .contains("<string name=\"about_website\">Lloc web</string>"))
        assertTrue(strings.contains("https://countaway.cajapersonal.org/privacy/"))
        assertTrue(activity.contains("R.id.websiteButton"))
        assertTrue(activity.contains("PROJECT_WEBSITE = \"https://countaway.cajapersonal.org/\""))
        assertTrue(activity.contains("R.id.privacyButton"))
        assertTrue(activity.contains("R.string.privacy_policy_url"))
        assertTrue(policy.contains("# CountAway Privacy Policy"))
        assertTrue(policy.contains("## English"))
        assertTrue(policy.contains("## Español"))
        assertTrue(policy.contains("## Català"))
        assertFalse(policy.contains("Google Play"))
    }
}
