package com.santiagorodriguez.countaway.share

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareLargeTextInstrumentedTest {
    @Test fun completeLargeShareTextIsReadableWithoutBinderExtras() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = "Historical event: " + "S".repeat(1_100_000)
        val uri = ShareTextStore.write(context, original)
        try {
            assertEquals("text/plain", context.contentResolver.getType(uri))
            val restored = context.contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
            assertEquals(original, restored)
            assertTrue(uri.toString().length < 200)
            assertTrue(original.length > 1_000_000)
        } finally {
            val name = uri.lastPathSegment!!
            java.io.File(ShareTextStore.directory(context), name).delete()
        }
    }
}
