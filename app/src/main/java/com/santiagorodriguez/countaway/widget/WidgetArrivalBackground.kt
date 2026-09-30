package com.santiagorodriguez.countaway.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.santiagorodriguez.countaway.countdown.ArrivalStage
import com.santiagorodriguez.countaway.ui.ArrivalAccent

/** Reuses the selected artwork and its existing allocation cap. No additional widget row. */
internal object WidgetArrivalBackground {
    fun render(context: Context, background: WidgetBackground, dark: Boolean,
        widthDp: Int, heightDp: Int, stage: ArrivalStage): Bitmap {
        val bitmap = WidgetBackgroundRenderer.render(context, background, dark, widthDp, heightDp)
        if (stage != ArrivalStage.NONE) {
            val theme = WidgetThemeResolver.resolve(context,
                if (dark) WidgetAppearance.DARK else WidgetAppearance.LIGHT, background)
            ArrivalAccent.draw(Canvas(bitmap), bitmap.width.toFloat(), bitmap.height.toFloat(),
                bitmap.width.toFloat() / widthDp.coerceAtLeast(1), theme.accentTextColor, stage)
        }
        return bitmap
    }
}
