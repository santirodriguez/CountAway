package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.method.TransformationMethod
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import kotlin.math.min
import kotlin.math.roundToInt

/** Keeps semantic copy intact while presenting its number on a separate line. */
class HomeCountTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TextView(context, attrs) {
    private var tileTextWidth = 0
    private val numberPattern = Regex("[+\\-]?\\p{Nd}+(?:[.,\\u00a0\\u202f]\\p{Nd}+)*")

    init {
        transformationMethod = object : TransformationMethod {
            override fun getTransformation(source: CharSequence?, view: View?): CharSequence {
                val original = source?.toString().orEmpty()
                val number = numberPattern.find(original)
                val chars = original.toCharArray()
                if (number != null) {
                    val before = number.range.first - 1
                    val after = number.range.last + 1
                    if (before >= 0 && chars[before] == ' ') chars[before] = '\n'
                    if (after < chars.size && chars[after] == ' ') chars[after] = '\n'
                }
                // Replace separators rather than adding characters. Text offsets,
                // accessibility copy and selection retain their original identity.
                return SpannableString(String(chars)).apply {
                    if (isEmpty()) return@apply
                    setSpan(AbsoluteSizeSpan(sp(11f).roundToInt()), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(StyleSpan(Typeface.NORMAL), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    val start = number?.range?.first ?: 0
                    val end = number?.range?.last?.plus(1) ?: length
                    setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    var sizeSpan: AbsoluteSizeSpan? = null
                    fun applySize(px: Int) {
                        sizeSpan?.let(::removeSpan)
                        sizeSpan = AbsoluteSizeSpan(px)
                        setSpan(sizeSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    val preferred = sp(if (number == null) 21f else 24f).toInt().coerceAtLeast(1)
                    if (tileTextWidth <= 0) {
                        applySize(preferred)
                    } else {
                        // Measure the actual styled run, not an approximation from
                        // a plain Paint. Keep rounding room for native line breaking.
                        val available = (tileTextWidth - resources.displayMetrics.density).coerceAtLeast(1f)
                        var low = 1
                        var high = preferred
                        var fitting = 1
                        while (low <= high) {
                            val candidate = (low + high) / 2
                            applySize(candidate)
                            if (Layout.getDesiredWidth(this, start, end, paint) <= available) {
                                fitting = candidate
                                low = candidate + 1
                            } else high = candidate - 1
                        }
                        applySize(fitting)
                    }
                }
            }

            override fun onFocusChanged(view: View?, sourceText: CharSequence?, focused: Boolean,
                direction: Int, previouslyFocusedRect: Rect?) = Unit
        }
    }

    fun prepareForWidth(width: Int) {
        val available = (width - compoundPaddingLeft - compoundPaddingRight).coerceAtLeast(1)
        if (available == tileTextWidth) return
        tileTextWidth = available
        setText(text.toString(), BufferType.NORMAL)
    }

    fun preferredTileWidth(): Int {
        val scale = resources.configuration.fontScale.coerceAtLeast(1f)
        val growth = 1f + (min(scale, 2f) - 1f) * 0.25f
        return (72f * resources.displayMetrics.density * growth).roundToInt()
    }

    fun preferredTileHeight(): Int {
        val metrics = Paint(paint)
        fun lineHeight(size: Float): Float {
            metrics.textSize = sp(size)
            val fm = metrics.fontMetrics
            return fm.bottom - fm.top
        }
        return maxOf((64f * resources.displayMetrics.density).roundToInt(),
            (lineHeight(24f) + 2 * lineHeight(11f) + 12f * resources.displayMetrics.density).roundToInt())
    }

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
