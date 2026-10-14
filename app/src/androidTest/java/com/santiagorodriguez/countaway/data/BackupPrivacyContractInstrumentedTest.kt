package com.santiagorodriguez.countaway.data

import android.Manifest
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.annotation.XmlRes
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.share.ShareImageProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class BackupPrivacyContractInstrumentedTest {
    @Test
    fun cloudDeviceTransferAndLegacyRulesExcludeAllBackupDomains() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)

        assertEquals(0, applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)

        val expectedDomains = setOf(
            "root",
            "file",
            "database",
            "sharedpref",
            "external",
            "device_root",
            "device_file",
            "device_database",
            "device_sharedpref",
        )

        val extractionRules = parseSectionedExclusions(R.xml.data_extraction_rules)
        assertEquals(expectedDomains, extractionRules.getValue("cloud-backup"))
        assertEquals(expectedDomains, extractionRules.getValue("device-transfer"))
        assertEquals(expectedDomains, parseFlatExclusions(R.xml.backup_rules))
    }

    @Test
    fun installedPrivacySurfaceRequiresOnlyDeclaredPermissionsAndKeepsSharesPrivate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = context.packageManager
        val requestedPermissions = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        ).requestedPermissions?.toSet() ?: emptySet()

        assertEquals(
            setOf(
                Manifest.permission.RECEIVE_BOOT_COMPLETED,
                Manifest.permission.POST_NOTIFICATIONS,
            ),
            requestedPermissions,
        )

        val shareProvider = packageManager.getProviderInfo(
            ComponentName(context, ShareImageProvider::class.java),
            0,
        )
        assertFalse(shareProvider.exported)
        assertTrue(shareProvider.grantUriPermissions)
        assertEquals("${context.packageName}.share", shareProvider.authority)
    }

    private fun parseSectionedExclusions(@XmlRes resourceId: Int): Map<String, Set<String>> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val exclusions = mutableMapOf(
            "cloud-backup" to mutableSetOf<String>(),
            "device-transfer" to mutableSetOf<String>(),
        )
        var section: String? = null

        val parser = context.resources.getXml(resourceId)
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

        return exclusions.mapValues { (_, domains) -> domains.toSet() }
    }

    private fun parseFlatExclusions(@XmlRes resourceId: Int): Set<String> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val exclusions = mutableSetOf<String>()

        val parser = context.resources.getXml(resourceId)
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "exclude") {
                    val domain = parser.getAttributeValue(null, "domain")
                    val path = parser.getAttributeValue(null, "path")
                    if (path == ".") exclusions.add(domain)
                }
                event = parser.next()
            }
        } finally {
            parser.close()
        }

        return exclusions
    }
}
