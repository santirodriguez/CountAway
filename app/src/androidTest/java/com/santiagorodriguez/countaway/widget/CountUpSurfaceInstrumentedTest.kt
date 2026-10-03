package com.santiagorodriguez.countaway.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.EventCountResolver
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventIcon
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.RepeatRule
import com.santiagorodriguez.countaway.share.ShareCardContentFactory
import com.santiagorodriguez.countaway.share.ShareCardRenderer
import com.santiagorodriguez.countaway.ui.CountdownEventAdapter
import com.santiagorodriguez.countaway.ui.EventCountText
import com.santiagorodriguez.countaway.ui.LanguageChooser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class CountUpSurfaceInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val today = LocalDate.of(2026, 9, 29)
    private val dimensions = listOf(56 to 50, 57 to 102, 90 to 180, 180 to 50, 180 to 65,
        160 to 100, 160 to 144, 240 to 160, 240 to 220)

    @Test fun countUpRemoteViewsKeepCountsAndAccessibleMeaningInEverySize() = instrumentation.runOnMainSync {
        val problems = mutableListOf<String>()
        var checked = 0
        for (language in listOf("en", "es", "ca")) for (scale in listOf(1f, 1.3f, 2f)) {
            val context = localized(language, scale)
            for ((width, height) in dimensions) for (days in listOf(-1L, 0L, 1L, 12345L)) {
                val event = event(days)
                val size = WidgetPreviewDimensions(width, height)
                val content = WidgetEventContentFactory.from(event, today)
                val root = WidgetPreviewFactory.remoteViews(context, event,
                    WidgetStyleSelection(WidgetAppearance.DARK, WidgetBackground.MONOGRAM), today, size)
                    .apply(context, FrameLayout(context))
                layout(context, root, width, height)
                val label = "$language-$scale-${width}x$height-$days"
                try {
                    val count = root.findViewById<TextView>(R.id.widgetCount)
                    val unit = root.findViewById<TextView>(R.id.widgetUnit)
                    assertEquals(content.countTextFor(size.size, unit.visibility == View.VISIBLE), count.text.toString())
                    assertCountFits(root, count)
                    assertEquals(View.GONE, root.findViewById<View>(R.id.widgetMilestone).visibility)
                    assertEquals(content.description(context), root.contentDescription.toString())
                    assertTrue(root.contentDescription.toString().contains(EventCountText.status(context,
                        EventCountResolver.resolve(event, today))))
                    for (id in listOf(R.id.widgetTitle, R.id.widgetIcon, R.id.widgetUnit, R.id.widgetDate)) {
                        val view = root.findViewById<View>(id)
                        if (view.visibility == View.VISIBLE) assertFits(root, view)
                    }
                    checked++
                } catch (error: AssertionError) {
                    problems += "$label: ${error.message}"
                    capture(root, "up-failure-$label")
                }
                if (scale == 1f && days in listOf(0L, -1L)) capture(root, "up-$label")
            }
        }
        File(instrumentation.targetContext.filesDir, "layout-evidence/count-up-matrix.txt").apply {
            parentFile!!.mkdirs()
            writeText("checked=$checked\nexpected=324\n" + problems.joinToString("\n"))
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        assertEquals(324, checked)
    }

    @Test fun countUpPreviewAndStylesUseTheSameContent() = instrumentation.runOnMainSync {
        val context = localized("en", 1f)
        val event = event(1).copy(title = "Since launch 🚀")
        val size = WidgetPreviewDimensions(240, 220)
        val content = WidgetEventContentFactory.from(event, today)
        for (appearance in WidgetAppearance.entries) for (background in WidgetBackground.entries) {
            val style = WidgetStyleSelection(appearance, background)
            val root = WidgetPreviewFactory.remoteViews(context, event, style, today, size)
                .apply(context, FrameLayout(context))
            layout(context, root, size.widthDp, size.heightDp)
            assertCountFits(root, root.findViewById(R.id.widgetCount))
            val frame = FrameLayout(context)
            val container = FrameLayout(context).apply { addView(frame) }
            val preview = WidgetPreviewController(context, container, frame)
            preview.renderStyle(style, size)
            preview.renderEvent(content)
            for (id in listOf(R.id.widgetCount, R.id.widgetTitle, R.id.widgetUnit, R.id.widgetDate)) {
                assertEquals(root.findViewById<TextView>(id).text, frame.findViewById<TextView>(id).text)
            }
            assertEquals(root.contentDescription, frame.findViewById<View>(R.id.widgetRoot).contentDescription)
            capture(root, "up-style-$appearance-$background")
        }
    }

    @Test fun sharingKeepsStartDateDirectionAndPluralMeaning() = instrumentation.runOnMainSync {
        for (language in listOf("en", "es", "ca")) for (days in listOf(-1L, 0L, 1L, 12345L)) {
            val context = localized(language, 1f)
            val event = event(days)
            val value = EventCountResolver.resolve(event, today)
            val card = ShareCardContentFactory.create(context, event.title, event.date, event.icon,
                RepeatRule.NONE, today, CountMode.COUNT_UP)
            val text = ShareCardContentFactory.text(context, event.title, event.date,
                RepeatRule.NONE, today, CountMode.COUNT_UP)
            assertEquals(event.date, card.date)
            assertEquals(value.magnitude.toString(), card.primaryText)
            assertEquals(context.getString(EventCountText.countUpUnit(checkNotNull(value.countUpState), value.magnitude)),
                card.secondaryText)
            assertNull(card.recurrenceText)
            assertTrue(text.contains(EventCountText.status(context, value)))
            assertTrue(text.contains(card.dateText))
            for (dark in listOf(false, true)) {
                val bitmap = ShareCardRenderer.render(context, card, dark)
                try {
                    assertEquals(ShareCardRenderer.SIZE_PX, bitmap.width)
                    save(bitmap, "up-share-$language-$days-$dark")
                } finally { bitmap.recycle() }
            }
        }
    }

    @Test fun recycledHomeRowDoesNotKeepCountdownCelebration() = instrumentation.runOnMainSync {
        val context = localized("en", 1f)
        val adapter = CountdownEventAdapter(context)
        val parent = FrameLayout(context)
        adapter.submit(listOf(event(0).copy(countMode = CountMode.COUNT_DOWN)), today)
        val row = adapter.getView(0, null, parent)
        adapter.submit(listOf(event(0)), today)
        assertSame(row, adapter.getView(0, row, parent))
        val status = row.findViewById<TextView>(R.id.eventStatus)
        assertEquals("+0 days", status.text.toString())
        assertEquals(1f, status.scaleX, 0f)
        assertEquals(1f, status.scaleY, 0f)
        assertTrue(status.contentDescription.toString().contains("0 days elapsed"))
        layout(context, row, 320, 180)
        capture(row, "up-home-row")
    }

    @Test fun languageHeaderAndEditorControlsFitNarrowLargeText() = instrumentation.runOnMainSync {
        for (language in listOf("en", "es", "ca")) {
            val context = localized(language, 2f)
            val root = LayoutInflater.from(context).inflate(R.layout.activity_main, FrameLayout(context), false)
            val button = root.findViewById<TextView>(R.id.languageButton)
            val flag = context.getDrawable(R.drawable.flag_us)!!.apply { setBounds(0, 0, dp(context, 24), dp(context, 16)) }
            button.setCompoundDrawablesRelative(flag, null, null, null)
            button.compoundDrawablePadding = dp(context, 8)
            layout(context, root, 320, 800)
            for (id in listOf(R.id.languageButton, R.id.themeButton, R.id.aboutButton)) {
                val view = root.findViewById<View>(id)
                assertFits(root, view)
                assertTrue(view.height >= dp(context, 48))
            }
            assertNoEllipsis(button)
            capture(root, "language-header-$language-large")
        }
    }

    private fun event(days: Long) = CountdownEvent("up", "Since launch 👩🏽‍🚀 with a deliberately long title",
        today.minusDays(days), EventType.CUSTOM, icon = EventIcon.STAR, createdAt = Instant.EPOCH,
        countMode = CountMode.COUNT_UP)

    private fun localized(language: String, scale: Float): Context = instrumentation.targetContext.createConfigurationContext(
        Configuration(instrumentation.targetContext.resources.configuration).apply {
            fontScale = scale
            setLocale(Locale.forLanguageTag(language))
        },
    )

    private fun layout(context: Context, root: View, width: Int, height: Int) {
        repeat(3) {
            root.measure(View.MeasureSpec.makeMeasureSpec(dp(context, width), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(context, height), View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        }
    }

    private fun assertFits(root: View, view: View) {
        val rect = Rect(0, 0, view.width, view.height)
        (root as ViewGroup).offsetDescendantRectToMyCoords(view, rect)
        assertTrue("Empty content $rect", view.width > 0 && view.height > 0)
        assertTrue("Content outside ${root.width}x${root.height}: $rect",
            rect.left >= 0 && rect.top >= 0 && rect.right <= root.width && rect.bottom <= root.height)
        if (view is TextView) assertTrue("Text height clipped", view.layout.height <= view.height)
    }

    private fun assertCountFits(root: View, count: TextView) {
        assertFits(root, count)
        assertNoEllipsis(count)
        assertTrue("Count width clipped", count.paint.measureText(count.text.toString()) <=
            count.width - count.compoundPaddingLeft - count.compoundPaddingRight + 1)
    }

    private fun assertNoEllipsis(text: TextView) {
        assertFalse("Text ellipsized", (0 until text.layout.lineCount).any { text.layout.getEllipsisCount(it) > 0 })
    }

    private fun capture(root: View, name: String) {
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        try { root.draw(Canvas(bitmap)); save(bitmap, name) } finally { bitmap.recycle() }
    }

    private fun save(bitmap: Bitmap, name: String) {
        File(instrumentation.targetContext.filesDir, "layout-evidence/$name.png").apply {
            parentFile!!.mkdirs()
            outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
