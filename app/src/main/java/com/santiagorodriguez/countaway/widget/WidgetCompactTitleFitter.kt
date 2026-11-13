package com.santiagorodriguez.countaway.widget

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue

/**
 * A compact single-word title must not leave an orphan syllable on a second row.
 * Keep normal multiword wrapping (especially in narrow/tall launcher slots).
 */
internal object WidgetCompactTitleFitter {
    private const val FULL_TITLE_SP = 10f
    private const val COMPACT_HORIZONTAL_PADDING_DP = 8f

    fun isSingleWord(title: String): Boolean =
        title.isNotBlank() && title.none(Char::isWhitespace)

    fun fittedSizeSp(context: Context, title: String, widthDp: Int): Float? {
        if (!isSingleWord(title)) return null
        val metrics = context.resources.displayMetrics
        val availablePx = (widthDp - COMPACT_HORIZONTAL_PADDING_DP).coerceAtLeast(1f) * metrics.density
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        for (sizeSp in listOf(FULL_TITLE_SP, 9.5f, 9f, 8.5f, 8f)) {
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, metrics)
            if (paint.measureText(title) <= availablePx) return sizeSp
        }
        return null
    }

    val normalSizeSp: Float get() = FULL_TITLE_SP
}
