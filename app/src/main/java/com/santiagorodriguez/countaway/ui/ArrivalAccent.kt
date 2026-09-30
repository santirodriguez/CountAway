package com.santiagorodriguez.countaway.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.santiagorodriguez.countaway.countdown.ArrivalStage
import kotlin.math.min

/** Static peripheral artwork. The central content rectangle is never painted. */
internal object ArrivalAccent {
    fun draw(canvas: Canvas, width: Float, height: Float, density: Float,
        accent: Int, stage: ArrivalStage, opacity: Int = 255) {
        if (stage == ArrivalStage.NONE || width <= 0f || height <= 0f) return
        val shortSide = min(width, height)
        val band = min(8f * density, shortSide * 0.055f)
        val inset = min(1.5f * density, band / 3f)
        val radius = min(20f * density, shortSide / 2f)
        val strength = when (stage) {
            ArrivalStage.THREE_DAYS -> 120
            ArrivalStage.TWO_DAYS -> 160
            ArrivalStage.TOMORROW -> 205
            ArrivalStage.TODAY -> 240
            ArrivalStage.NONE -> 0
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            alpha = strength * opacity / 255
            style = Paint.Style.STROKE
            strokeWidth = min((if (stage == ArrivalStage.TODAY) 2f else 1.5f) * density, band * 0.7f)
            strokeCap = Paint.Cap.ROUND
        }
        val save = canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, width, height), radius, radius, Path.Direction.CW) })
        canvas.clipOutRect(band, band, width - band, height - band)
        // Small rounded corners extend into the protected content rectangle. Prefer
        // a clean edge highlight to drawing a clipped, discontinuous outline there.
        if (shortSide >= 128f * density) {
            canvas.drawRoundRect(RectF(inset, inset, width - inset, height - inset),
                (radius - inset).coerceAtLeast(0f), (radius - inset).coerceAtLeast(0f), paint)
        }

        // One, two, then three small highlights, without pretending to show a percentage.
        val highlights = when (stage) {
            ArrivalStage.THREE_DAYS -> 1
            ArrivalStage.TWO_DAYS -> 2
            ArrivalStage.TOMORROW -> 3
            ArrivalStage.TODAY -> 3
            ArrivalStage.NONE -> 0
        }
        paint.alpha = 235 * opacity / 255
        paint.strokeWidth = min(2.5f * density, band * 0.7f)
        val length = min(10f * density, width * 0.08f)
        val gap = min(5f * density, width * 0.04f)
        val total = length * highlights + gap * (highlights - 1)
        for (index in 0 until highlights) {
            val x = (width - total) / 2f + index * (length + gap)
            canvas.drawLine(x, band * 0.55f, x + length, band * 0.55f, paint)
        }
        if (stage == ArrivalStage.TODAY) {
            canvas.drawLine((width - total) / 2f, height - band * 0.55f,
                (width + total) / 2f, height - band * 0.55f, paint)
        }
        if (stage == ArrivalStage.TODAY && shortSide >= 72f * density) {
            // Fixed positions avoid flicker across refreshes; details remain outside the content.
            val colors = intArrayOf(accent, Color.rgb(235, 166, 76), Color.rgb(107, 191, 170))
            for (index in 0 until 12) {
                paint.color = colors[index % colors.size]
                paint.alpha = 220 * opacity / 255
                paint.strokeWidth = min(2f * density, band * 0.45f)
                val along = (index / 4 + 1) / 4f
                val offset = band * if (index % 2 == 0) 0.38f else 0.73f
                val dash = min(3f * density, band * 0.6f)
                when (index % 4) {
                    0 -> canvas.drawLine(width * along, offset, width * along + dash, offset + dash / 2f, paint)
                    1 -> canvas.drawLine(width - offset, height * along, width - offset - dash / 2f, height * along + dash, paint)
                    2 -> canvas.drawLine(width * along, height - offset, width * along + dash, height - offset - dash / 2f, paint)
                    3 -> canvas.drawLine(offset, height * along, offset + dash / 2f, height * along + dash, paint)
                }
            }
        }
        canvas.restoreToCount(save)
    }
}

internal class ArrivalAccentDrawable(
    private val stage: ArrivalStage,
    private val accent: Int,
    private val density: Float,
) : Drawable() {
    private var opacity = 255
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        ArrivalAccent.draw(canvas, bounds.width().toFloat(), bounds.height().toFloat(), density, accent, stage, opacity)
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
