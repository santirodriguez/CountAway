package com.santiagorodriguez.countaway.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.ceil

class WidgetTitleLayoutInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val today = LocalDate.of(2026, 9, 29)
    private val birthday = CountdownEvent("birthday", "Sophia's birthday 🥳",
        today.plusDays(91), EventType.BIRTHDAY, createdAt = Instant.EPOCH)

    @Test fun narrowTallTitleAndBirthdayIconFitTogether() = instrumentation.runOnMainSync {
        for (appearance in listOf(WidgetAppearance.LIGHT, WidgetAppearance.DARK)) {
            val context = context(1f)
            val dimensions = WidgetPreviewDimensions(57, 102)
            val style = WidgetStyleSelection(appearance, WidgetBackground.MONOGRAM)
            val legacyViews = WidgetPreviewFactory.remoteViews(context, birthday, style, today, dimensions)
            legacyViews.setInt(R.id.widgetTitle, "setMaxLines", 1)
            legacyViews.setViewVisibility(R.id.widgetIcon, View.GONE)
            val legacy = legacyViews.apply(context, FrameLayout(context))
            layout(context, legacy, dimensions)
            assertTrue("The released one-line policy must reproduce title truncation",
                hasEllipsis(legacy.findViewById(R.id.widgetTitle)))
            capture(legacy, "title-before-$appearance")

            val root = WidgetPreviewFactory.remoteViews(context, birthday, style, today, dimensions)
                .apply(context, FrameLayout(context))
            layout(context, root, dimensions)
            capture(root, "title-after-$appearance")
            val title = root.findViewById<TextView>(R.id.widgetTitle)
            assertEquals(birthday.title, title.text.toString())
            assertFalse("The full birthday title and emoji should fit", hasEllipsis(title))
            assertTrue("The title must wrap", title.lineCount > 1)
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widgetIcon).visibility)
            assertFits(root, title)
            assertFits(root, root.findViewById(R.id.widgetIcon))
            assertCountFits(root)

            val container = FrameLayout(context)
            val frame = FrameLayout(context)
            container.addView(frame)
            val preview = WidgetPreviewController(context, container, frame)
            preview.renderStyle(style, dimensions)
            preview.renderEvent(WidgetEventContentFactory.from(birthday, today))
            assertEquals(title.maxLines, frame.findViewById<TextView>(R.id.widgetTitle).maxLines)
            assertEquals(View.VISIBLE, frame.findViewById<View>(R.id.widgetIcon).visibility)
        }
    }

    @Test fun responsiveTitlesNeverPushCountsOutsideTheWidget() = instrumentation.runOnMainSync {
        val problems = mutableListOf<String>()
        val dimensions = listOf(WidgetPreviewDimensions(56, 50), WidgetPreviewDimensions(57, 102),
            WidgetPreviewDimensions(90, 180), WidgetPreviewDimensions(180, 50), WidgetPreviewDimensions(180, 65),
            WidgetPreviewDimensions(160, 100), WidgetPreviewDimensions(160, 144),
            WidgetPreviewDimensions(240, 160), WidgetPreviewDimensions(240, 220))
        for (scale in listOf(1f, 1.3f, 2f)) for (size in dimensions)
            for (days in listOf(-12345L, -6L, 0L, 1L, 91L, 12345L)) {
                val context = context(scale)
                val event = birthday.copy(date = today.plusDays(days),
                    title = "Sophia's birthday 🥳 with a deliberately long second part")
                val root = WidgetPreviewFactory.remoteViews(context, event,
                    WidgetStyleSelection(WidgetAppearance.DARK, WidgetBackground.MONOGRAM), today, size)
                    .apply(context, FrameLayout(context))
                layout(context, root, size)
                try {
                    assertCountFits(root)
                    for (id in listOf(R.id.widgetTitle, R.id.widgetIcon)) {
                        val view = root.findViewById<View>(id)
                        if (view.visibility == View.VISIBLE) assertFits(root, view)
                    }
                } catch (error: AssertionError) {
                    capture(root, "failure-${size.widthDp}-${size.heightDp}-$scale-$days")
                    problems += "${size.widthDp}x${size.heightDp}, font=$scale, days=$days: ${error.message}"
                }
            }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun localizedTwoLineDetailsDoNotClipTheCount() = instrumentation.runOnMainSync {
        val problems = mutableListOf<String>()
        var wrappedUnits = 0
        var wrappedDates = 0
        var legacyClipped = 0
        val event = birthday.copy(date = LocalDate.of(2027, 9, 29),
            title = "Un cumpleaños molt especial 🥳 amb un títol llarg")
        for (language in listOf("en", "es", "ca")) for (scale in listOf(1.3f, 1.4f, 1.5f, 1.6f, 1.7f)) {
            val context = context(scale, language)
            val localizedDate = event.date.format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.forLanguageTag(language)))
            for (large in listOf(false, true)) {
                // Height where the previous single-line detail budget allowed two title lines.
                val oldThreshold = (if (large) 28 else 20) +
                    ceil((if (large) 22 else 20) * scale).toInt() +
                    ceil((if (large) 20 else 16) * scale).toInt() +
                    (if (large && scale < 1.5f) ceil(20 * scale).toInt() + 4 else 0) +
                    2 * ceil((if (large) 22 else 18) * scale).toInt()
                for (extra in listOf(0, 8, 64))
                    for (twoLineDate in if (large && scale < 1.5f) listOf(false, true) else listOf(false)) {
                    val size = WidgetPreviewDimensions(if (large) 180 else 110, oldThreshold + extra)
                    val style = WidgetStyleSelection(WidgetAppearance.DARK, WidgetBackground.MONOGRAM)
                    // Medium date abbreviations vary by Android's locale data and may fit one line.
                    // Also exercise the permitted two-line date height without changing its content.
                    val dateText = if (twoLineDate) localizedDate.let {
                        val space = it.lastIndexOf(' ')
                        it.replaceRange(space, space + 1, "\n")
                    } else localizedDate
                    val legacyViews = WidgetPreviewFactory.remoteViews(context, event, style, today, size)
                    legacyViews.setTextViewText(R.id.widgetDate, dateText)
                    legacyViews.setInt(R.id.widgetTitle, "setMaxLines", 2)
                    val legacy = legacyViews.apply(context, FrameLayout(context))
                    layout(context, legacy, size)
                    try { assertCountFits(legacy) } catch (_: AssertionError) { legacyClipped++ }

                    val fixedViews = WidgetPreviewFactory.remoteViews(context, event, style, today, size)
                    fixedViews.setTextViewText(R.id.widgetDate, dateText)
                    val root = fixedViews.apply(context, FrameLayout(context))
                    layout(context, root, size)
                    val unit = root.findViewById<TextView>(R.id.widgetUnit)
                    val date = root.findViewById<TextView>(R.id.widgetDate)
                    if (unit.visibility == View.VISIBLE && unit.lineCount == 2) wrappedUnits++
                    if (date.visibility == View.VISIBLE && date.lineCount == 2) wrappedDates++
                    try {
                        assertCountFits(root)
                        for (id in listOf(R.id.widgetTitle, R.id.widgetUnit, R.id.widgetDate)) {
                            val view = root.findViewById<View>(id)
                            if (view.visibility == View.VISIBLE) assertFits(root, view)
                        }
                    } catch (error: AssertionError) {
                        problems += "$language $scale ${size.widthDp}x${size.heightDp} dateLines=${date.lineCount}: ${error.message}"
                        capture(root, "wrapped-failure-$language-$scale-$large-$extra-$twoLineDate")
                    }
                    if (language != "en" && extra == 0) {
                        capture(legacy, "wrapped-before-$language-$scale-$large-$twoLineDate")
                        capture(root, "wrapped-after-$language-$scale-$large-$twoLineDate")
                    }
                }
            }
        }
        assertTrue("Localized unit labels must actually wrap", wrappedUnits > 0)
        assertTrue("The permitted two-line date height must be exercised", wrappedDates > 0)
        assertTrue("The previous budget must reproduce count clipping", legacyClipped > 0)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private fun context(scale: Float, language: String = "en"): Context {
        val config = Configuration(instrumentation.targetContext.resources.configuration).apply {
            fontScale = scale
            setLocale(Locale.forLanguageTag(language))
        }
        return instrumentation.targetContext.createConfigurationContext(config)
    }

    private fun layout(context: Context, root: View, dimensions: WidgetPreviewDimensions) {
        val density = context.resources.displayMetrics.density
        repeat(3) {
            root.measure(View.MeasureSpec.makeMeasureSpec((dimensions.widthDp * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((dimensions.heightDp * density).toInt(), View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        }
    }

    private fun hasEllipsis(text: TextView): Boolean =
        (0 until text.layout.lineCount).any { text.layout.getEllipsisCount(it) > 0 }

    private fun assertCountFits(root: View) {
        val count = root.findViewById<TextView>(R.id.widgetCount)
        assertFits(root, count)
        assertFalse("Count must not ellipsize", hasEllipsis(count))
        assertTrue("Count must fit horizontally", count.paint.measureText(count.text.toString()) <=
            count.width - count.compoundPaddingLeft - count.compoundPaddingRight + 1)
    }

    private fun assertFits(root: View, view: View) {
        val rect = Rect(0, 0, view.width, view.height)
        (root as ViewGroup).offsetDescendantRectToMyCoords(view, rect)
        assertTrue("Visible content must have space: $rect", view.width > 0 && view.height > 0)
        assertTrue("Content must remain inside ${root.width}x${root.height}: $rect",
            rect.left >= 0 && rect.top >= 0 && rect.right <= root.width && rect.bottom <= root.height)
        if (view is TextView) assertTrue("Text height must fit", view.layout.height <= view.height)
    }

    private fun capture(root: View, name: String) {
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val file = File(instrumentation.targetContext.filesDir, "layout-evidence/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
