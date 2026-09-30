package com.santiagorodriguez.countaway.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.ArrivalStage
import com.santiagorodriguez.countaway.data.CountdownDataProblem
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventIcon
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.ui.ArrivalIllustration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class ArrivalAccentInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val today = LocalDate.of(2026, 12, 31)
    private val event = CountdownEvent(id = "arrival", title = "Family 🧩", date = today,
        type = EventType.EVENT, icon = EventIcon.CALENDAR, createdAt = Instant.EPOCH)
    private val sizes = listOf(WidgetPreviewDimensions(56, 50), WidgetPreviewDimensions(57, 102),
        WidgetPreviewDimensions(90, 180), WidgetPreviewDimensions(180, 50),
        WidgetPreviewDimensions(160, 144), WidgetPreviewDimensions(240, 220))

    @Test fun everyStyleKeepsItsBackgroundAndUsesDistinctRecognizableIllustrations() = instrumentation.runOnMainSync {
        val glyphs = ArrivalStage.entries.filter { it != ArrivalStage.NONE }.map { stage ->
            val drawable = context.getDrawable(ArrivalIllustration.resource(stage))!!
            Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888).also { image ->
                drawable.setBounds(0, 0, 72, 72)
                drawable.draw(Canvas(image))
                assertTrue((0 until 72).any { x -> (0 until 72).any { y -> image.getPixel(x, y) != 0 } })
                save(image, "arrival-glyph-$stage")
            }
        }
        try {
            for (a in glyphs.indices) for (b in 0 until a) assertFalse(glyphs[a].sameAs(glyphs[b]))
        } finally { glyphs.forEach(Bitmap::recycle) }
        val dimensions = WidgetPreviewDimensions(240, 220)
        for (background in WidgetBackground.entries) for (dark in listOf(false, true)) {
            val base = WidgetBackgroundRenderer.render(context, background, dark, dimensions.widthDp, dimensions.heightDp)
            try {
                for (days in 0L..3L) {
                    val root = WidgetPreviewFactory.remoteViews(context, event.copy(date = today.plusDays(days)),
                        WidgetStyleSelection(if (dark) WidgetAppearance.DARK else WidgetAppearance.LIGHT, background),
                        today, dimensions).apply(context, FrameLayout(context))
                    layout(context, root, dimensions)
                    assertTrue("Theme must remain intact", base.sameAs(background(root)))
                    val badge = root.findViewById<ImageView>(R.id.widgetArrival)
                    assertEquals(View.VISIBLE, badge.visibility)
                    assertDecorationFits(root, badge)
                    capture(root, "arrival-art-$background-$dark-$days")
                }
            } finally { base.recycle() }
        }
    }

    @Test fun actualWidgetsRetainCountsAndFittingTitlesAcrossLocalesAndTextSizes() = instrumentation.runOnMainSync {
        var checked = 0
        val failures = mutableListOf<String>()
        for (locale in listOf("en", "es", "ca")) for (scale in listOf(1f, 2f)) for (dimensions in sizes) {
            val config = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(locale)); fontScale = scale
            }
            val localized = context.createConfigurationContext(config)
            for (days in 0L..3L) {
                val source = event.copy(date = today.plusDays(days))
                val style = WidgetStyleSelection(WidgetAppearance.DARK, WidgetBackground.MONOGRAM)
                val views = WidgetPreviewFactory.remoteViews(localized, source, style, today, dimensions)
                val root = views.apply(localized, FrameLayout(localized))
                val baseline = views.apply(localized, FrameLayout(localized))
                unwrapCount(baseline)
                layout(localized, root, dimensions)
                layout(localized, baseline, dimensions)
                capture(root, "arrival-widget-$locale-$scale-${dimensions.widthDp}-${dimensions.heightDp}-$days")
                try {
                    val title = root.findViewById<TextView>(R.id.widgetTitle)
                    assertEquals(source.title, title.text.toString())
                    val count = root.findViewById<TextView>(R.id.widgetCount)
                    assertFits(root, count)
                    assertFalse("Counter ellipsized", hasEllipsis(count))
                    assertContentUnchanged(baseline, root)
                    for (id in listOf(R.id.widgetTitle, R.id.widgetIcon, R.id.widgetUnit, R.id.widgetDate)) {
                        val view = root.findViewById<View>(id)
                        if (view.visibility == View.VISIBLE) assertFits(root, view)
                    }
                    assertEquals(View.GONE, root.findViewById<View>(R.id.widgetMilestone).visibility)
                    val badge = root.findViewById<ImageView>(R.id.widgetArrival)
                    if (badge != null && badge.visibility == View.VISIBLE) assertDecorationFits(root, badge)
                    if (scale == 2f && badge != null) assertEquals(View.GONE, badge.visibility)
                    if (scale == 1f && dimensions == WidgetPreviewDimensions(57, 102)) {
                        assertEquals(View.VISIBLE, title.visibility)
                        assertFalse("Narrow-tall title must remain whole", hasEllipsis(title))
                        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widgetIcon).visibility)
                    }
                } catch (failure: AssertionError) { failures += "$locale/$scale/$dimensions/$days: ${failure.message}" }
                checked++
            }
        }
        File(context.filesDir, "layout-evidence/arrival-matrix.txt").writeText(
            "checked=$checked\nexpected=144\nfailures=${failures.size}\n" + failures.joinToString("\n"))
        assertEquals(144, checked)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun providerAndPreviewsClearIllustrationsForCountUpErrorEmptyAndResize() = instrumentation.runOnMainSync {
        val companion = CountdownWidgetProvider.Companion
        val render = companion.javaClass.declaredMethods.single { it.name == "renderForSize" }.apply { isAccessible = true }
        val cache = HashMap<Any, Bitmap>()
        val later = event.copy(id = "later", date = today.plusDays(3))
        val up = event.copy(id = "up", countMode = CountMode.COUNT_UP)
        val data = WidgetRenderData.from(CountdownLoadResult.Success(listOf(event, later, up)), today)
        val style = WidgetStyleSelection(WidgetAppearance.DARK, WidgetBackground.MONOGRAM)
        fun rendered(configuration: WidgetConfiguration?, input: WidgetRenderData = data): View {
            val views = render.invoke(companion, context, context, 90421, today, configuration,
                input, 160, 144, cache) as RemoteViews
            return views.apply(context, FrameLayout(context)).also { layout(context, it, WidgetPreviewDimensions(160, 144)) }
        }
        fun configuration(id: String) = WidgetConfiguration(id, style.appearance, style.background)
        val arrival = rendered(configuration(event.id))
        val approaching = rendered(configuration(later.id))
        val ordinary = rendered(configuration(up.id))
        assertTrue(background(arrival).sameAs(background(approaching)))
        assertTrue(background(arrival).sameAs(background(ordinary)))
        assertEquals(View.VISIBLE, arrival.findViewById<View>(R.id.widgetArrival).visibility)
        assertEquals(View.VISIBLE, approaching.findViewById<View>(R.id.widgetArrival).visibility)
        assertEquals(View.GONE, ordinary.findViewById<View>(R.id.widgetArrival).visibility)
        assertEquals(1, cache.size)
        for (root in listOf(rendered(configuration("missing")),
            rendered(configuration(event.id), WidgetRenderData.Failure(CountdownDataProblem.CORRUPT)))) {
            assertEquals(View.GONE, root.findViewById<View>(R.id.widgetArrival).visibility)
        }
        val next = WidgetConfiguration(null, style.appearance, style.background, WidgetEventSelection.NEXT)
        assertEquals(View.VISIBLE, rendered(next).findViewById<View>(R.id.widgetArrival).visibility)
        val upOnly = WidgetRenderData.from(CountdownLoadResult.Success(listOf(up)), today)
        assertEquals(View.GONE, rendered(next, upOnly).findViewById<View>(R.id.widgetArrival).visibility)
        val preview = WidgetPreviewFactory.remoteViews(context, event, style, today, WidgetPreviewDimensions(160, 144))
            .apply(context, FrameLayout(context))
        assertTrue(background(arrival).sameAs(background(preview)))
        val frame = FrameLayout(context)
        val controller = WidgetPreviewController(context, FrameLayout(context), frame)
        controller.renderStyle(style, WidgetPreviewDimensions(160, 144))
        controller.renderEvent(WidgetEventContentFactory.from(event, today))
        assertEquals(View.VISIBLE, frame.findViewById<View>(R.id.widgetArrival).visibility)
        controller.renderStyle(style, WidgetPreviewDimensions(160, 85))
        assertEquals(View.GONE, frame.findViewById<View>(R.id.widgetArrival).visibility)
        controller.renderStyle(style, WidgetPreviewDimensions(160, 144))
        assertEquals(View.VISIBLE, frame.findViewById<View>(R.id.widgetArrival).visibility)
        controller.renderPlaceholder("Select event", "Configure")
        assertEquals(View.GONE, frame.findViewById<View>(R.id.widgetArrival).visibility)
    }

    private fun unwrapCount(root: View) {
        val slot = root.findViewById<FrameLayout>(R.id.widgetCountArea) ?: return
        val parent = slot.parent as LinearLayout
        val index = parent.indexOfChild(slot)
        val count = slot.findViewById<TextView>(R.id.widgetCount)
        slot.removeView(count)
        parent.removeView(slot)
        parent.addView(count, index, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 0, 1f))
    }

    private fun assertContentUnchanged(before: View, after: View) {
        for (id in listOf(R.id.widgetTitle, R.id.widgetIcon, R.id.widgetCount, R.id.widgetUnit, R.id.widgetDate)) {
            val old = before.findViewById<View>(id)
            val current = after.findViewById<View>(id)
            assertEquals("Visibility changed", old.visibility, current.visibility)
            if (old.visibility != View.VISIBLE) continue
            assertEquals("Content bounds changed", bounds(before, old), bounds(after, current))
            if (old is TextView && current is TextView) {
                assertEquals("Font changed", old.textSize, current.textSize, 0.01f)
                assertEquals(old.text.toString(), current.text.toString())
                assertEquals(old.layout.lineCount, current.layout.lineCount)
                for (line in 0 until old.layout.lineCount) assertEquals(old.layout.getEllipsisCount(line), current.layout.getEllipsisCount(line))
            }
        }
    }

    private fun assertDecorationFits(root: View, badge: ImageView) {
        assertFits(root, badge)
        val slot = root.findViewById<View>(R.id.widgetCountArea)
        val count = root.findViewById<TextView>(R.id.widgetCount)
        val decoration = bounds(root, badge)
        assertTrue("Illustration exceeds count slot", bounds(root, slot).contains(decoration))
        val center = bounds(root, count).exactCenterX()
        val halfWidth = count.paint.measureText(count.text.toString()) / 2
        assertTrue("Illustration overlaps count", decoration.left >= center + halfWidth || decoration.right <= center - halfWidth)
    }
    private fun background(root: View) = (root.findViewById<ImageView>(R.id.widgetBackground).drawable as BitmapDrawable).bitmap
    private fun bounds(root: View, view: View): Rect = Rect(0, 0, view.width, view.height).also {
        (root as ViewGroup).offsetDescendantRectToMyCoords(view, it)
    }
    private fun layout(context: Context, root: View, dimensions: WidgetPreviewDimensions) {
        val density = context.resources.displayMetrics.density
        repeat(3) {
            root.forceLayout()
            root.measure(View.MeasureSpec.makeMeasureSpec((dimensions.widthDp * density).roundToInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((dimensions.heightDp * density).roundToInt(), View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        }
    }
    private fun hasEllipsis(view: TextView) = (0 until view.layout.lineCount).any { view.layout.getEllipsisCount(it) > 0 }
    private fun assertFits(root: View, view: View) {
        val rect = bounds(root, view)
        assertTrue("No space for content: $rect", view.width > 0 && view.height > 0)
        assertTrue("Content clipped: $rect in ${root.width}x${root.height}",
            rect.left >= 0 && rect.top >= 0 && rect.right <= root.width && rect.bottom <= root.height)
        if (view is TextView) {
            assertTrue("Text clipped vertically", view.layout.height <= view.height)
            for (line in 0 until view.layout.lineCount) assertTrue("Text overflow",
                view.layout.getLineMax(line) <= view.width - view.compoundPaddingLeft - view.compoundPaddingRight + 1f)
        }
    }
    private fun capture(root: View, name: String) {
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        try { root.draw(Canvas(bitmap)); save(bitmap, name) } finally { bitmap.recycle() }
    }
    private fun save(bitmap: Bitmap, name: String) {
        File(context.filesDir, "layout-evidence/$name.png").apply {
            parentFile!!.mkdirs()
            outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
