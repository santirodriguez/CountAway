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
import java.util.Locale

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
            WidgetPreviewDimensions(90, 180), WidgetPreviewDimensions(180, 50),
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

    private fun context(scale: Float): Context {
        val config = Configuration(instrumentation.targetContext.resources.configuration).apply {
            fontScale = scale
            setLocale(Locale.ENGLISH)
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
