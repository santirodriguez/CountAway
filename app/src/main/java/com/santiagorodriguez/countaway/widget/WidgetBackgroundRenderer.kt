package com.santiagorodriguez.countaway.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.roundToInt

object WidgetBackgroundRenderer {
    fun render(
        context: Context,
        background: WidgetBackground,
        dark: Boolean,
        widthDp: Int,
        heightDp: Int,
    ): Bitmap {
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

        if (background == WidgetBackground.MONOGRAM) {
            return renderRidge(width, height, density * scale, dark)
        }

        val configuration = Configuration(context.resources.configuration)
        val nightMode = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        configuration.uiMode =
            (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        val appearanceContext = context.createConfigurationContext(configuration)
        val drawable = requireNotNull(appearanceContext.getDrawable(background.drawableRes(dark))).mutate()

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.setBounds(0, 0, width, height)
            drawable.draw(Canvas(bitmap))
        }
    }

    private fun renderRidge(
        width: Int,
        height: Int,
        scaledDensity: Float,
        dark: Boolean,
    ): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        val radius = 20f * scaledDensity
        val stroke = maxOf(1f, scaledDensity)
        val rect = RectF(stroke / 2f, stroke / 2f, width - stroke / 2f, height - stroke / 2f)

        val surface = if (dark) Color.rgb(7, 22, 47) else Color.rgb(248, 251, 255)
        val border = if (dark) Color.rgb(36, 69, 102) else Color.rgb(213, 224, 236)
        val motif = if (dark) Color.WHITE else Color.rgb(28, 79, 156)
        val outline = if (dark) Color.rgb(191, 212, 255) else Color.rgb(14, 42, 82)

        canvas.drawRoundRect(
            rect,
            radius,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = surface
            },
        )
        canvas.drawRoundRect(
            rect,
            radius,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                color = border
            },
        )

        val minDimension = minOf(width, height).toFloat()
        val motifWidth = (minDimension * 0.16f).coerceIn(
            6f * scaledDensity,
            22f * scaledDensity,
        )
        val path = Path().apply {
            moveTo(width * 0.24f, height * 0.27f)
            lineTo(width * 0.50f, height * 0.73f)
            lineTo(width * 0.76f, height * 0.27f)
        }
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = motifWidth + 2f * scaledDensity
                color = outline
                alpha = if (dark) 18 else 14
            },
        )
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = motifWidth
                color = motif
                alpha = if (dark) 41 else 43
            },
        )
    }

    private const val MAX_BITMAP_DIMENSION_PX = 360
}
