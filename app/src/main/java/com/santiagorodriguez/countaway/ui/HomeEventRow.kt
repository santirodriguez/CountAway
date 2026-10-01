package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.santiagorodriguez.countaway.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Uses spare horizontal space for actions without changing title or metadata wrapping. */
class HomeEventRow @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {
    private val icon: View get() = findViewById(R.id.eventIcon)
    private val title: TextView get() = findViewById(R.id.eventTitle)
    private val meta: TextView get() = findViewById(R.id.eventMeta)
    private val status: TextView get() = findViewById(R.id.eventStatus)
    private val arrival: View get() = findViewById(R.id.eventArrival)
    private val actions: View get() = findViewById(R.id.eventActions)
    // Keep the actual inflated pixel size: XML dimensions can be rounded
    // differently from converting the same nominal sp value at runtime.
    private val fallbackStatusSizePx by lazy { status.textSize }
    private var measuringVariants = false
    private var lateral = false
    private var lateralWidth = 0
    private var lastLateralAppearance: Boolean? = null
    private val combinedPill = requireNotNull(context.getDrawable(R.drawable.status_pill)).mutate()
    private val pillBounds = Rect()
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
        // Ineligible rows use the accepted fallback once, without speculative
        // text measurements changing its native wrapping or height.
        measuringVariants = actions.visibility == View.VISIBLE && resources.configuration.fontScale <= 1.3f
        restoreStatusSize()
        measureFallback(inner)
        lateral = measuringVariants && !expanded && tryLateral(inner)
        if (measuringVariants && !lateral) {
            restoreStatusSize()
            measureFallback(inner)
        }
        applyAppearance()
        setMeasuredDimension(resolveSize(width, widthMeasureSpec),
            resolveSize(max(suggestedMinimumHeight, contentHeight + paddingTop + paddingBottom), heightMeasureSpec))
    }

    private fun measureFallback(inner: Int) {
        val railLimit = min(dp(112), (inner * 0.38f).roundToInt()).coerceAtLeast(dp(54)).coerceAtMost(inner)
        measureStatus(railLimit)
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
            measureStatus(statusLimit)
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
                measureStatus(inner)
                val extra = if (showArrival) arrivalExtra else 0
                railWidth = max(dp(54), status.measuredWidth + extra).coerceAtMost(inner)
                bodyHeight = meta.measuredHeight + dp(4) + actionRailHeight()
            } else {
                bodyHeight = max(meta.measuredHeight, actionRailHeight())
            }
            contentHeight = headerHeight + dp(4) + bodyHeight
        }
    }

    private fun tryLateral(inner: Int): Boolean {
        if (actions.visibility != View.VISIBLE || resources.configuration.fontScale > 1.3f) return false
        val titleLines = lineEnds(title)
        val metaLines = lineEnds(meta)
        val baselineHeight = contentHeight
        val requestedArt = showArrival
        // Reuse only eight dp of existing end padding. The full 48dp touch target
        // remains inside this row and never overlays the title or the status.
        val endAllowance = min(dp(8), paddingEnd)
        val sizes = if (requestedArt) listOf(true, false) else listOf(false)
        for (art in sizes) {
            for (sp in listOf(15f, 14f, 13f)) {
                if (art && sp == 13f) continue // Drop decoration before shrinking text.
                setStatusSize(sp)
                val artWidth = if (art) dp(26) else 0
                val limit = min(dp(112), (inner * 0.38f).roundToInt()) - artWidth
                if (limit < dp(54)) continue
                measureStatus(limit)
                if (status.lineCount > 2 || splitsWord(status)) continue
                val pillWidth = status.measuredWidth + artWidth
                val groupWidth = pillWidth + dp(4) + actions.measuredWidth
                val textWidth = inner + endAllowance - groupWidth - dp(72)
                if (textWidth < dp(100)) continue
                measureText(title, textWidth)
                measureText(meta, textWidth)
                if (lineEnds(title) != titleLines || lineEnds(meta) != metaLines ||
                    splitsWord(title) || splitsWord(meta)) continue
                val nextHeight = max(dp(48), max(title.measuredHeight + dp(3) + meta.measuredHeight,
                    max(status.measuredHeight, actions.measuredHeight)))
                if (nextHeight > baselineHeight) continue
                lateralWidth = groupWidth
                contentHeight = nextHeight
                showArrival = art
                return true
            }
        }
        return false
    }

    private fun restoreStatusSize() {
        val px = fallbackStatusSizePx
        if (status.textSize != px) status.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
    }

    private fun setStatusSize(sp: Float) {
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
        if (status.textSize != px) status.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
    }

    private fun lineEnds(view: TextView): List<Int> {
        val layout = view.layout ?: return emptyList()
        return (0 until layout.lineCount).map(layout::getLineEnd)
    }

    private fun applyAppearance() {
        if (lastLateralAppearance == lateral) return
        // Alpha does not alter TextView padding, measurement, or accessibility.
        status.background.mutate().alpha = if (lateral) 0 else 255
        actions.setBackgroundResource(if (lateral) R.drawable.home_event_actions_bubble else R.drawable.event_actions_button)
        actions.setPadding(dp(15), dp(15), dp(15), dp(15))
        lastLateralAppearance = lateral
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (lateral) {
            combinedPill.bounds = pillBounds
            combinedPill.draw(canvas)
        }
        super.dispatchDraw(canvas)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val inner = (width - paddingLeft - paddingRight).coerceAtLeast(1)
        val availableHeight = height - paddingTop - paddingBottom
        val y = paddingTop + ((availableHeight - contentHeight) / 2).coerceAtLeast(0)
        if (lateral) {
            place(icon, 0, y + (contentHeight - icon.measuredHeight) / 2)
            val textY = y + (contentHeight - title.measuredHeight - dp(3) - meta.measuredHeight) / 2
            place(title, dp(60), textY)
            place(meta, dp(60), textY + title.measuredHeight + dp(3))
            val start = inner + min(dp(8), paddingEnd) - lateralWidth
            val statusY = y + (contentHeight - status.measuredHeight) / 2
            if (showArrival) {
                place(arrival, start + dp(4), statusY + (status.measuredHeight - arrival.measuredHeight) / 2)
                place(status, start + dp(26), statusY)
            } else {
                arrival.layout(0, 0, 0, 0)
                place(status, start, statusY)
            }
            val pillWidth = status.measuredWidth + if (showArrival) dp(26) else 0
            val pillLeft = if (layoutDirection == View.LAYOUT_DIRECTION_RTL)
                width - paddingRight - start - pillWidth else paddingLeft + start
            pillBounds.set(pillLeft, statusY, pillLeft + pillWidth, statusY + status.measuredHeight)
            place(actions, start + pillWidth + dp(4), y + (contentHeight - actions.measuredHeight) / 2)
        } else if (!expanded) {
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
    private fun measureText(view: TextView, width: Int) {
        // Trial layouts revisit widths within one pass. Dimensions from View's cache
        // do not guarantee that TextView.layout belongs to the requested width.
        if (measuringVariants) view.forceLayout()
        view.measure(exact(width), unspecified())
    }

    private fun measureStatus(limit: Int) {
        if (measuringVariants) status.forceLayout()
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
