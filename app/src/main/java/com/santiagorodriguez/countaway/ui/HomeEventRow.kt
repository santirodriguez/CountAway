package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.santiagorodriguez.countaway.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Keeps the action below the count; expands the title before sacrificing readable text. */
class HomeEventRow @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {
    private val icon: View get() = findViewById(R.id.eventIcon)
    private val title: TextView get() = findViewById(R.id.eventTitle)
    private val meta: TextView get() = findViewById(R.id.eventMeta)
    private val status: TextView get() = findViewById(R.id.eventStatus)
    private val arrival: View get() = findViewById(R.id.eventArrival)
    private val actions: View get() = findViewById(R.id.eventActions)
    private var expanded = false
    private var stackedDetails = false
    private var showArrival = false
    private var railWidth = 0
    private var headerHeight = 0
    private var bodyHeight = 0
    private var contentHeight = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED)
            dp(320) else MeasureSpec.getSize(widthMeasureSpec)
        val inner = (width - paddingLeft - paddingRight).coerceAtLeast(1)
        icon.measure(exact(dp(48)), exact(dp(48)))
        arrival.measure(exact(dp(18)), exact(dp(18)))
        actions.measure(exact(dp(48)), exact(dp(48)))
        val railLimit = min(dp(112), (inner * 0.38f).roundToInt()).coerceAtLeast(dp(54)).coerceAtMost(inner)
        status.measure(MeasureSpec.makeMeasureSpec(railLimit, MeasureSpec.AT_MOST), unspecified())
        val plainRailWidth = max(dp(54), status.measuredWidth).coerceAtMost(inner)
        val arrivalExtra = dp(22)
        val requestedArrival = arrival.visibility == View.VISIBLE && (arrival.tag as? Int ?: 0) != 0
        val decoratedRailWidth = max(dp(54), status.measuredWidth + arrivalExtra).coerceAtMost(inner)

        railWidth = plainRailWidth
        val plainExpanded = measureSide(inner, plainRailWidth)
        showArrival = requestedArrival && status.measuredWidth + arrivalExtra <= railLimit
        if (showArrival) {
            railWidth = decoratedRailWidth
            val decoratedExpanded = measureSide(inner, decoratedRailWidth)
            if (decoratedExpanded && !plainExpanded) {
                showArrival = false
                railWidth = plainRailWidth
                expanded = measureSide(inner, plainRailWidth)
            } else {
                expanded = decoratedExpanded
            }
        } else {
            expanded = plainExpanded
        }

        stackedDetails = false
        if (!expanded) {
            contentHeight = max(dp(48), max(title.measuredHeight + dp(3) + meta.measuredHeight, actionRailHeight()))
        } else {
            measureText(title, (inner - dp(60)).coerceAtLeast(1))
            headerHeight = max(dp(48), title.measuredHeight)
            val statusLimit = min(inner, max(railLimit,
                maxWordWidth(status) + status.compoundPaddingLeft + status.compoundPaddingRight))
            status.measure(MeasureSpec.makeMeasureSpec(statusLimit, MeasureSpec.AT_MOST), unspecified())
            val plainExpandedRail = max(dp(54), status.measuredWidth).coerceAtMost(inner)
            if (showArrival && status.measuredWidth + arrivalExtra <= railLimit) {
                railWidth = max(dp(54), status.measuredWidth + arrivalExtra).coerceAtMost(inner)
            } else {
                showArrival = false
                railWidth = plainExpandedRail
            }
            val detailWidth = (inner - railWidth - dp(12)).coerceAtLeast(1)
            measureText(meta, detailWidth)
            stackedDetails = detailWidth < dp(100) || meta.lineCount > 2 || splitsWord(meta) || splitsWord(status)
            if (showArrival && stackedDetails) {
                val plainDetailWidth = (inner - plainExpandedRail - dp(12)).coerceAtLeast(1)
                measureText(meta, plainDetailWidth)
                val stacksWithoutArrival = plainDetailWidth < dp(100) || meta.lineCount > 2 ||
                    splitsWord(meta) || splitsWord(status)
                if (!stacksWithoutArrival) {
                    showArrival = false
                    railWidth = plainExpandedRail
                    stackedDetails = false
                }
            }
            if (stackedDetails) {
                measureText(meta, inner)
                status.measure(MeasureSpec.makeMeasureSpec(inner, MeasureSpec.AT_MOST), unspecified())
                val extra = if (showArrival) arrivalExtra else 0
                railWidth = max(dp(54), status.measuredWidth + extra).coerceAtMost(inner)
                bodyHeight = meta.measuredHeight + dp(4) + actionRailHeight()
            } else {
                bodyHeight = max(meta.measuredHeight, actionRailHeight())
            }
            contentHeight = headerHeight + dp(4) + bodyHeight
        }
        setMeasuredDimension(resolveSize(width, widthMeasureSpec),
            resolveSize(max(suggestedMinimumHeight, contentHeight + paddingTop + paddingBottom), heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val inner = (width - paddingLeft - paddingRight).coerceAtLeast(1)
        val availableHeight = height - paddingTop - paddingBottom
        val y = paddingTop + ((availableHeight - contentHeight) / 2).coerceAtLeast(0)
        if (!expanded) {
            place(icon, 0, y + (contentHeight - icon.measuredHeight) / 2)
            val textY = y + (contentHeight - title.measuredHeight - dp(3) - meta.measuredHeight) / 2
            place(title, dp(60), textY)
            place(meta, dp(60), textY + title.measuredHeight + dp(3))
            placeRail(inner, y + (contentHeight - actionRailHeight()) / 2)
        } else {
            place(icon, 0, y + (headerHeight - icon.measuredHeight) / 2)
            place(title, dp(60), y + (headerHeight - title.measuredHeight) / 2)
            val bodyY = y + headerHeight + dp(4)
            place(meta, 0, bodyY)
            placeRail(inner, if (stackedDetails) bodyY + meta.measuredHeight + dp(4) else bodyY)
        }
    }

    private fun measureSide(inner: Int, rail: Int): Boolean {
        val sideWidth = (inner - dp(48 + 12 + 12) - rail).coerceAtLeast(1)
        measureText(title, sideWidth)
        measureText(meta, sideWidth)
        return sideWidth < dp(100) || title.lineCount > 2 || meta.lineCount > 3 || status.lineCount > 2 ||
            splitsWord(title) || splitsWord(meta) || splitsWord(status)
    }

    private fun placeRail(inner: Int, y: Int) {
        val start = inner - railWidth
        if (showArrival) {
            val groupWidth = arrival.measuredWidth + dp(4) + status.measuredWidth
            val groupStart = start + ((railWidth - groupWidth) / 2).coerceAtLeast(0)
            place(arrival, groupStart, y + (status.measuredHeight - arrival.measuredHeight) / 2)
            place(status, groupStart + arrival.measuredWidth + dp(4), y)
        } else {
            arrival.layout(0, 0, 0, 0)
            place(status, start + (railWidth - status.measuredWidth) / 2, y)
        }
        if (actions.visibility != View.GONE) place(actions, start + (railWidth - actions.measuredWidth) / 2,
            y + status.measuredHeight)
        else actions.layout(0, 0, 0, 0)
    }

    private fun place(view: View, start: Int, y: Int) {
        val x = if (layoutDirection == View.LAYOUT_DIRECTION_RTL)
            width - paddingRight - start - view.measuredWidth else paddingLeft + start
        view.layout(x, y, x + view.measuredWidth, y + view.measuredHeight)
    }

    private fun actionRailHeight() = status.measuredHeight + if (actions.visibility == View.GONE) 0 else actions.measuredHeight
    private fun measureText(view: TextView, width: Int) = view.measure(exact(width), unspecified())
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
