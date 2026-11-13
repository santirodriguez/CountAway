package com.santiagorodriguez.countaway.widget

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.util.TypedValue
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.santiagorodriguez.countaway.R
import kotlin.math.roundToInt

internal class WidgetPreviewController(
    private val context: Context,
    private val container: FrameLayout,
    private val frame: FrameLayout,
) {
    private var currentSize: WidgetSize? = null
    private var currentHeightDp: Int = 0
    private var currentStyle: WidgetStyleSelection? = null
    private var currentDimensions: WidgetPreviewDimensions? = null
    private var currentContent: WidgetEventContent? = null
    private lateinit var backgroundView: ImageView
    private lateinit var iconView: ImageView
    private lateinit var titleView: TextView
    private lateinit var milestoneView: TextView
    private lateinit var countView: TextView
    private lateinit var unitView: TextView
    private lateinit var dateView: TextView

    fun renderStyle(selection: WidgetStyleSelection, dimensions: WidgetPreviewDimensions) {
        currentHeightDp = dimensions.heightDp
        currentStyle = selection
        currentDimensions = dimensions
        ensureLayout(dimensions.size)
        applyFrame(dimensions)
        val theme = WidgetThemeResolver.resolve(context, selection.appearance, selection.background)
        renderBackground()
        iconView.setColorFilter(theme.accentTextColor)
        titleView.setTextColor(theme.primaryTextColor)
        countView.setTextColor(theme.accentTextColor)
        milestoneView.setTextColor(theme.secondaryTextColor)
        unitView.setTextColor(theme.secondaryTextColor)
        dateView.setTextColor(theme.secondaryTextColor)
        currentContent?.let(::renderEvent)
    }

    private fun renderBackground() {
        val style = currentStyle ?: return
        val dimensions = currentDimensions ?: return
        val theme = WidgetThemeResolver.resolve(context, style.appearance, style.background)
        backgroundView.setImageBitmap(WidgetBackgroundRenderer.render(context.applicationContext,
            style.background, theme.dark, dimensions.widthDp, dimensions.heightDp))
    }

    fun renderEvent(content: WidgetEventContent) {
        currentContent = content
        val presentation = WidgetPresentationResolver.resolve(content, requireNotNull(currentSize),
            context.resources.configuration.fontScale, currentHeightDp)
        iconView.visibility = if (presentation.showIcon) View.VISIBLE else View.GONE
        titleView.visibility = if (presentation.showTitle) View.VISIBLE else View.GONE
        titleView.maxLines = presentation.titleMaxLines
        iconView.setImageResource(content.iconRes)
        titleView.text = content.title
        if (currentSize == WidgetSize.COMPACT) {
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, WidgetCompactTitleFitter.normalSizeSp)
            if (presentation.showTitle && WidgetCompactTitleFitter.isSingleWord(content.title)) {
                titleView.maxLines = 1
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                    WidgetCompactTitleFitter.fittedSizeSp(context, content.title,
                        requireNotNull(currentDimensions).widthDp)
                        ?: WidgetCompactTitleFitter.normalSizeSp)
            }
        }
        countView.text = presentation.countText
        unitView.text = presentation.unitRes?.let(context::getString).orEmpty()
        milestoneView.text = presentation.milestone.orEmpty()
        dateView.text = content.dateText(context)
        milestoneView.visibility = if (presentation.showMilestone) View.VISIBLE else View.GONE
        unitView.visibility = if (presentation.showUnit) View.VISIBLE else View.GONE
        dateView.visibility = if (presentation.showDate) View.VISIBLE else View.GONE
        frame.findViewById<View>(R.id.widgetRoot).contentDescription = content.description(context)
    }

    fun renderPlaceholder(
        title: String,
        unit: String,
        countText: String = context.getString(R.string.widget_preview_sample_count),
        iconRes: Int = R.drawable.ic_event_calendar,
    ) {
        currentContent = null
        val presentation = WidgetPresentationResolver.placeholder(requireNotNull(currentSize),
            context.resources.configuration.fontScale, currentHeightDp)
        iconView.visibility = if (presentation.showIcon) View.VISIBLE else View.GONE
        titleView.visibility = if (presentation.showTitle) View.VISIBLE else View.GONE
        titleView.maxLines = presentation.titleMaxLines
        iconView.setImageResource(iconRes)
        titleView.text = title
        countView.text = countText
        unitView.text = unit
        milestoneView.text = ""
        dateView.text = ""
        milestoneView.visibility = View.GONE
        dateView.visibility = View.GONE
        unitView.visibility = if (presentation.showUnit) View.VISIBLE else View.GONE
        frame.findViewById<View>(R.id.widgetRoot).contentDescription = "$title, $unit"
    }

    private fun ensureLayout(size: WidgetSize) {
        if (currentSize == size && frame.childCount > 0) return
        frame.removeAllViews()
        LayoutInflater.from(context).inflate(WidgetLayoutResolver.layoutRes(size), frame, true)
        backgroundView = frame.findViewById(R.id.widgetBackground)
        iconView = frame.findViewById(R.id.widgetIcon)
        titleView = frame.findViewById(R.id.widgetTitle)
        milestoneView = frame.findViewById(R.id.widgetMilestone)
        countView = frame.findViewById(R.id.widgetCount)
        if (size != WidgetSize.SHORT) {
            // The attached preview resizes after inflation. Give autosizing the final
            // row width instead of retaining a wrap-content width from its small frame.
            countView.layoutParams = countView.layoutParams.apply {
                width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            }
        }
        unitView = frame.findViewById(R.id.widgetUnit)
        dateView = frame.findViewById(R.id.widgetDate)
        currentSize = size
    }

    private fun applyFrame(dimensions: WidgetPreviewDimensions) {
        container.post {
            if (currentDimensions != dimensions || container.width <= 0 || container.height <= 0) {
                return@post
            }
            // Inflate at the same logical dp size used by the provider. Scale the
            // entire composition, not only the frame: dp/sp children, corners,
            // title breaks and icon spacing then preserve production proportions.
            val density = context.resources.displayMetrics.density
            val widthPx = (dimensions.widthDp * density).roundToInt().coerceAtLeast(1)
            val heightPx = (dimensions.heightDp * density).roundToInt().coerceAtLeast(1)
            val fitted = WidgetPreviewSizing.fit(container.width, container.height, dimensions)
            frame.layoutParams = FrameLayout.LayoutParams(widthPx, heightPx, Gravity.CENTER)
            frame.pivotX = widthPx / 2f
            frame.pivotY = heightPx / 2f
            val scale = minOf(fitted.width.toFloat() / widthPx, fitted.height.toFloat() / heightPx)
            frame.scaleX = scale
            frame.scaleY = scale
        }
    }
}
