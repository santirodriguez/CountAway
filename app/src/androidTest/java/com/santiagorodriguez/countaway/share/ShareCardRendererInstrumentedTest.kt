package com.santiagorodriguez.countaway.share

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.model.EventIcon
import com.santiagorodriguez.countaway.model.RepeatRule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareCardRendererInstrumentedTest {
    @Test
    fun monthlyShareCardUsesResolvedOccurrenceAndRendersBothRidgeAppearances() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2026, 2, 1)
        val content = ShareCardContentFactory.create(
            context = context,
            title = "Month end",
            date = LocalDate.of(2026, 1, 31),
            icon = EventIcon.CALENDAR,
            repeatRule = RepeatRule.MONTHLY,
            today = today,
        )

        assertEquals(LocalDate.of(2026, 2, 28), content.date)

        val light = ShareCardRenderer.render(context, content, dark = false)
        val dark = ShareCardRenderer.render(context, content, dark = true)
        val center = ShareCardRenderer.SIZE_PX / 2
        try {
            assertEquals(768, ShareCardRenderer.SIZE_PX)
            assertEquals(ShareCardRenderer.SIZE_PX, light.width)
            assertEquals(ShareCardRenderer.SIZE_PX, light.height)
            assertEquals(ShareCardRenderer.SIZE_PX, dark.width)
            assertEquals(ShareCardRenderer.SIZE_PX, dark.height)
            assertNotEquals(light.getPixel(center, center), dark.getPixel(center, center))
        } finally {
            light.recycle()
            dark.recycle()
        }
    }

    @Test
    fun generatedSharePngIsReadableThroughTheDeclaredContentUri() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = ShareCardContentFactory.create(
            context = context,
            title = "Trip",
            date = LocalDate.of(2026, 12, 24),
            icon = EventIcon.AIRPLANE,
            repeatRule = RepeatRule.NONE,
            today = LocalDate.of(2026, 9, 24),
        )
        val bitmap = ShareCardRenderer.render(context, content, dark = true)
        val uri = try {
            ShareImageStore.write(context, bitmap)
        } finally {
            bitmap.recycle()
        }

        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.share", uri.authority)
        assertEquals("image/png", context.contentResolver.getType(uri))

        context.contentResolver.openInputStream(uri).use { input ->
            val decoded = BitmapFactory.decodeStream(input)
            assertTrue(decoded != null)
            assertEquals(ShareCardRenderer.SIZE_PX, decoded.width)
            assertEquals(ShareCardRenderer.SIZE_PX, decoded.height)
            decoded.recycle()
        }
    }
}
