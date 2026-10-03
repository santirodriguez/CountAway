package com.santiagorodriguez.countaway.ui

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.StaticLayout
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import com.santiagorodriguez.countaway.R
import kotlin.math.roundToInt

/** Layout for editor choices only; widget title layout has its own sizing policy. */
internal class EditorChoiceLayout(private val grid: GridLayout) {
    data class Choice(val key: String, val label: String, val glyphRes: Int)

    private val context get() = grid.context
    private val probe = TextView(context).apply {
        textSize = 12f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    fun render(choices: List<Choice>, selectedKey: String?, enabled: Boolean, onClick: (String) -> Unit) {
        val width = (grid.width.takeIf { it > 0 }
            ?: dp((context.resources.configuration.screenWidthDp - 40).coerceAtLeast(120))) -
            grid.paddingLeft - grid.paddingRight
        val columnTextWidth = ((width - dp(12)) / 3 - dp(8)).coerceAtLeast(1)
        val columns = if (choices.all { fitsGridLabel(it.label, columnTextWidth) }) 3 else 1
        val sameChoices = grid.childCount == choices.size && choices.indices.all {
            grid.getChildAt(it).tag == choices[it].key
        }
        if (!sameChoices) {
            grid.removeAllViews()
            choices.forEach { choice ->
                grid.addView(TextView(context).apply {
                    tag = choice.key
                    ChoiceAccessibility.apply(this)
                    text = choice.label
                    contentDescription = choice.label
                    typeface = probe.typeface
                    setTextColor(context.getColorStateList(R.color.preset_choice_text))
                    setBackgroundResource(R.drawable.preset_choice_background)
                    compoundDrawableTintList = ColorStateList.valueOf(context.getColor(R.color.accent_text))
                    setOnClickListener { onClick(choice.key) }
                })
            }
        }
        // Keep the existing views (and their input/accessibility focus) during width-only reflow.
        // Grow the column range before assigning wider specs; shrink it only after replacing them.
        if (columns > grid.columnCount) grid.columnCount = columns
        val labelHeight = choices.maxOfOrNull {
            StaticLayout.Builder.obtain(it.label, 0, it.label.length, probe.paint, columnTextWidth)
                .setIncludePad(true).build().height
        } ?: 0
        val cardMinimum = maxOf(dp(78), labelHeight + dp(46))
        choices.forEachIndexed { index, choice ->
            val view = grid.getChildAt(index) as TextView
            view.textSize = if (columns == 3) 12f else 14f
            view.gravity = if (columns == 3) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
            view.setPaddingRelative(dp(if (columns == 3) 4 else 12), dp(8),
                dp(if (columns == 3) 4 else 12), dp(8))
            val glyph = context.getDrawable(choice.glyphRes)!!.mutate().apply {
                setBounds(0, 0, dp(24), dp(24))
                // Tint each replacement drawable, not only the TextView's previously assigned set.
                setTint(context.getColor(R.color.accent_text))
            }
            if (columns == 3) view.setCompoundDrawablesRelative(null, glyph, null, null)
            else view.setCompoundDrawablesRelative(glyph, null, null, null)
            view.compoundDrawablePadding = dp(if (columns == 3) 6 else 12)
            view.minHeight = if (columns == 3) cardMinimum else dp(52)
            view.isEnabled = enabled
            view.isSelected = choice.key == selectedKey
            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / columns, GridLayout.FILL),
                GridLayout.spec(index % columns, 1f),
            ).apply {
                this.width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            val old = view.layoutParams as? GridLayout.LayoutParams
            if (old == null || old.rowSpec != params.rowSpec || old.columnSpec != params.columnSpec ||
                old.width != params.width || old.leftMargin != params.leftMargin) view.layoutParams = params
        }
        if (grid.columnCount != columns) grid.columnCount = columns
    }

    private fun fitsGridLabel(label: String, width: Int): Boolean {
        if (label.split(Regex("\\s+")).any { probe.paint.measureText(it) > width - dp(1) }) return false
        val layout = StaticLayout.Builder.obtain(label, 0, label.length, probe.paint, width)
            .setIncludePad(true).build()
        return layout.lineCount <= 2
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
}
