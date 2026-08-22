package com.santiagorodriguez.countaway.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import kotlin.math.roundToInt

object WidgetBackgroundRenderer {
    fun render(
        context: Context,
        background: WidgetBackground,
        dark: Boolean,
        widthDp: Int,
        heightDp: Int,
    ): Bitmap {
        val configuration = Configuration(context.resources.configuration)
        val nightMode = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        configuration.uiMode =
            (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        val appearanceContext = context.createConfigurationContext(configuration)
        val drawable = requireNotNull(appearanceContext.getDrawable(background.drawableRes(dark))).mutate()

        val density = context.resources.displayMetrics.density
        val rawWidth = (widthDp.coerceAtLeast(1) * density).roundToInt().coerceAtLeast(1)
        val rawHeight = (heightDp.coerceAtLeast(1) * density).roundToInt().coerceAtLeast(1)
        val maxRawDimension = maxOf(rawWidth, rawHeight)
        val scale = if (maxRawDimension > MAX_BITMAP_DIMENSION_PX) {
            MAX_BITMAP_DIMENSION_PX.toFloat() / maxRawDimension
        } else {
            1f
        }
        val width = (rawWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (rawHeight * scale).roundToInt().coerceAtLeast(1)

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.setBounds(0, 0, width, height)
            drawable.draw(Canvas(bitmap))
        }
    }

    private const val MAX_BITMAP_DIMENSION_PX = 360
}
