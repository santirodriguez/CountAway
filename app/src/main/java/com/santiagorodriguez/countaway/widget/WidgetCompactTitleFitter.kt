package com.santiagorodriguez.countaway.widget

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import kotlin.math.abs

internal data class WidgetCompactTitleLayout(
    val text: String,
    val sizeSp: Float,
    val maxLines: Int,
)

/**
 * Keep compact event names readable without leaving a one- or two-letter
 * fragment on the next line. A tall launcher cell can use two balanced rows;
 * its actual event title and accessible description remain unchanged.
 */
internal object WidgetCompactTitleFitter {
    private const val FULL_TITLE_SP = 10f
    private const val COMPACT_HORIZONTAL_PADDING_DP = 8f
    private const val WIDTH_SAFETY_DP = 1f

    fun isSingleWord(title: String): Boolean =
        title.isNotBlank() && title.none(Char::isWhitespace)

    fun fit(context: Context, title: String, widthDp: Int, titleLines: Int): WidgetCompactTitleLayout {
        val metrics = context.resources.displayMetrics
        val availablePx = (widthDp - COMPACT_HORIZONTAL_PADDING_DP - WIDTH_SAFETY_DP)
            .coerceAtLeast(1f) * metrics.density
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        fun atSize(sp: Float) {
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics)
        }
        fun fits(text: String): Boolean = paint.measureText(text) <= availablePx

        // Prefer a normal-sized complete title whenever it fits on one row.
        for (sp in listOf(10f, 9.5f, 9f)) {
            atSize(sp)
            if (fits(title)) return WidgetCompactTitleLayout(title, sp, 1)
        }

        // Do not force a one-line ellipsis on a narrow, tall cell. For a
        // single alphabetic word, find two measured, balanced fragments;
        // never leave a one- or two-letter orphan. Other scripts and emoji
        // retain native single-line ellipsis rather than splitting a cluster.
        if (titleLines >= 2 && title.length >= 7 && title.all(Char::isLetter)) {
            for (sp in listOf(10f, 9.5f, 9f, 8.5f, 8f)) {
                atSize(sp)
                val split = (3..title.length - 3)
                    .filter { cut -> fits(title.substring(0, cut)) && fits(title.substring(cut)) }
                    .minByOrNull { cut ->
                        abs(paint.measureText(title.substring(0, cut)) -
                            paint.measureText(title.substring(cut)))
                    }
                if (split != null) {
                    return WidgetCompactTitleLayout(
                        title.substring(0, split) + "\n" + title.substring(split),
                        sp, 2,
                    )
                }
            }
        }

        // A truly short cell has no spare title row; a smaller intact label
        // beats a dangling fragment, otherwise use the existing end ellipsis.
        for (sp in listOf(8.5f, 8f)) {
            atSize(sp)
            if (fits(title)) return WidgetCompactTitleLayout(title, sp, 1)
        }
        return WidgetCompactTitleLayout(title, FULL_TITLE_SP, 1)
    }

    val normalSizeSp: Float get() = FULL_TITLE_SP
}
