package com.santiagorodriguez.countaway.widget

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.santiagorodriguez.countaway.R
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal class WidgetPreviewController(
    private val context: Context,
    private val container: FrameLayout,
    private val frame: FrameLayout,
    private val backgroundView: ImageView,
    private val contentView: LinearLayout,
    private val iconView: ImageView,
    private val titleView: TextView,
    private val milestoneView: TextView,
    private val countView: TextView,
    private val unitView: TextView,
    private val dateView: TextView,
) {
    private var currentSize = WidgetSize.STANDARD

    fun renderStyle(
        selection: WidgetStyleSelection,
        dimensions: WidgetPreviewDimensions,
    ) {
        currentSize = dimensions.size
        applyFrame(dimensions)
        applyLayout(currentSize)

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
        val presentation = WidgetPresentationResolver.resolve(
            content = content,
            size = currentSize,
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
        iconView.setImageResource(iconRes)
        titleView.text = title
        countView.text = countText
        unitView.text = unit
        milestoneView.text = ""
        dateView.text = ""
        milestoneView.visibility = View.GONE
        dateView.visibility = View.GONE
        unitView.visibility = if (
            currentSize == WidgetSize.STANDARD || currentSize == WidgetSize.LARGE
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
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

    private fun applyLayout(size: WidgetSize) {
        contentView.removeAllViews()
        if (size == WidgetSize.SHORT) {
            contentView.orientation = LinearLayout.HORIZONTAL
            contentView.gravity = Gravity.CENTER_VERTICAL
            contentView.setPadding(dp(6), dp(3), dp(6), dp(3))
            iconView.layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
            countView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(5) }
            titleView.layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply { marginStart = dp(7) }
            contentView.addView(iconView)
            contentView.addView(countView)
            contentView.addView(titleView)
            titleView.textSize = 10f
            configureCountAutoSize(size)
            titleView.maxLines = 2
            milestoneView.visibility = View.GONE
            unitView.visibility = View.GONE
            dateView.visibility = View.GONE
            return
        }

        contentView.orientation = LinearLayout.VERTICAL
        contentView.gravity =
            if (size == WidgetSize.COMPACT) Gravity.CENTER else Gravity.CENTER_HORIZONTAL
        val paddingDp = when (size) {
            WidgetSize.COMPACT -> 4
            WidgetSize.STANDARD -> 10
            WidgetSize.LARGE -> 14
            WidgetSize.SHORT -> 6
        }
        contentView.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp))

        titleView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        val iconSizeDp = when (size) {
            WidgetSize.COMPACT -> 16
            WidgetSize.STANDARD -> 22
            WidgetSize.LARGE -> 30
            WidgetSize.SHORT -> 18
        }
        iconView.layoutParams = LinearLayout.LayoutParams(
            dp(iconSizeDp),
            dp(iconSizeDp),
        ).apply {
            topMargin = when (size) {
                WidgetSize.STANDARD -> dp(3)
                WidgetSize.LARGE -> dp(5)
                else -> 0
            }
        }
        milestoneView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        countView.layoutParams = if (size == WidgetSize.COMPACT) {
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        } else {
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0,
                1f,
            )
        }
        unitView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        dateView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = if (size == WidgetSize.LARGE) dp(4) else 0
        }

        contentView.addView(titleView)
        contentView.addView(iconView)
        contentView.addView(milestoneView)
        contentView.addView(countView)
        contentView.addView(unitView)
        contentView.addView(dateView)

        titleView.maxLines = 2
        titleView.textSize = when (size) {
            WidgetSize.COMPACT -> 10f
            WidgetSize.STANDARD -> 13f
            WidgetSize.LARGE -> 17f
            WidgetSize.SHORT -> 10f
        }
        milestoneView.textSize = when (size) {
            WidgetSize.COMPACT -> 9f
            WidgetSize.STANDARD -> 11f
            WidgetSize.LARGE -> 13f
            WidgetSize.SHORT -> 9f
        }
        configureCountAutoSize(size)
        unitView.textSize = if (size == WidgetSize.LARGE) 15f else 12f
        dateView.textSize = 13f
    }

    private fun configureCountAutoSize(size: WidgetSize) {
        val (minSp, maxSp) = when (size) {
            WidgetSize.COMPACT -> 12 to 26
            WidgetSize.SHORT -> 12 to 24
            WidgetSize.STANDARD -> 12 to 42
            WidgetSize.LARGE -> 14 to 60
        }
        countView.setAutoSizeTextTypeUniformWithConfiguration(
            minSp,
            maxSp,
            1,
            TypedValue.COMPLEX_UNIT_SP,
        )
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
