package com.santiagorodriguez.countaway.share

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.model.EventIcon
import com.santiagorodriguez.countaway.model.RepeatRule
import com.santiagorodriguez.countaway.widget.WidgetBackground
import com.santiagorodriguez.countaway.widget.WidgetPaletteResolver
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ShareCardLayoutInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val today = LocalDate.of(2026, 9, 28)

    @Test fun capturedConfigurationKeepsTextAndWordmarkTogetherAcrossSystemLanguages() {
        for (system in listOf("en", "es")) for (app in listOf("en", "es", "ca")) {
            val systemContext = localized(context, system)
            val effective = localized(systemContext, app)
            val screen = object : ContextWrapper(effective) {
                override fun getApplicationContext(): Context = systemContext
            }
            val captured = ShareCardRenderer.captureContext(screen)
            assertEquals(app, captured.resources.configuration.locales[0].language)
            assertNotSame(screen, captured)
            for (dark in listOf(false, true)) {
                val content = content(captured, "A new journey")
                val reference = ShareCardRenderer.render(effective, content(effective, "A new journey"), dark)
                val actual = ShareCardRenderer.render(captured, content, dark)
                try {
                    assertTrue("Wordmark and text must use the same captured locale", actual.sameAs(reference))
                    save(actual, "locale-$system-$app-${if (dark) "dark" else "light"}")
                } finally { reference.recycle(); actual.recycle() }
            }
        }
    }

    @Test fun ordinaryCardMatchesBaselinePixelsAndNewlineGetsARealSecondLine() {
        for (dark in listOf(false, true)) {
            val ordinary = "A new journey"
            val actual = ShareCardRenderer.render(context, content(context, ordinary), dark)
            val baseline = baselineCard(ordinary, dark)
            try { assertTrue("Ordinary title must retain exact baseline placement", baseline.sameAs(actual)) }
            finally { actual.recycle(); baseline.recycle() }

            val newline = "First line\nSecond line"
            val old = baselineCard(newline, dark)
            val fixed = ShareCardRenderer.render(context, content(context, newline), dark)
            try {
                assertFalse("Baseline drawText does not lay out the explicit newline", old.sameAs(fixed))
                assertEquals(2, ShareCardRenderer.titleLayout(newline, titlePaint(dark), 900, 82f).lineCount)
                save(old, "newline-baseline-$dark")
                save(fixed, "newline-fixed-$dark")
            } finally { old.recycle(); fixed.recycle() }
        }
    }

    @Test fun unicodeTitlesStayWithinTwoLinesAndLeaveOtherCardRegionsUntouched() {
        val titles = listOf("W".repeat(256), "👨‍👩‍👧‍👦 ✈️ e\u0301 ".repeat(35),
            "رحلة Buenos Aires שלום ".repeat(15), "First\nSecond\nThird")
        for ((index, title) in titles.withIndex()) for (dark in listOf(false, true)) {
            val layout = ShareCardRenderer.titleLayout(title, titlePaint(dark), 900, 82f)
            assertTrue(layout.lineCount in 1..2)
            assertTrue(layout.getEllipsisCount(layout.lineCount - 1) > 0)
            val image = ShareCardRenderer.render(context, content(context, title), dark)
            val blank = ShareCardRenderer.render(context, content(context, ""), dark)
            try {
                assertEquals(768, image.width)
                // The title may change only within its existing two-line region.
                for (y in 260 until 768) for (x in 0 until 768) {
                    assertEquals("Title overlaps lower card content", blank.getPixel(x, y), image.getPixel(x, y))
                }
                save(image, "unicode-$index-$dark")
            } finally { image.recycle(); blank.recycle() }
        }
    }

    private fun localized(base: Context, tag: String): Context = base.createConfigurationContext(
        Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
    private fun content(ctx: Context, title: String) = ShareCardContentFactory.create(ctx, title,
        today.plusDays(1), EventIcon.AIRPLANE, RepeatRule.MONTHLY, today)
    private fun titlePaint(dark: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WidgetPaletteResolver.resolve(WidgetBackground.MONOGRAM, dark).primaryTextColor
        textSize = 70f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    // Immutable title algorithm from the accepted parent (2a376614), used as a visual control.
    private fun baselineCard(title: String, dark: Boolean): Bitmap {
        val bitmap = ShareCardRenderer.render(context, content(context, ""), dark)
        val canvas = Canvas(bitmap)
        canvas.scale(768f / 1080f, 768f / 1080f)
        val paint = titlePaint(dark)
        if (paint.measureText(title) <= 900f) canvas.drawText(title, 76f, 226f, paint)
        else {
            val raw = paint.breakText(title, true, 900f, null).coerceAtLeast(1)
            val split = title.lastIndexOf(' ', raw - 1).takeIf { it > 0 } ?: raw
            canvas.drawText(title.substring(0, split).trimEnd(), 76f, 226f, paint)
            canvas.drawText(TextUtils.ellipsize(title.substring(split).trimStart(), paint, 900f,
                TextUtils.TruncateAt.END).toString(), 76f, 308f, paint)
        }
        return bitmap
    }
    private fun save(bitmap: Bitmap, name: String) {
        val directory = File(context.filesDir, "share-evidence").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
