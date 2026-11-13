package com.santiagorodriguez.countaway.widget

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import kotlin.math.roundToInt

internal data class WidgetCompactTitleLayout(
    val text: String,
    val sizeSp: Float,
    val maxLines: Int,
)

/**
 * Prioritize intact event names and only allow locale-aware hyphenation.
 * Never invent a visually balanced mid-word split (e.g. "Nacim / iento").
 */
internal object WidgetCompactTitleFitter {
    private const val FULL_TITLE_SP = 10f
    private const val COMPACT_HORIZONTAL_PADDING_DP = 8f
    private const val WIDTH_SAFETY_DP = 1f
    private val sizesSp = listOf(10f, 9.5f, 9f, 8.5f, 8f)

    fun isSingleWord(title: String): Boolean =
        title.isNotBlank() && title.none(Char::isWhitespace)

    fun fit(context: Context, title: String, widthDp: Int, titleLines: Int): WidgetCompactTitleLayout {
        val metrics = context.resources.displayMetrics
        val availablePx = ((widthDp - COMPACT_HORIZONTAL_PADDING_DP - WIDTH_SAFETY_DP)
            .coerceAtLeast(1f) * metrics.density).roundToInt().coerceAtLeast(1)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textLocale = context.resources.configuration.locales[0]
        }
        fun size(sp: Float) {
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics)
        }
        fun fits(text: String): Boolean = paint.measureText(text) <= availablePx

        // Try the entire word at every readable size before breaking it.
        for (sp in sizesSp) {
            size(sp)
            if (fits(title)) return WidgetCompactTitleLayout(title, sp, 1)
        }

        // Consult Android's own hyphenation at the selected language/scale,
        // then render its measured boundary identically in widget and preview.
        if (titleLines >= 2 && title.length >= 7 && title.all(Char::isLetter)) {
            for (sp in sizesSp) {
                size(sp)
                val layout = StaticLayout.Builder.obtain(title, 0, title.length, paint, availablePx)
                    .setIncludePad(false)
                    .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
                    .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
                    .build()
                if (layout.lineCount != 2) continue
                val cut = layout.getLineEnd(0)
                if (cut !in 3..(title.length - 3) || layout.getLineStart(1) != cut) continue
                val first = title.substring(0, cut)
                val second = title.substring(cut)
                val hyphenWidth = paint.measureText("$first-") - paint.measureText(first)
                // Native wrapping alone is not proof of a syllable break.
                if (layout.getLineWidth(0) - paint.measureText(first) <
                    hyphenWidth * 0.45f) continue
                if (fits("$first-") && fits(second)) {
                    return WidgetCompactTitleLayout("$first-\n$second", sp, 2)
                }
            }
        }

        // True constraints: a measured single-line ellipsis is more honest
        // than an arbitrary, hard-to-read fragment. Accessibility remains full.
        return WidgetCompactTitleLayout(title, 9f, 1)
    }

    val normalSizeSp: Float get() = FULL_TITLE_SP
}
