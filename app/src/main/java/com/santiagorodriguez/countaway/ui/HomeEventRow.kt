package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.santiagorodriguez.countaway.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Reflows event content beside one independent, native trailing-edge action. */
class HomeEventRow @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {
    private val icon: View get() = findViewById(R.id.eventIcon)
    private val title: TextView get() = findViewById(R.id.eventTitle)
    private val meta: TextView get() = findViewById(R.id.eventMeta)
    private val status: TextView get() = findViewById(R.id.eventStatus)
    private val arrival: View get() = findViewById(R.id.eventArrival)
    private val actions: View get() = findViewById(R.id.eventActions)
    private val combinedPill = requireNotNull(context.getDrawable(R.drawable.status_pill)).mutate()
    private val pillBounds = Rect()
    private val notchCurve = Path()
    private val notchFill = Path()
    private val notchSurface = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.surface_secondary)
    }
    private val notchOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.outline)
        style = Paint.Style.STROKE
        strokeWidth = dp(1).toFloat()
    }
    private var expanded = false
    private var stackedTitle = false
    private var stackedDetails = false
    private var showArrival = false
    private var railWidth = 0
    private var headerHeight = 0
    private var contentHeight = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED)
            dp(320) else MeasureSpec.getSize(widthMeasureSpec)
        icon.measure(exact(dp(48)), exact(dp(48)))
        arrival.measure(exact(dp(18)), exact(dp(18)))
        val actionSize = if (actions.visibility == View.GONE) 0 else dp(48)
        actions.measure(exact(actionSize), exact(actionSize))
        measureContent(contentWidth(width))
        contentHeight = max(contentHeight, actionSize)
        // One background contains the real status and optional arrival views.
        // Alpha preserves the TextView's XML-inflated padding and font size.
        if (status.background.alpha != 0) status.background.mutate().alpha = 0
        setMeasuredDimension(resolveSize(width, widthMeasureSpec),
            resolveSize(max(suggestedMinimumHeight, contentHeight + paddingTop + paddingBottom), heightMeasureSpec))
    }

    private fun contentWidth(width: Int): Int {
        // The existing end padding is part of the 48dp target clearance, not an
        // additional column. Actionless adapters recover their complete width.
        val end = if (actions.visibility == View.GONE) paddingEnd else max(paddingEnd, dp(48 + 4))
        return (width - paddingStart - end).coerceAtLeast(1)
    }

    private fun measureContent(inner: Int) {
        val railLimit = min(dp(112), (inner * 0.38f).roundToInt()).coerceAtLeast(dp(54)).coerceAtMost(inner)
        measureStatus(railLimit)
        val plainRail = status.measuredWidth
        val artWidth = dp(26)
        val requestedArt = arrival.visibility == View.VISIBLE && (arrival.tag as? Int ?: 0) != 0
        val plainExpanded = measureSide(inner, plainRail)
        showArrival = requestedArt && plainRail + artWidth <= railLimit
        railWidth = plainRail
        if (showArrival) {
            railWidth += artWidth
            val decoratedExpanded = measureSide(inner, railWidth)
            if (decoratedExpanded && !plainExpanded) {
                showArrival = false
                railWidth = plainRail
                expanded = measureSide(inner, railWidth)
            } else {
                expanded = decoratedExpanded
            }
        } else {
            expanded = plainExpanded
        }

        stackedTitle = false
        stackedDetails = false
        if (!expanded) {
            contentHeight = max(dp(48), max(title.measuredHeight + dp(3) + meta.measuredHeight,
                status.measuredHeight))
            return
        }

        measureText(title, (inner - dp(60)).coerceAtLeast(1))
        // Give an ordinary long word the full content width before letting the
        // native TextView split it beside the icon. Keep the icon above it.
        stackedTitle = splitsWord(title) && maxWordWidth(title) <= inner
        if (stackedTitle) {
            measureText(title, inner)
            headerHeight = dp(48 + 8) + title.measuredHeight
        } else {
            headerHeight = max(dp(48), title.measuredHeight)
        }
        val statusLimit = min(inner, max(railLimit,
            maxWordWidth(status) + status.compoundPaddingLeft + status.compoundPaddingRight))
        measureStatus(statusLimit)
        val plainExpandedRail = status.measuredWidth
        showArrival = showArrival && plainExpandedRail + artWidth <= railLimit
        railWidth = plainExpandedRail + if (showArrival) artWidth else 0
        val detailWidth = (inner - railWidth - dp(12)).coerceAtLeast(1)
        measureText(meta, detailWidth)
        stackedDetails = detailWidth < dp(100) || meta.lineCount > 2 || splitsWord(meta) || splitsWord(status)
        if (showArrival && stackedDetails) {
            val plainDetailWidth = (inner - plainExpandedRail - dp(12)).coerceAtLeast(1)
            measureText(meta, plainDetailWidth)
            if (plainDetailWidth >= dp(100) && meta.lineCount <= 2 && !splitsWord(meta) && !splitsWord(status)) {
                showArrival = false
                railWidth = plainExpandedRail
                stackedDetails = false
            } else {
                // The decorated width is still the selected arrangement.
                measureText(meta, detailWidth)
            }
        }
        val bodyHeight = if (stackedDetails) {
            measureText(meta, inner)
            measureStatus(inner)
            showArrival = showArrival && status.measuredWidth + artWidth <= inner
            railWidth = status.measuredWidth + if (showArrival) artWidth else 0
            meta.measuredHeight + dp(4) + status.measuredHeight
        } else {
            max(meta.measuredHeight, status.measuredHeight)
        }
        contentHeight = headerHeight + dp(4) + bodyHeight
    }

    private fun measureSide(inner: Int, rail: Int): Boolean {
        val sideWidth = (inner - dp(72) - rail).coerceAtLeast(1)
        measureText(title, sideWidth)
        measureText(meta, sideWidth)
        return sideWidth < dp(100) || title.lineCount > 2 || meta.lineCount > 3 || status.lineCount > 2 ||
            splitsWord(title) || splitsWord(meta) || splitsWord(status)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val inner = contentWidth(width)
        val availableHeight = height - paddingTop - paddingBottom
        val y = paddingTop + ((availableHeight - contentHeight) / 2).coerceAtLeast(0)
        if (!expanded) {
            place(icon, 0, y + (contentHeight - icon.measuredHeight) / 2)
            val textY = y + (contentHeight - title.measuredHeight - dp(3) - meta.measuredHeight) / 2
            place(title, dp(60), textY)
            place(meta, dp(60), textY + title.measuredHeight + dp(3))
            placeStatus(inner, y + (contentHeight - status.measuredHeight) / 2)
        } else {
            place(icon, 0, if (stackedTitle) y else y + (headerHeight - icon.measuredHeight) / 2)
            place(title, if (stackedTitle) 0 else dp(60),
                if (stackedTitle) y + dp(48 + 8) else y + (headerHeight - title.measuredHeight) / 2)
            val bodyY = y + headerHeight + dp(4)
            place(meta, 0, bodyY)
            placeStatus(inner, if (stackedDetails) bodyY + meta.measuredHeight + dp(4) else bodyY)
        }
        if (actions.visibility != View.GONE) {
            val x = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) 0 else width - actions.measuredWidth
            val actionY = y + (contentHeight - actions.measuredHeight) / 2
            actions.layout(x, actionY, x + actions.measuredWidth, actionY + actions.measuredHeight)
        } else {
            actions.layout(0, 0, 0, 0)
        }
        updateActionNotch()
    }

    private fun placeStatus(inner: Int, y: Int) {
        val start = inner - railWidth
        if (showArrival) {
            place(arrival, start + dp(4), y + (status.measuredHeight - arrival.measuredHeight) / 2)
            place(status, start + dp(26), y)
        } else {
            arrival.layout(0, 0, 0, 0)
            place(status, start, y)
        }
        val left = if (layoutDirection == View.LAYOUT_DIRECTION_RTL)
            width - paddingRight - start - railWidth else paddingLeft + start
        pillBounds.set(left, y, left + railWidth, y + status.measuredHeight)
    }

    private fun updateActionNotch() {
        notchCurve.rewind()
        notchFill.rewind()
        if (actions.visibility != View.VISIBLE || actions.width == 0) return
        // Visual dimensions are independent from the full native hit target.
        // The same 14 x 28dp treatment is used for compact and reflowed cards.
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
        combinedPill.bounds = pillBounds
        combinedPill.draw(canvas)
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
        // Each trial must refresh TextView.layout, not only cached dimensions.
        view.forceLayout()
        view.measure(exact(width), unspecified())
    }

    private fun measureStatus(limit: Int) {
        status.forceLayout()
        status.measure(MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST), unspecified())
    }

    private fun maxWordWidth(view: TextView): Int = ceil(view.text.toString().split(Regex("\\s+"))
        .maxOfOrNull { view.paint.measureText(it).toDouble() } ?: 0.0).toInt()

    private fun splitsWord(view: TextView): Boolean {
        val layout = view.layout ?: return false
        val text = view.text
        return (0 until layout.lineCount - 1).any { line ->
            val end = layout.getLineEnd(line)
            end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun exact(value: Int) = MeasureSpec.makeMeasureSpec(value.coerceAtLeast(0), MeasureSpec.EXACTLY)
    private fun unspecified() = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
}
