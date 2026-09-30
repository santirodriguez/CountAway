package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.santiagorodriguez.countaway.R
import kotlin.math.roundToInt

internal object EditorControlIcons {
    private val wordSeparator = Regex("\\s+")
    private val widthListener = View.OnLayoutChangeListener { view, left, _, right, _, oldLeft, _, oldRight, _ ->
        if (right > left && right - left != oldRight - oldLeft) fitLabel(view as TextView)
    }

    fun apply(view: TextView, glyphRes: Int, disclosure: Boolean = true) {
        fun drawable(resource: Int, size: Int): Drawable = view.context.getDrawable(resource)!!.mutate().apply {
            val pixels = dp(view, size)
            setBounds(0, 0, pixels, pixels)
            setTint(view.context.getColor(R.color.accent_text))
        }
        view.setCompoundDrawablesRelative(drawable(glyphRes, 22), null,
            if (disclosure) drawable(R.drawable.editor_expand, 18) else null, null)
        view.compoundDrawablePadding = dp(view, 12)
        view.compoundDrawableTintList = ColorStateList.valueOf(view.context.getColor(R.color.accent_text))
        // The same listener is reused so recycled spinner rows cannot accumulate callbacks.
        view.removeOnLayoutChangeListener(widthListener)
        view.addOnLayoutChangeListener(widthListener)
        fitLabel(view)
    }

    private fun fitLabel(view: TextView) {
        if (view.width <= 0) return
        val current = view.compoundDrawablesRelative
        val glyph = current[0] ?: current[1] ?: return
        val end = current[2]
        val available = view.width - view.paddingLeft - view.paddingRight
        val longestWord = view.text.split(wordSeparator).maxOfOrNull { view.paint.measureText(it) } ?: 0f
        fun inlineWidth(gap: Int) = available - glyph.bounds.width() - gap -
            (end?.let { it.bounds.width() + gap } ?: 0)
        val gap = if (longestWord <= inlineWidth(dp(view, 12))) dp(view, 12) else dp(view, 6)
        // Spend decorative spacing first. Only move the leading glyph above genuinely oversized
        // labels; preserve the native font size, full label, disclosure and interactive target.
        val stacked = longestWord > inlineWidth(gap)
        if (view.compoundDrawablePadding != gap) view.compoundDrawablePadding = gap
        if (stacked != (current[1] != null)) {
            view.setCompoundDrawablesRelative(if (stacked) null else glyph,
                if (stacked) glyph else null, end, null)
        }
    }

    private fun dp(view: TextView, value: Int): Int =
        (value * view.resources.displayMetrics.density).roundToInt()
}

internal class EditorOptionAdapter(context: Context, labels: List<String>, private val glyphRes: Int) :
    ArrayAdapter<String>(context, R.layout.editor_spinner_item, labels) {
    init { setDropDownViewResource(R.layout.editor_spinner_dropdown_item) }
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        (super.getView(position, convertView, parent) as TextView).also {
            EditorControlIcons.apply(it, glyphRes)
        }
}
