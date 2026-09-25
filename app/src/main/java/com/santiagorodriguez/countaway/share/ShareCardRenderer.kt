package com.santiagorodriguez.countaway.share

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownCalculator
import com.santiagorodriguez.countaway.countdown.CountdownOccurrenceResolver
import com.santiagorodriguez.countaway.countdown.CountdownStatus
import com.santiagorodriguez.countaway.model.EventIcon
import com.santiagorodriguez.countaway.model.RepeatRule
import com.santiagorodriguez.countaway.ui.EventIconPresentation
import com.santiagorodriguez.countaway.ui.ThemeManager
import com.santiagorodriguez.countaway.widget.WidgetBackground
import com.santiagorodriguez.countaway.widget.WidgetBackgroundRenderer
import com.santiagorodriguez.countaway.widget.WidgetPaletteResolver
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal data class ShareCardContent(
    val iconRes: Int,
    val title: String,
    val primaryText: String,
    val secondaryText: String?,
    val dateText: String,
    val recurrenceText: String?,
    val date: LocalDate,
)

internal object ShareCardContentFactory {
    fun create(
        context: Context,
        title: String,
        date: LocalDate,
        icon: EventIcon,
        repeatRule: RepeatRule,
        today: LocalDate,
    ): ShareCardContent {
        val displayDate = CountdownOccurrenceResolver.displayDate(date, repeatRule, today)
        val value = CountdownCalculator.value(today, displayDate)
        val primaryText = when (value.status) {
            CountdownStatus.FUTURE,
            CountdownStatus.THREE_DAYS,
            CountdownStatus.TWO_DAYS,
            -> value.days.coerceAtLeast(0).toString()
            CountdownStatus.TOMORROW -> context.getString(R.string.status_tomorrow)
            CountdownStatus.TODAY -> context.getString(R.string.status_today)
            CountdownStatus.DONE -> value.elapsedDays.toString()
        }
        val secondaryText = when (value.status) {
            CountdownStatus.FUTURE,
            CountdownStatus.THREE_DAYS,
            CountdownStatus.TWO_DAYS,
            -> context.getString(R.string.widget_days_left)
            CountdownStatus.TOMORROW,
            CountdownStatus.TODAY,
            -> null
            CountdownStatus.DONE -> context.getString(
                if (value.elapsedDays == 1L) R.string.widget_day_ago else R.string.widget_days_ago,
            )
        }
        val locale = context.resources.configuration.locales[0]
        val dateText = displayDate.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale),
        )
        val recurrenceText = repeatLabelRes(repeatRule)?.let(context::getString)

        return ShareCardContent(
            iconRes = EventIconPresentation.drawableRes(icon),
            title = title,
            primaryText = primaryText,
            secondaryText = secondaryText,
            dateText = dateText,
            recurrenceText = recurrenceText,
            date = displayDate,
        )
    }

    private fun repeatLabelRes(repeatRule: RepeatRule): Int? = when (repeatRule) {
        RepeatRule.NONE -> null
        RepeatRule.WEEKLY -> R.string.repeat_weekly
        RepeatRule.MONTHLY -> R.string.repeat_monthly
        RepeatRule.YEARLY -> R.string.repeat_yearly
    }
}

internal object ShareCardRenderer {
    private const val DESIGN_SIZE_PX = 1080f
    const val SIZE_PX = 768

