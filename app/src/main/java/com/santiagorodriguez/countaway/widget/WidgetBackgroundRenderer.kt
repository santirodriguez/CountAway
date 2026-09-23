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
        val scaledDensity = density * scale

        if (background == WidgetBackground.MONOGRAM) {
            return renderRidge(width, height, scaledDensity, dark)
        }

        val configuration = Configuration(context.resources.configuration)
        val nightMode = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        configuration.uiMode =
            (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        val appearanceContext = context.createConfigurationContext(configuration)
        val drawable = requireNotNull(
            appearanceContext.getDrawable(background.drawableRes(dark)),
        ).mutate()

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)

            canvas.save()
            canvas.clipPath(
                roundedClip(
                    width = width.toFloat(),
                    height = height.toFloat(),
                    radius = 20f * scaledDensity,
                ),
            )
            drawAccent(
                canvas = canvas,
                background = background,
                dark = dark,
                width = width.toFloat(),
                height = height.toFloat(),
                density = scaledDensity,
            )
            canvas.restore()
        }
    }

    private fun drawAccent(
        canvas: Canvas,
        background: WidgetBackground,
        dark: Boolean,
        width: Float,
        height: Float,
        density: Float,
    ) {
        when (background) {
            WidgetBackground.HORIZON -> drawHorizon(canvas, dark, width, height, density)
            WidgetBackground.FOREST -> drawForest(canvas, dark, width, height, density)
            WidgetBackground.PULSE -> drawPulse(canvas, dark, width, height, density)
            WidgetBackground.BREEZE -> drawBreeze(canvas, dark, width, height, density)
            WidgetBackground.EMBER -> drawEmber(canvas, dark, width, height, density)
            else -> Unit
        }
    }

    private fun drawHorizon(
        canvas: Canvas,
        dark: Boolean,
        width: Float,
        height: Float,
        density: Float,
    ) {
        val lineColor = if (dark) Color.rgb(186, 221, 255) else Color.rgb(41, 77, 136)
        val sunColor = if (dark) Color.rgb(255, 193, 124) else Color.rgb(220, 122, 75)
        val stroke = accentStroke(width, height, density)
        val centerX = width * 0.51f
        val centerY = height * 0.84f
        val radius = minOf(width, height) * 0.16f

        canvas.drawArc(
            RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius),
            180f,
            180f,
            false,
            accentPaint(sunColor, if (dark) 34 else 24, stroke),
        )
        canvas.drawPath(
            Path().apply {
                moveTo(-width * 0.04f, height * 0.80f)
                cubicTo(
                    width * 0.24f, height * 0.70f,
                    width * 0.42f, height * 0.86f,
                    width * 0.64f, height * 0.79f,
                )
                cubicTo(
                    width * 0.82f, height * 0.72f,
                    width * 0.94f, height * 0.77f,
                    width * 1.04f, height * 0.82f,
                )
            },
            accentPaint(lineColor, if (dark) 29 else 21, stroke),
        )
        canvas.drawPath(
            Path().apply {
                moveTo(-width * 0.04f, height * 0.92f)
                cubicTo(
                    width * 0.24f, height * 0.75f,
                    width * 0.43f, height * 0.90f,
                    width * 0.65f, height * 0.85f,
                )
                cubicTo(
                    width * 0.82f, height * 0.81f,
                    width * 0.94f, height * 0.90f,
                    width * 1.04f, height * 0.96f,
                )
            },
            accentPaint(lineColor, if (dark) 21 else 16, stroke * 0.82f),
        )
    }

    private fun drawForest(
        canvas: Canvas,
        dark: Boolean,
        width: Float,
        height: Float,
        density: Float,
    ) {
        val color = if (dark) Color.rgb(168, 230, 196) else Color.rgb(11, 82, 62)
        val stroke = accentStroke(width, height, density)
        val yBases = floatArrayOf(0.64f, 0.75f, 0.86f)

        yBases.forEachIndexed { index, y ->
            val path = Path().apply {
                moveTo(-width * 0.05f, height * y)
                cubicTo(
                    width * 0.12f, height * (y - 0.15f),
                    width * 0.26f, height * (y + 0.07f),
                    width * 0.43f, height * (y - 0.04f),
                )
                cubicTo(
                    width * 0.61f, height * (y - 0.14f),
                    width * 0.76f, height * (y + 0.06f),
                    width * 1.05f, height * (y - 0.08f),
                )
            }
            canvas.drawPath(
                path,
                accentPaint(
                    color,
                    if (dark) 21 + index * 5 else 16 + index * 4,
                    stroke * (0.84f + index * 0.10f),
                ),
            )
        }
    }

    private fun drawPulse(
        canvas: Canvas,
        dark: Boolean,
        width: Float,
        height: Float,
        density: Float,
    ) {
        val color = if (dark) Color.rgb(233, 197, 255) else Color.rgb(55, 42, 66)
        val stroke = accentStroke(width, height, density)
        val centerX = width * 0.88f
        val centerY = height * 0.22f
        val baseRadius = minOf(width, height) * 0.17f

        floatArrayOf(1f, 1.65f, 2.35f).forEachIndexed { index, multiplier ->
            val radius = baseRadius * multiplier
            canvas.drawArc(
                RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius),
                96f,
                205f,
                false,
                accentPaint(
                    color,
                    if (dark) 34 - index * 5 else 26 - index * 4,
                    stroke,
                ),
            )
        }
        canvas.drawCircle(
            centerX,
            centerY,
            maxOf(1.7f * density, minOf(width, height) * 0.017f),
            fillPaint(color, if (dark) 39 else 31),
        )
    }

    private fun drawBreeze(
        canvas: Canvas,
        dark: Boolean,
        width: Float,
        height: Float,
        density: Float,
    ) {
        val color = if (dark) Color.rgb(169, 231, 255) else Color.rgb(7, 88, 117)
        val stroke = accentStroke(width, height, density)
        val starts = floatArrayOf(0.45f, 0.56f, 0.68f, 0.80f)

        starts.forEachIndexed { index, y ->
            val path = Path().apply {
                moveTo(-width * 0.04f, height * y)
                cubicTo(
                    width * 0.20f, height * (y - 0.18f),
                    width * 0.42f, height * (y + 0.13f),
                    width * 0.64f, height * (y - 0.01f),
                )
                cubicTo(
                    width * 0.79f, height * (y - 0.11f),
                    width * 0.91f, height * (y + 0.04f),
                    width * 1.04f, height * (y - 0.08f),
                )
            }
            canvas.drawPath(
                path,
                accentPaint(
                    color,
                    if (dark) 19 + index * 3 else 14 + index * 3,
                    stroke * if (index == 1) 1.65f else 0.86f,
                ),
            )
        }
    }

    private fun drawEmber(
        canvas: Canvas,
        dark: Boolean,
        width: Float,
        height: Float,
        density: Float,
    ) {
        val color = if (dark) Color.rgb(255, 217, 160) else Color.rgb(124, 65, 0)
        val stroke = accentStroke(width, height, density)

        canvas.drawPath(
            Path().apply {
                moveTo(width * 0.46f, height * 1.04f)
                cubicTo(
                    width * 0.68f, height * 0.89f,
                    width * 0.76f, height * 0.62f,
                    width * 0.89f, height * 0.47f,
                )
                cubicTo(
                    width * 0.97f, height * 0.38f,
                    width * 0.96f, height * 0.23f,
                    width * 0.90f, height * 0.06f,
                )
            },
            accentPaint(color, if (dark) 32 else 24, stroke),
        )
        canvas.drawPath(
            Path().apply {
                moveTo(width * 0.57f, height * 1.03f)
                cubicTo(
                    width * 0.70f, height * 0.82f,
                    width * 0.68f, height * 0.69f,
                    width * 0.78f, height * 0.53f,
                )
            },
            accentPaint(color, if (dark) 23 else 18, stroke * 0.76f),
        )

        val dots = arrayOf(
            0.69f to 0.74f,
            0.79f to 0.58f,
            0.86f to 0.43f,
            0.91f to 0.27f,
        )
        dots.forEachIndexed { index, (x, y) ->
            canvas.drawCircle(
                width * x,
                height * y,
                maxOf(1.2f * density, minOf(width, height) * (0.009f + index * 0.0015f)),
                fillPaint(color, if (dark) 27 else 21),
            )
        }
        drawSpark(
            canvas = canvas,
            centerX = width * 0.86f,
            centerY = height * 0.19f,
            radius = minOf(width, height) * 0.037f,
            color = color,
            alpha = if (dark) 31 else 23,
        )
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

        canvas.drawRoundRect(rect, radius, radius, fillPaint(surface, 255))
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

        canvas.save()
        canvas.clipPath(roundedClip(width.toFloat(), height.toFloat(), radius))

        val minDimension = minOf(width, height).toFloat()
        val baseStroke = (minDimension * 0.055f).coerceIn(
            3.2f * scaledDensity,
            10f * scaledDensity,
        )
        val colors = if (dark) {
            intArrayOf(
                Color.rgb(248, 251, 255),
                Color.rgb(73, 145, 255),
                Color.rgb(159, 197, 255),
            )
        } else {
            intArrayOf(
                Color.rgb(255, 255, 255),
                Color.rgb(50, 132, 238),
                Color.rgb(20, 55, 112),
            )
        }
        val startYs = floatArrayOf(0.39f, 0.50f, 0.61f)
        val centerYs = floatArrayOf(0.72f, 0.81f, 0.89f)

        startYs.forEachIndexed { index, startY ->
            val path = Path().apply {
                moveTo(-width * 0.02f, height * startY)
                cubicTo(
                    width * 0.18f, height * (startY + 0.03f),
                    width * 0.34f, height * (centerYs[index] - 0.08f),
                    width * 0.50f, height * centerYs[index],
                )
                cubicTo(
                    width * 0.66f, height * (centerYs[index] - 0.08f),
                    width * 0.82f, height * (startY + 0.03f),
                    width * 1.02f, height * startY,
                )
            }
            canvas.drawPath(
                path,
                accentPaint(
                    colors[index],
                    if (dark) 42 - index * 4 else 36 - index * 3,
                    baseStroke * if (index == 1) 1.15f else 0.84f,
                ),
            )
        }
        canvas.restore()
    }

    private fun drawSpark(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        color: Int,
        alpha: Int,
    ) {
        canvas.drawPath(
            Path().apply {
                moveTo(centerX, centerY - radius)
                cubicTo(
                    centerX + radius * 0.15f, centerY - radius * 0.18f,
                    centerX + radius * 0.18f, centerY - radius * 0.15f,
                    centerX + radius, centerY,
                )
                cubicTo(
                    centerX + radius * 0.18f, centerY + radius * 0.15f,
                    centerX + radius * 0.15f, centerY + radius * 0.18f,
                    centerX, centerY + radius,
                )
                cubicTo(
                    centerX - radius * 0.15f, centerY + radius * 0.18f,
                    centerX - radius * 0.18f, centerY + radius * 0.15f,
                    centerX - radius, centerY,
                )
                cubicTo(
                    centerX - radius * 0.18f, centerY - radius * 0.15f,
                    centerX - radius * 0.15f, centerY - radius * 0.18f,
                    centerX, centerY - radius,
                )
                close()
            },
            fillPaint(color, alpha),
        )
    }

    private fun roundedClip(width: Float, height: Float, radius: Float): Path =
        Path().apply {
            addRoundRect(
                RectF(0f, 0f, width, height),
                radius,
                radius,
                Path.Direction.CW,
            )
        }

    private fun accentStroke(width: Float, height: Float, density: Float): Float =
        maxOf(1.1f * density, minOf(width, height) * 0.018f)

    private fun accentPaint(color: Int, alpha: Int, strokeWidth: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.strokeWidth = strokeWidth
            this.color = color
            this.alpha = alpha.coerceIn(0, 255)
        }

    private fun fillPaint(color: Int, alpha: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
            this.alpha = alpha.coerceIn(0, 255)
        }

    private const val MAX_BITMAP_DIMENSION_PX = 360
}
