package com.santiagorodriguez.countaway.ui

import android.app.AlertDialog
import android.widget.Button
import com.santiagorodriguez.countaway.R

internal object DialogPresentation {
    enum class PositiveTone { ACCENT, DANGER }

    fun polish(dialog: AlertDialog, tone: PositiveTone = PositiveTone.ACCENT) {
        val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE) ?: return
        val context = dialog.context
        val horizontal = (14f * context.resources.displayMetrics.density).toInt()
        positive.setAllCaps(false)
        positive.minHeight = (44f * context.resources.displayMetrics.density).toInt()
        positive.setPadding(horizontal, 0, horizontal, 0)
        when (tone) {
            PositiveTone.ACCENT -> {
                positive.setBackgroundResource(R.drawable.button_primary)
                positive.setTextColor(context.getColor(R.color.on_accent))
            }
            PositiveTone.DANGER -> {
                positive.setBackgroundResource(R.drawable.button_destructive)
                positive.setTextColor(context.getColor(R.color.danger))
            }
        }
        normalizeSecondary(dialog.getButton(AlertDialog.BUTTON_NEGATIVE))
        normalizeSecondary(dialog.getButton(AlertDialog.BUTTON_NEUTRAL))
    }

    private fun normalizeSecondary(button: Button?) {
        if (button == null) return
        button.setAllCaps(false)
    }
}
