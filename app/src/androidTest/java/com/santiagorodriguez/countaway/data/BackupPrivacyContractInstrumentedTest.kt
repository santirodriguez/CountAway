package com.santiagorodriguez.countaway.data

import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class BackupPrivacyContractInstrumentedTest {
    @Test
    fun cloudAndDeviceTransferRulesExcludeAllCountAwayStorageRoots() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)

        assertEquals(0, applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)

        val expectedDomains = setOf("root", "device_root", "external")
        val exclusions = mutableMapOf(
            "cloud-backup" to mutableSetOf<String>(),
            "device-transfer" to mutableSetOf<String>(),
        )
        var section: String? = null

        val parser = context.resources.getXml(R.xml.data_extraction_rules)
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "cloud-backup", "device-transfer" -> section = parser.name
                        "exclude" -> {
                            val activeSection = section
                            val domain = parser.getAttributeValue(null, "domain")
                            val path = parser.getAttributeValue(null, "path")
                            if (activeSection != null && path == ".") {
                                exclusions.getValue(activeSection).add(domain)
                            }
                        }
                    }
                } else if (
                    event == XmlPullParser.END_TAG &&
                    (parser.name == "cloud-backup" || parser.name == "device-transfer")
                ) {
                    section = null
                }
                event = parser.next()
            }
        } finally {
            parser.close()
        }

        assertEquals(expectedDomains, exclusions.getValue("cloud-backup"))
        assertEquals(expectedDomains, exclusions.getValue("device-transfer"))
        assertTrue(exclusions.values.all { it.isNotEmpty() })
    }
}
