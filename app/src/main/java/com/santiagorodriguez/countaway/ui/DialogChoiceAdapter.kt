package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckedTextView
import com.santiagorodriguez.countaway.R

/** Shared accessible, checkable rows for language and appearance preferences. */
internal data class DialogChoice(
    val title: String,
    val iconRes: Int,
    val subtitle: String? = null,
    val flagIcon: Boolean = false,
)

internal class DialogChoiceAdapter(
    private val context: Context,
    private val choices: List<DialogChoice>,
) : BaseAdapter() {
    override fun getCount(): Int = choices.size
    override fun getItem(position: Int): DialogChoice = choices[position]
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView as? CheckedTextView
            ?: LayoutInflater.from(context).inflate(R.layout.dialog_choice_row, parent, false) as CheckedTextView
        val choice = choices[position]
        view.text = choice.subtitle?.let { subtitle ->
            SpannableStringBuilder(choice.title).apply {
                append('\n')
                val start = length
                append(subtitle)
                setSpan(RelativeSizeSpan(0.85f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(
                    ForegroundColorSpan(context.getColor(R.color.secondary_text)),
                    start,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        } ?: choice.title
        val drawable = context.getDrawable(choice.iconRes)?.apply {
            setBounds(
                0, 0,
                dp(if (choice.flagIcon) 30 else 24),
                dp(if (choice.flagIcon) 20 else 24),
            )
        }
        view.setCompoundDrawablesRelative(drawable, null, null, null)
        return view
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
