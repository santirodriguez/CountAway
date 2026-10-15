package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.AttributeSet
import android.view.TouchDelegate
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
    private val timeDetail: TextView get() = findViewById(R.id.eventTimeDetail)
    private var timedCompact = false
    private var timedMetaFullWidth = false
    private var upperHeight = 0
    private var metaBandHeight = 0

    fun bindTimedCompact(enabled: Boolean) {
        if (timedCompact != enabled) {
            timedCompact = enabled
            requestLayout()
        }
    }
    private val tile: View get() = findViewById(R.id.eventCountTile)
    private val status: HomeCountTextView get() = findViewById(R.id.eventStatus)
    private val arrival: View get() = findViewById(R.id.eventArrival)
    private val actions: View get() = findViewById(R.id.eventActions)
    private val sectionLabel: TextView get() = findViewById(R.id.eventSectionLabel)
    private val sectionLine: View get() = findViewById(R.id.eventSectionLine)
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
    private var hasSectionHeader = false

    fun bindSectionHeader(label: CharSequence?, accessibilityLabel: CharSequence?) {
        val visible = label != null
        sectionLabel.text = label ?: ""
        sectionLabel.contentDescription = accessibilityLabel
        sectionLabel.visibility = if (visible) View.VISIBLE else View.GONE
        sectionLine.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible != hasSectionHeader) {
            hasSectionHeader = visible
            setBackgroundResource(if (visible) R.drawable.surface_card_clickable_sectioned else R.drawable.surface_card_clickable)
            setPaddingRelative(dp(16), dp(if (visible) 30 else 10), dp(16), dp(10))
        }
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED)
            dp(320) else MeasureSpec.getSize(widthMeasureSpec)
        val inner = contentWidth(width)
        if (hasSectionHeader) {
            sectionLabel.measure(atMost((width - dp(24)).coerceAtLeast(1)), unspecified())
            val lineWidth = (width - dp(24) - sectionLabel.measuredWidth - dp(8)).coerceAtLeast(dp(24))
            sectionLine.measure(exact(lineWidth), exact(dp(1)))
        } else {
            sectionLabel.measure(exact(0), exact(0))
            sectionLine.measure(exact(0), exact(0))
        }
        icon.measure(exact(dp(48)), exact(dp(48)))
        arrival.measure(exact(dp(18)), exact(dp(18)))
        val actionWidth = if (actions.visibility == View.GONE) 0 else dp(20)
        val actionHeight = if (actions.visibility == View.GONE) 0 else dp(48)
        actions.measure(exact(actionWidth), exact(actionHeight))
        // Timed events have one numeric line and one unit line in this tile.
        // Preserve the classic date-only tile's existing reserved height.
        status.useCompactTimedTile(timedCompact)
        val tileWidth = status.preferredTileWidth().coerceAtMost(inner)
        val tileHeight = status.preferredTileHeight()
        tile.measure(exact(tileWidth), exact(tileHeight))
        showArrival = arrival.visibility == View.VISIBLE && (arrival.tag as? Int ?: 0) != 0
        status.prepareForWidth(tileWidth)
        status.forceLayout()
        status.measure(exact(tileWidth), exact(tileHeight - if (showArrival) dp(20) else 0))

        val leftWidth = (inner - tileWidth - dp(8)).coerceAtLeast(1)
        val wordWidth = if (timedCompact) 0 else max(maxWordWidth(title), maxWordWidth(meta))
        // Only a genuine word-fit constraint allows a full-width text band.
        // The count tile never changes width in response to an event or status.
        wideText = leftWidth < dp(80) || (wordWidth > leftWidth && wordWidth <= inner)
        val textArea = if (wideText) inner else leftWidth
        val besideIcon = (textArea - dp(60)).coerceAtLeast(1)
        stackedIcon = besideIcon < dp(64) || wordWidth > besideIcon
        val textWidth = if (stackedIcon) textArea else besideIcon
        // Measure actual line count before selecting the responsive layout.
        // A wider 411dp screen can still wrap localized date and zone text
        // into three lines at 200% font size.
        measureText(title, textWidth)
        measureText(meta, textWidth)
        timedMetaFullWidth = timedCompact &&
            resources.configuration.fontScale >= 1.6f &&
            (textWidth < dp(200) || (meta.layout?.lineCount ?: 0) > 2)
        if (timedMetaFullWidth) measureText(meta, inner)
        if (timeDetail.visibility == View.VISIBLE) {
            // Keep the whole clock legible at 200% text: use one compact
            // full-width line below the main title/date/icon/count row.
            measureText(timeDetail, if (timedCompact) inner else textWidth)
        } else timeDetail.measure(exact(0), exact(0))
        val inlineDetailHeight =
            if (!timedCompact && timeDetail.visibility == View.VISIBLE) dp(2) + timeDetail.measuredHeight else 0
        val textHeight = title.measuredHeight + dp(3) +
            (if (timedMetaFullWidth) 0 else meta.measuredHeight) + inlineDetailHeight
        leftHeight = if (stackedIcon) dp(48 + 8) + textHeight else max(dp(48), textHeight)
        upperHeight = if (wideText) leftHeight + dp(8) + tileHeight else max(leftHeight, tileHeight)
        metaBandHeight = if (timedMetaFullWidth) dp(3) + meta.measuredHeight else 0
        val timedBandHeight =
            if (timedCompact && timeDetail.visibility == View.VISIBLE) dp(3) + timeDetail.measuredHeight else 0
        contentHeight = upperHeight + metaBandHeight + timedBandHeight
        val minimumHeight = suggestedMinimumHeight + if (hasSectionHeader) dp(20) else 0
        setMeasuredDimension(resolveSize(width, widthMeasureSpec),
            resolveSize(max(minimumHeight, contentHeight + paddingTop + paddingBottom), heightMeasureSpec))
    }

    private fun contentWidth(width: Int): Int {
        // Only the 20dp visual rail plus a 2dp gap consumes layout width.
        // TouchDelegate restores the full 48dp hit target without pushing content left.
        val end = if (actions.visibility == View.GONE) paddingEnd else max(paddingEnd, dp(22))
        return (width - paddingStart - end).coerceAtLeast(1)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        layoutSectionHeader()
        val inner = contentWidth(width)
        val y = paddingTop + ((height - paddingTop - paddingBottom - contentHeight) / 2).coerceAtLeast(0)
        val leftY = if (wideText) y else y + (upperHeight - leftHeight) / 2
        val inlineDetailHeight =
            if (!timedCompact && timeDetail.visibility == View.VISIBLE) dp(2) + timeDetail.measuredHeight else 0
        val textHeight = title.measuredHeight + dp(3) +
            (if (timedMetaFullWidth) 0 else meta.measuredHeight) + inlineDetailHeight
        place(icon, 0, if (stackedIcon) leftY else leftY + (leftHeight - icon.measuredHeight) / 2)
        val textY = if (stackedIcon) leftY + dp(56) else leftY + (leftHeight - textHeight) / 2
        val textStart = if (stackedIcon) 0 else dp(60)
        place(title, textStart, textY)
        if (timedMetaFullWidth) {
            place(meta, 0, y + upperHeight + dp(3))
        } else {
            place(meta, textStart, textY + title.measuredHeight + dp(3))
        }
        if (timeDetail.visibility == View.VISIBLE) {
            if (timedCompact) {
                place(timeDetail, 0, y + upperHeight + metaBandHeight + dp(3))
            } else {
                place(timeDetail, textStart, textY + title.measuredHeight + dp(3) + meta.measuredHeight + dp(2))
            }
        } else timeDetail.layout(0, 0, 0, 0)
        val tileY = if (wideText) y + leftHeight + dp(8) else y + (upperHeight - tile.measuredHeight) / 2
        val tileStart = inner - tile.measuredWidth
        place(tile, tileStart, tileY)
        if (showArrival) {
            place(arrival, tileStart + (tile.measuredWidth - arrival.measuredWidth) / 2, tileY + dp(2))
            place(status, tileStart, tileY + dp(20))
        } else {
            arrival.layout(0, 0, 0, 0)
            place(status, tileStart, tileY)
        }
        if (actions.visibility != View.GONE) {
            val x = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) 0 else width - actions.measuredWidth
            val actionY = tileY + (tile.measuredHeight - actions.measuredHeight) / 2
            actions.layout(x, actionY, x + actions.measuredWidth, actionY + actions.measuredHeight)
        } else actions.layout(0, 0, 0, 0)
        updateActionTouchDelegate()
        updateActionNotch()
    }

    private fun layoutSectionHeader() {
        if (!hasSectionHeader) {
            sectionLabel.layout(0, 0, 0, 0)
            sectionLine.layout(0, 0, 0, 0)
            return
        }
        val headerHeight = dp(20)
        val edge = dp(8)
        val gap = dp(8)
        val labelTop = ((headerHeight - sectionLabel.measuredHeight) / 2).coerceAtLeast(0)
        val lineTop = (headerHeight - sectionLine.measuredHeight) / 2
        if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            val labelRight = width - edge
            val labelLeft = labelRight - sectionLabel.measuredWidth
            sectionLabel.layout(labelLeft, labelTop, labelRight, labelTop + sectionLabel.measuredHeight)
            sectionLine.layout(edge, lineTop, (labelLeft - gap).coerceAtLeast(edge), lineTop + sectionLine.measuredHeight)
        } else {
            val labelLeft = edge
            val labelRight = labelLeft + sectionLabel.measuredWidth
            sectionLabel.layout(labelLeft, labelTop, labelRight, labelTop + sectionLabel.measuredHeight)
            sectionLine.layout(labelRight + gap, lineTop, width - edge, lineTop + sectionLine.measuredHeight)
        }
    }

    private fun updateActionTouchDelegate() {
        if (actions.visibility != View.VISIBLE || actions.width == 0) {
            touchDelegate = null
            return
        }
        val target = Rect(actions.left, actions.top, actions.right, actions.bottom)
        if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            target.right = (target.left + dp(48)).coerceAtMost(width)
        } else {
            target.left = (target.right - dp(48)).coerceAtLeast(0)
        }
        touchDelegate = TouchDelegate(target, actions)
    }

    private fun updateActionNotch() {
        notchCurve.rewind()
        notchFill.rewind()
        if (actions.visibility != View.VISIBLE || actions.width == 0) return
        val edge = width - notchOutline.strokeWidth / 2f
        val inset = width - dp(14).toFloat()
        val center = (actions.top + actions.bottom) / 2f
        val top = center - dp(16)
        val bottom = center + dp(16)
        val bend = dp(7).toFloat()
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
    private fun atMost(value: Int) = MeasureSpec.makeMeasureSpec(value.coerceAtLeast(0), MeasureSpec.AT_MOST)
    private fun unspecified() = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
}
