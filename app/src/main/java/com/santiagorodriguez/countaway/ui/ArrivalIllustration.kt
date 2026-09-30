package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.widget.TextView
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.ArrivalStage
import kotlin.math.roundToInt

/** Optional illustrations never replace the numeric state or the saved event icon. */
internal object ArrivalIllustration {
    fun resource(stage: ArrivalStage): Int = when (stage) {
        ArrivalStage.NONE -> 0
        ArrivalStage.THREE_DAYS -> R.drawable.ic_arrival_smile
        ArrivalStage.TWO_DAYS -> R.drawable.ic_arrival_grin
        ArrivalStage.TOMORROW -> R.drawable.ic_arrival_excited
        ArrivalStage.TODAY -> R.drawable.ic_arrival_party
    }

    fun bindHome(context: Context, status: TextView, stage: ArrivalStage) {
        val resource = if (context.resources.configuration.fontScale <= 1.3f) resource(stage) else 0
        val icon = if (resource == 0) null else context.getDrawable(resource)?.mutate()?.apply {
            val side = (18 * context.resources.displayMetrics.density).roundToInt()
            setBounds(0, 0, side, side)
        }
        status.setCompoundDrawablesRelative(icon, null, null, null)
        status.compoundDrawablePadding = if (icon == null) 0 else (4 * context.resources.displayMetrics.density).roundToInt()
    }
}
