package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.view.View
import android.widget.ImageView
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.ArrivalStage

/** Optional illustrations never replace the numeric state or the saved event icon. */
internal object ArrivalIllustration {
    fun resource(stage: ArrivalStage): Int = when (stage) {
        ArrivalStage.NONE -> 0
        ArrivalStage.THREE_DAYS -> R.drawable.ic_arrival_smile
        ArrivalStage.TWO_DAYS -> R.drawable.ic_arrival_grin
        ArrivalStage.TOMORROW -> R.drawable.ic_arrival_excited
        ArrivalStage.TODAY -> R.drawable.ic_arrival_party
    }

    fun bindHome(context: Context, image: ImageView, stage: ArrivalStage) {
        val resource = if (context.resources.configuration.fontScale <= 1.3f) resource(stage) else 0
        image.tag = resource
        if (resource == 0) {
            image.setImageDrawable(null)
            image.visibility = View.GONE
        } else {
            image.setImageResource(resource)
            image.visibility = View.VISIBLE
        }
    }
}
