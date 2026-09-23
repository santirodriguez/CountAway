package com.santiagorodriguez.countaway.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AboutHelpLayoutContractTest {
    @Test
    fun helpStepsUseFlexibleHeightAndDedicatedTitleSubtitleCopy() {
        val xml = File("src/main/res/layout/activity_about.xml").readText()
        val start = xml.indexOf("android:id=\"@+id/createCountdownAction\"")
        val end = xml.indexOf("android:id=\"@+id/exportButton\"")
        val help = xml.substring(start, end)

        assertFalse(help.contains("android:layout_height=\"54dp\""))
        assertTrue(help.contains("android:minHeight=\"64dp\""))
        assertTrue(help.contains("@string/about_create_countdown_note"))
        assertTrue(help.contains("@string/about_add_widget_note"))
        assertTrue(help.contains("android:id=\"@+id/stopCheckingIcon\""))
        assertTrue(help.contains("android:gravity=\"start\""))
    }
}
