package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.santiagorodriguez.countaway.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Stable count/action columns, with natural reflow for readable event content. */
class HomeEventRow @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {
    private val icon: View get() = findViewById(R.id.eventIcon)
    private val title: TextView get() = findViewById(R.id.eventTitle)
    private val meta: TextView get() = findViewById(R.id.eventMeta)
    private val tile: View get() = findViewById(R.id.eventCountTile)
    private val status: HomeCountTextView get() = findViewById(R.id.eventStatus)
    private val arrival: View get() = findViewById(R.id.eventArrival)
    private val actions: View get() = findViewById(R.id.eventActions)
    private val notchCurve = Path()
    private val notchFill = Path()
    private val notchSurface = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.surface_secondary) }
    private val notchOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.outline)
        style = Paint.Style.STROKE
        strokeWidth = dp(1).toFloat()
    }
    private var wideText = false
    private var stackedIcon = false
    private var showArrival = false
    private var leftHeight = 0
    private var contentHeight = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED)
            dp(320) else MeasureSpec.getSize(widthMeasureSpec)
        val inner = contentWidth(width)
        icon.measure(exact(dp(48)), exact(dp(48)))
        arrival.measure(exact(dp(18)), exact(dp(18)))
        val actionSize = if (actions.visibility == View.GONE) 0 else dp(48)
        actions.measure(exact(actionSize), exact(actionSize))
        val tileWidth = status.preferredTileWidth().coerceAtMost(inner)
        val tileHeight = status.preferredTileHeight()
        tile.measure(exact(tileWidth), exact(tileHeight))
        showArrival = arrival.visibility == View.VISIBLE && (arrival.tag as? Int ?: 0) != 0
        status.prepareForWidth(tileWidth)
        status.forceLayout()
        status.measure(exact(tileWidth), exact(tileHeight - if (showArrival) dp(22) else 0))

        val leftWidth = (inner - tileWidth - dp(12)).coerceAtLeast(1)
        val wordWidth = max(maxWordWidth(title), maxWordWidth(meta))
        // Only a genuine word-fit constraint allows a full-width text band.
        // The count tile never changes width in response to an event or status.
        wideText = leftWidth < dp(80) || (wordWidth > leftWidth && wordWidth <= inner)
        val textArea = if (wideText) inner else leftWidth
        val besideIcon = (textArea - dp(60)).coerceAtLeast(1)
        stackedIcon = besideIcon < dp(64) || wordWidth > besideIcon
        val textWidth = if (stackedIcon) textArea else besideIcon
        measureText(title, textWidth)
        measureText(meta, textWidth)
        val textHeight = title.measuredHeight + dp(3) + meta.measuredHeight
        leftHeight = if (stackedIcon) dp(48 + 8) + textHeight else max(dp(48), textHeight)
        contentHeight = if (wideText) leftHeight + dp(8) + tileHeight else max(leftHeight, tileHeight)
        setMeasuredDimension(resolveSize(width, widthMeasureSpec),
            resolveSize(max(suggestedMinimumHeight, contentHeight + paddingTop + paddingBottom), heightMeasureSpec))
    }

    private fun contentWidth(width: Int): Int {
        val end = if (actions.visibility == View.GONE) paddingEnd else max(paddingEnd, dp(52))
        return (width - paddingStart - end).coerceAtLeast(1)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val inner = contentWidth(width)
        val y = paddingTop + ((height - paddingTop - paddingBottom - contentHeight) / 2).coerceAtLeast(0)
        val leftY = if (wideText) y else y + (contentHeight - leftHeight) / 2
        val textHeight = title.measuredHeight + dp(3) + meta.measuredHeight
        place(icon, 0, if (stackedIcon) leftY else leftY + (leftHeight - icon.measuredHeight) / 2)
        val textY = if (stackedIcon) leftY + dp(56) else leftY + (leftHeight - textHeight) / 2
        val textStart = if (stackedIcon) 0 else dp(60)
        place(title, textStart, textY)
        place(meta, textStart, textY + title.measuredHeight + dp(3))
        val tileY = if (wideText) y + leftHeight + dp(8) else y + (contentHeight - tile.measuredHeight) / 2
        val tileStart = inner - tile.measuredWidth
        place(tile, tileStart, tileY)
        if (showArrival) {
            place(arrival, tileStart + (tile.measuredWidth - arrival.measuredWidth) / 2, tileY + dp(4))
            place(status, tileStart, tileY + dp(22))
        } else {
            arrival.layout(0, 0, 0, 0)
            place(status, tileStart, tileY)
        }
        if (actions.visibility != View.GONE) {
            val x = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) 0 else width - actions.measuredWidth
            val actionY = tileY + (tile.measuredHeight - actions.measuredHeight) / 2
            actions.layout(x, actionY, x + actions.measuredWidth, actionY + actions.measuredHeight)
        } else actions.layout(0, 0, 0, 0)
        updateActionNotch()
    }

    private fun updateActionNotch() {
        notchCurve.rewind()
        notchFill.rewind()
        if (actions.visibility != View.VISIBLE || actions.width == 0) return
        val edge = width - notchOutline.strokeWidth / 2f
        val inset = width - dp(14).toFloat()
        val center = (actions.top + actions.bottom) / 2f
        val top = center - dp(14)
        val bottom = center + dp(14)
        val bend = dp(6).toFloat()
        notchCurve.moveTo(edge, top)
        notchCurve.cubicTo(edge, top + bend, inset, top + bend, inset, center)
        notchCurve.cubicTo(inset, bottom - bend, edge, bottom - bend, edge, bottom)
        notchFill.set(notchCurve)
        notchFill.lineTo(width.toFloat(), bottom)
        notchFill.lineTo(width.toFloat(), top)
        notchFill.close()
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (!notchFill.isEmpty && actions.visibility == View.VISIBLE) {
            val saved = canvas.save()
            canvas.clipRect(0, 0, width, height)
            if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                canvas.translate(width.toFloat(), 0f)
                canvas.scale(-1f, 1f)
            }
            canvas.drawPath(notchFill, notchSurface)
            canvas.drawPath(notchCurve, notchOutline)
            canvas.restoreToCount(saved)
        }
        super.dispatchDraw(canvas)
    }

    private fun place(view: View, start: Int, y: Int) {
        val x = if (layoutDirection == View.LAYOUT_DIRECTION_RTL)
            width - paddingRight - start - view.measuredWidth else paddingLeft + start
        view.layout(x, y, x + view.measuredWidth, y + view.measuredHeight)
    }
    private fun measureText(view: TextView, width: Int) {
        view.forceLayout()
        view.measure(exact(width), unspecified())
    }
    private fun maxWordWidth(view: TextView): Int = ceil(view.text.toString().split(Regex("\\s+"))
        .maxOfOrNull { view.paint.measureText(it).toDouble() } ?: 0.0).toInt()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun exact(value: Int) = MeasureSpec.makeMeasureSpec(value.coerceAtLeast(0), MeasureSpec.EXACTLY)
    private fun unspecified() = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
}
