package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.text.SpannableString
import android.text.Spanned
import android.text.method.TransformationMethod
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
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
                    setSpan(AbsoluteSizeSpan(sp(12f).roundToInt()), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(StyleSpan(Typeface.NORMAL), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    val start = number?.range?.first ?: 0
                    val end = number?.range?.last?.plus(1) ?: length
                    val preferred = sp(if (number == null) 23f else 26f)
                    val metrics = Paint(paint).apply { textSize = preferred; typeface = Typeface.create(typeface, Typeface.BOLD) }
                    val needed = metrics.measureText(original, start, end)
                    val size = if (tileTextWidth > 0 && needed > tileTextWidth)
                        preferred * tileTextWidth / needed else preferred
                    setSpan(AbsoluteSizeSpan(size.toInt().coerceAtLeast(1)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
        // Recompute the transformed spans only when the shared tile width changes.
        text = text
    }

    fun preferredTileWidth(): Int = (96f * resources.displayMetrics.density *
        min(resources.configuration.fontScale, 1.5f).coerceAtLeast(1f)).roundToInt()

    fun preferredTileHeight(): Int {
        val metrics = Paint(paint)
        fun lineHeight(size: Float): Float {
            metrics.textSize = sp(size)
            val fm = metrics.fontMetrics
            return fm.bottom - fm.top
        }
        return maxOf((80f * resources.displayMetrics.density).roundToInt(),
            (lineHeight(26f) + 2 * lineHeight(12f) + 16f * resources.displayMetrics.density).roundToInt())
    }

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
