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
    fun apply(view: TextView, glyphRes: Int, disclosure: Boolean = true) {
        fun drawable(resource: Int, size: Int): Drawable = view.context.getDrawable(resource)!!.mutate().apply {
            val pixels = (size * view.resources.displayMetrics.density).roundToInt()
            setBounds(0, 0, pixels, pixels)
        }
        view.setCompoundDrawablesRelative(drawable(glyphRes, 22), null,
            if (disclosure) drawable(R.drawable.editor_expand, 18) else null, null)
        view.compoundDrawablePadding = (12 * view.resources.displayMetrics.density).roundToInt()
        view.compoundDrawableTintList = ColorStateList.valueOf(view.context.getColor(R.color.accent_text))
    }
}

internal class EditorOptionAdapter(context: Context, labels: List<String>, private val glyphRes: Int) :
    ArrayAdapter<String>(context, R.layout.editor_spinner_item, labels) {
    init { setDropDownViewResource(R.layout.editor_spinner_dropdown_item) }
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        (super.getView(position, convertView, parent) as TextView).also {
            EditorControlIcons.apply(it, glyphRes)
        }
}
