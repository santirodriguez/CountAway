package com.santiagorodriguez.countaway.widget

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.santiagorodriguez.countaway.R
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal class WidgetPreviewController(
    private val context: Context,
    private val container: FrameLayout,
    private val frame: FrameLayout,
) {
    private var currentSize: WidgetSize? = null
    private lateinit var backgroundView: ImageView
    private lateinit var iconView: ImageView
    private lateinit var titleView: TextView
    private lateinit var milestoneView: TextView
    private lateinit var countView: TextView
    private lateinit var unitView: TextView
    private lateinit var dateView: TextView

    fun renderStyle(
        selection: WidgetStyleSelection,
        dimensions: WidgetPreviewDimensions,
    ) {
        val size = dimensions.size
        ensureLayout(size)
        applyFrame(dimensions)

        val theme = WidgetThemeResolver.resolve(
            context = context,
            appearance = selection.appearance,
            background = selection.background,
        )
        backgroundView.setImageBitmap(
            WidgetBackgroundRenderer.render(
                context = context.applicationContext,
                background = selection.background,
                dark = theme.dark,
                widthDp = dimensions.widthDp,
                heightDp = dimensions.heightDp,
            ),
        )
        iconView.setColorFilter(theme.accentTextColor)
        titleView.setTextColor(theme.primaryTextColor)
        countView.setTextColor(theme.accentTextColor)
        milestoneView.setTextColor(theme.secondaryTextColor)
        unitView.setTextColor(theme.secondaryTextColor)
        dateView.setTextColor(theme.secondaryTextColor)
    }

    fun renderEvent(content: WidgetEventContent) {
        val size = requireNotNull(currentSize)
        val presentation = WidgetPresentationResolver.resolve(
            content = content,
            size = size,
            fontScale = context.resources.configuration.fontScale,
        )
        val locale = context.resources.configuration.locales[0]
        val formattedDate = content.date.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale),
        )

        iconView.setImageResource(content.iconRes)
        titleView.text = content.title
        countView.text = presentation.countText
        unitView.text = presentation.unitRes?.let(context::getString).orEmpty()
        milestoneView.text = presentation.milestone.orEmpty()
        dateView.text = formattedDate
        milestoneView.visibility = if (presentation.showMilestone) View.VISIBLE else View.GONE
        unitView.visibility = if (presentation.showUnit) View.VISIBLE else View.GONE
        dateView.visibility = if (presentation.showDate) View.VISIBLE else View.GONE
    }

    fun renderPlaceholder(
        title: String,
        unit: String,
        countText: String = context.getString(R.string.widget_preview_sample_count),
        iconRes: Int = R.drawable.ic_event_calendar,
    ) {
        val size = requireNotNull(currentSize)
        iconView.setImageResource(iconRes)
        titleView.text = title
        countView.text = countText
        unitView.text = unit
        milestoneView.text = ""
        dateView.text = ""
        milestoneView.visibility = View.GONE
        dateView.visibility = View.GONE
        unitView.visibility = if (
            size == WidgetSize.STANDARD || size == WidgetSize.LARGE
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun ensureLayout(size: WidgetSize) {
        if (currentSize == size && frame.childCount > 0) return

        frame.removeAllViews()
        LayoutInflater.from(context).inflate(
            WidgetLayoutResolver.layoutRes(size),
            frame,
            true,
        )
        backgroundView = frame.findViewById(R.id.widgetBackground)
        iconView = frame.findViewById(R.id.widgetIcon)
        titleView = frame.findViewById(R.id.widgetTitle)
        milestoneView = frame.findViewById(R.id.widgetMilestone)
        countView = frame.findViewById(R.id.widgetCount)
        unitView = frame.findViewById(R.id.widgetUnit)
        dateView = frame.findViewById(R.id.widgetDate)
        currentSize = size
    }

    private fun applyFrame(dimensions: WidgetPreviewDimensions) {
        container.post {
            val fitted = WidgetPreviewSizing.fit(
                containerWidth = container.width,
                containerHeight = container.height,
                dimensions = dimensions,
            )
            frame.layoutParams = FrameLayout.LayoutParams(
                fitted.width,
                fitted.height,
                Gravity.CENTER,
            )
        }
    }
}