    fun render(
        context: Context,
        content: ShareCardContent,
        dark: Boolean,
    ): Bitmap {
        val bitmap = WidgetBackgroundRenderer.renderShareRidge(
            dark = dark,
            sizePx = SIZE_PX,
        )
        val canvas = Canvas(bitmap)
        val palette = WidgetPaletteResolver.resolve(WidgetBackground.MONOGRAM, dark)
        val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val bold = Typeface.create("sans-serif", Typeface.BOLD)
        val scale = SIZE_PX / DESIGN_SIZE_PX

        canvas.save()
        canvas.scale(scale, scale)

        drawBrandWordmark(
            context = context,
            canvas = canvas,
        )

        drawIcon(
            context = context,
            canvas = canvas,
            iconRes = content.iconRes,
            tint = palette.accentTextColor,
            left = 882,
            top = 54,
            size = 122,
        )

        val titlePaint = textPaint(palette.primaryTextColor, 70f, medium)
        drawTwoLineText(
            canvas = canvas,
            text = content.title,
            paint = titlePaint,
            x = 76f,
            firstBaseline = 226f,
            maxWidth = 900f,
            lineHeight = 82f,
        )

        val primaryMaxSize = if (content.secondaryText == null) 166f else 270f
        val primaryPaint = textPaint(palette.accentTextColor, primaryMaxSize, bold)
        fitText(primaryPaint, content.primaryText, 900f, minSize = 92f)
        canvas.drawText(content.primaryText, 76f, 590f, primaryPaint)

        content.secondaryText?.let { secondary ->
            val secondaryPaint = textPaint(palette.secondaryTextColor, 58f, medium)
            canvas.drawText(secondary, 84f, 666f, secondaryPaint)
        }

        val datePaint = textPaint(palette.primaryTextColor, 50f, medium)
        val fittedDate = ellipsize(content.dateText, datePaint, 900f)
        canvas.drawText(fittedDate, 76f, 770f, datePaint)

        content.recurrenceText?.let { recurrence ->
            val recurrencePaint = textPaint(palette.secondaryTextColor, 39f, medium)
            canvas.drawText(ellipsize(recurrence, recurrencePaint, 760f), 78f, 828f, recurrencePaint)
        }

        canvas.restore()
        return bitmap
    }

    fun resolveDark(context: Context): Boolean {
        val systemDark = context.applicationContext.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return when (ThemeManager.currentTheme(context)) {
            ThemeManager.AppTheme.SYSTEM -> systemDark
            ThemeManager.AppTheme.LIGHT -> false
            ThemeManager.AppTheme.DARK -> true
        }
    }

    private fun drawBrandWordmark(
        context: Context,
        canvas: Canvas,
    ) {
        val drawable = context.getDrawable(R.drawable.brand_logo)?.mutate() ?: return
        drawable.setBounds(
            28,
            0,
            348,
            200,
        )
        drawable.draw(canvas)
    }

    private fun drawIcon(
        context: Context,
        canvas: Canvas,
        iconRes: Int,
        tint: Int?,
        left: Int,
        top: Int,
        size: Int,
    ) {
        val drawable = context.getDrawable(iconRes)?.mutate() ?: return
        tint?.let(drawable::setTint)
        drawable.setBounds(left, top, left + size, top + size)
        drawable.draw(canvas)
    }

    private fun drawTwoLineText(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        firstBaseline: Float,
        maxWidth: Float,
        lineHeight: Float,
    ) {
        if (paint.measureText(text) <= maxWidth) {
            canvas.drawText(text, x, firstBaseline, paint)
            return
        }

        val rawBreak = paint.breakText(text, true, maxWidth, null).coerceAtLeast(1)
        val preferredBreak = text.lastIndexOf(' ', startIndex = rawBreak - 1)
            .takeIf { it > 0 }
            ?: rawBreak
        val first = text.substring(0, preferredBreak).trimEnd()
        val rest = text.substring(preferredBreak).trimStart()
        canvas.drawText(first, x, firstBaseline, paint)
        canvas.drawText(
            ellipsize(rest, paint, maxWidth),
            x,
            firstBaseline + lineHeight,
            paint,
        )
    }

    private fun fitText(
        paint: TextPaint,
        text: String,
        maxWidth: Float,
        minSize: Float,
    ) {
        while (paint.textSize > minSize && paint.measureText(text) > maxWidth) {
            paint.textSize -= 4f
        }
    }

    private fun ellipsize(text: String, paint: TextPaint, maxWidth: Float): String =
        TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()

    private fun textPaint(color: Int, size: Float, typeface: Typeface): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            this.typeface = typeface
        }
}
