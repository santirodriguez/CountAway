package com.santiagorodriguez.countaway.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownTimeSnapshot
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Test

/**
 * Development-only visual comparison, not an app preference or a selected product treatment.
 * A: keep the event icon and add a small CountAway mark in roomy cards.
 * B: use the CountAway mark in place of the event icon.
 * Both are applied to the actual native RemoteViews renderer before layout/capture.
 */
class TimeWidgetBrandConceptInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    private val snapshot = CountdownTimeSnapshot(
        ZonedDateTime.of(2026, 10, 16, 12, 0, 0, 0, zone))
    private val event = CountdownEvent(
        id = "brand-concepts", title = "Extraordinarily long concert anniversary in Buenos Aires",
        date = LocalDate.of(2026, 10, 19), eventTime = LocalTime.of(19, 12, 5),
        eventZone = zone, type = EventType.EVENT,
        createdAt = Instant.parse("2026-09-01T12:00:00Z"),
    )

    @Test fun twoBrandPlacementsKeepTimeAndTitleReadable() = instrumentation.runOnMainSync {
        val sizes = listOf(
            Triple(160, 90, 1f), Triple(160, 190, 1f),
            Triple(240, 180, 1f), Triple(280, 210, 1f),
            Triple(320, 250, 1f), Triple(280, 210, 2f),
        )
        for ((width, height, fontScale) in sizes) {
            for (appearance in listOf(WidgetAppearance.DARK, WidgetAppearance.LIGHT)) {
                for (concept in listOf("A", "B")) {
                    val context = fontContext(fontScale)
                    val style = WidgetConfiguration(event.id, appearance, WidgetBackground.HORIZON)
                    val remote = TimeWidgetRenderer.render(
                        context, context, 1234, style, event, null, snapshot,
                        width, height, hashMapOf(), interactive = false,
                    )
                    val roomy = width >= 235 && height >= 160 && fontScale < 1.6f
                    val theme = WidgetThemeResolver.resolve(
                        context, appearance, WidgetBackground.HORIZON)
                    if (concept == "A" && roomy) {
                        remote.setViewVisibility(R.id.timeWidgetBrandMark, View.VISIBLE)
                        remote.setInt(R.id.timeWidgetBrandMark, "setColorFilter", theme.accentTextColor)
                        remote.setViewVisibility(R.id.timeWidgetLiveDot, View.GONE)
                    }
                    if (concept == "B") {
                        remote.setImageViewResource(R.id.timeWidgetIcon, R.drawable.widget_time_brand_mark)
                    }

                    val root = remote.apply(context, FrameLayout(context)) as FrameLayout
                    layout(root, context, width, height)
                    val label = "$concept-$width" + "x$height-font" +
                        (fontScale * 100).toInt() + "-" + appearance.storageKey
                    val eventMark = root.findViewById<ImageView>(R.id.timeWidgetIcon)
                    val brand = root.findViewById<ImageView>(R.id.timeWidgetBrandMark)
                    val live = root.findViewById<View>(R.id.timeWidgetLiveDot)
                    val title = root.findViewById<TextView>(R.id.timeWidgetTitle)
                    val clock = root.findViewById<Chronometer>(R.id.timeWidgetClock)
                    val date = root.findViewById<TextView>(R.id.timeWidgetDate)
                    val plan = TimeWidgetLayoutPlanner.plan(width, height, fontScale)

                    assertEquals(label + " icon", View.VISIBLE, eventMark.visibility)
                    assertTrue(label + " event mark lost", eventMark.width > 0 && eventMark.height > 0)
                    assertEquals(label + " secondary mark",
                        if (concept == "A" && roomy) View.VISIBLE else View.GONE, brand.visibility)
                    if (concept == "A" && roomy) {
                        assertTrue(label + " CountAway mark not rendered",
                            brand.width > 0 && brand.height > 0 && brand.drawable != null)
                        assertEquals(label + " avoid three competing header symbols",
                            View.GONE, live.visibility)
                    }
                    assertTrue(label + " title lost", title.visibility == View.VISIBLE &&
                        title.text.isNotBlank() && title.height > 0)
                    assertTrue(label + " title layout missing", title.layout != null)
                    assertEquals(label + " native timer", View.VISIBLE, clock.visibility)
                    assertTrue(label + " native clock not formatted", clock.text.contains(":"))
                    assertEquals(label + " conditional date",
                        if (plan.showDate) View.VISIBLE else View.GONE, date.visibility)

                    val fields = listOfNotNull(eventMark, title, clock,
                        if (brand.visibility == View.VISIBLE) brand else null,
                        if (date.visibility == View.VISIBLE) date else null)
                    for (field in fields) {
                        val bounds = Rect(0, 0, field.width, field.height)
                        root.offsetDescendantRectToMyCoords(field, bounds)
                        assertTrue(label + " element outside bounds: " + bounds,
                            bounds.left >= 0 && bounds.top >= 0 &&
                                bounds.right <= root.width && bounds.bottom <= root.height)
                    }
                    capture(root, "brand-concept-$label.png")
                }
            }
        }
    }

    private fun fontContext(scale: Float): Context = app.createConfigurationContext(
        Configuration(app.resources.configuration).apply {
            fontScale = scale
            setLocale(Locale.US)
        })

    private fun layout(root: View, context: Context, widthDp: Int, heightDp: Int) {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).roundToInt()
        val height = (heightDp * density).roundToInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
    }

    private fun capture(root: View, name: String) {
        val file = File(app.filesDir, "layout-evidence/time-widget/" + name)
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        try {
            root.draw(Canvas(bitmap))
            file.outputStream().use { stream ->
                assertTrue("Failed to save brand-concept evidence",
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
