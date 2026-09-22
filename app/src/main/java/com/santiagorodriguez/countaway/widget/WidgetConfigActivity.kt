package com.santiagorodriguez.countaway.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownEventOrder
import com.santiagorodriguez.countaway.countdown.CountdownOccurrenceResolver
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.countdown.CountdownTimeSnapshot
import com.santiagorodriguez.countaway.data.CountdownDataProblem
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.ui.BaseActivity
import com.santiagorodriguez.countaway.ui.EditorActivity
import com.santiagorodriguez.countaway.ui.InsetUtils
import com.santiagorodriguez.countaway.ui.SimpleItemSelectedListener
import com.santiagorodriguez.countaway.ui.TemporalInvalidationController
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class WidgetConfigActivity : BaseActivity() {
    private lateinit var repository: CountdownRepository
    private lateinit var eventList: ListView
    private lateinit var emptyState: TextView
    private lateinit var appearanceSpinner: Spinner
    private lateinit var backgroundSpinner: Spinner
    private lateinit var previewBackground: ImageView
    private lateinit var previewContent: LinearLayout
    private lateinit var previewIcon: ImageView
    private lateinit var previewTitle: TextView
    private lateinit var previewCount: TextView
    private lateinit var previewUnit: TextView
    private lateinit var saveButton: Button
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var events: List<CountdownEvent> = emptyList()
    private var selectedEventId: String? = null
    private var selectedMode: WidgetEventSelection = WidgetEventSelection.FIXED
    private lateinit var temporalInvalidationController: TemporalInvalidationController
    private var loadGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        val manager = AppWidgetManager.getInstance(applicationContext)
        val provider = ComponentName(applicationContext, CountdownWidgetProvider::class.java)
        if (!WidgetInstanceValidator.isOwnedBy(manager, appWidgetId, provider)) {
            finish()
            return
        }

        setContentView(R.layout.activity_widget_config)
        InsetUtils.applySystemBarPadding(findViewById(R.id.widgetConfigRoot))

        repository = CountdownRepository(this)
        temporalInvalidationController = TemporalInvalidationController(this) { snapshot ->
            reloadEvents(snapshot)
        }
        eventList = findViewById(R.id.widgetEventList)
        emptyState = findViewById(R.id.widgetEmptyState)
        appearanceSpinner = findViewById(R.id.widgetAppearanceSpinner)
        backgroundSpinner = findViewById(R.id.widgetBackgroundSpinner)
        previewBackground = findViewById(R.id.widgetPreviewBackground)
        previewContent = findViewById(R.id.widgetPreviewContent)
        previewIcon = findViewById(R.id.widgetPreviewIcon)
        previewTitle = findViewById(R.id.widgetPreviewTitle)
        previewCount = findViewById(R.id.widgetPreviewCount)
        previewUnit = findViewById(R.id.widgetPreviewUnit)
        saveButton = findViewById(R.id.widgetSaveButton)
        setSaveEnabled(false)

        appearanceSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf(
                getString(R.string.widget_appearance_system),
                getString(R.string.widget_appearance_light),
                getString(R.string.widget_appearance_dark),
            ),
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        backgroundSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf(
                getString(R.string.widget_background_classic),
                getString(R.string.widget_background_mist),
                getString(R.string.widget_background_horizon),
                getString(R.string.widget_background_forest),
                getString(R.string.widget_background_sunset),
                getString(R.string.widget_background_pulse),
                getString(R.string.widget_background_breeze),
                getString(R.string.widget_background_ember),
                getString(R.string.widget_background_six),
            ),
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val existing = WidgetPreferences(applicationContext).get(appWidgetId)
        val existingStyle = existing?.let { configuration ->
            WidgetStyleSelection(
                appearance = configuration.appearance,
                background = configuration.background,
            )
        }
        val defaults = WidgetDefaultsPreferences(applicationContext).get()
        val baselineStyle = WidgetStyleSelectionResolver.initial(
            existing = existingStyle,
            defaults = defaults,
        )

        if (savedInstanceState == null) {
            selectedEventId = existing?.eventId
            selectedMode = existing?.eventSelection ?: WidgetEventSelection.FIXED
        } else {
            selectedEventId = savedInstanceState.getString(STATE_EVENT_ID)
            selectedMode = enumValueOrDefault(
                savedInstanceState.getString(STATE_SELECTION_MODE),
                WidgetEventSelection.entries,
                existing?.eventSelection ?: WidgetEventSelection.FIXED,
            )
        }

        val initialStyle = if (savedInstanceState == null) {
            baselineStyle
        } else {
            WidgetStyleSelectionResolver.restore(
                baseline = baselineStyle,
                appearanceName = savedInstanceState.getString(STATE_APPEARANCE),
                backgroundName = savedInstanceState.getString(STATE_BACKGROUND),
            )
        }
        appearanceSpinner.setSelection(WidgetAppearance.entries.indexOf(initialStyle.appearance))
        backgroundSpinner.setSelection(WidgetBackground.entries.indexOf(initialStyle.background))

        val styleListener = SimpleItemSelectedListener { updateStylePreview() }
        appearanceSpinner.onItemSelectedListener = styleListener
        backgroundSpinner.onItemSelectedListener = SimpleItemSelectedListener { updateStylePreview() }
        updateStylePreview()

        eventList.choiceMode = ListView.CHOICE_MODE_SINGLE
        eventList.setOnItemClickListener { _, _, position, _ ->
            if (position == 0) {
                selectedMode = WidgetEventSelection.NEXT
                selectedEventId = null
            } else {
                selectedMode = WidgetEventSelection.FIXED
                selectedEventId = events[position - 1].id
            }
            updateContentPreview(CountdownTime.snapshot().today)
            setSaveEnabled(true)
        }

        findViewById<Button>(R.id.widgetCreateButton).setOnClickListener {
            startActivity(Intent(this, EditorActivity::class.java))
        }
        saveButton.setOnClickListener { saveConfiguration() }
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) {
            val snapshot = CountdownTime.snapshot()
            temporalInvalidationController.start(snapshot)
            reloadEvents(snapshot)
        }
    }

    override fun onPause() {
        if (::temporalInvalidationController.isInitialized) {
            temporalInvalidationController.stop()
        }
        loadGeneration += 1
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_EVENT_ID, selectedEventId)
        outState.putString(STATE_SELECTION_MODE, selectedMode.name)
        WidgetAppearance.entries.getOrNull(appearanceSpinner.selectedItemPosition)?.let {
            outState.putString(STATE_APPEARANCE, it.name)
        }
        WidgetBackground.entries.getOrNull(backgroundSpinner.selectedItemPosition)?.let {
            outState.putString(STATE_BACKGROUND, it.name)
        }
        super.onSaveInstanceState(outState)
    }

    private fun reloadEvents(snapshot: CountdownTimeSnapshot = CountdownTime.snapshot()) {
        val generation = ++loadGeneration
        eventList.isEnabled = false
        setSaveEnabled(false)

        CountdownIo.submit(
            task = { repository.loadResult() },
            onComplete = { result ->
                if (generation != loadGeneration || isFinishing || isDestroyed) return@submit
                renderEvents(
                    result.getOrElse { CountdownLoadResult.Failure(CountdownDataProblem.CORRUPT) },
                    snapshot.today,
                )
            },
        )
    }

    private fun renderEvents(result: CountdownLoadResult, today: LocalDate) {
        if (result is CountdownLoadResult.Failure) {
            events = emptyList()
            eventList.isEnabled = false
            eventList.visibility = View.GONE
            emptyState.setText(
                if (result.problem == CountdownDataProblem.UNSUPPORTED_SCHEMA) {
                    R.string.widget_data_newer_version
                } else {
                    R.string.widget_data_error
                },
            )
            emptyState.visibility = View.VISIBLE
            setSaveEnabled(false)
            updateContentPreview(today)
            return
        }

        events = CountdownEventOrder.sortedForDisplay((result as CountdownLoadResult.Success).events, today)
        val locale = resources.configuration.locales[0]
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        val labels = buildList {
            add(getString(R.string.widget_next_countdown))
            addAll(events.map { event ->
                val displayDate = CountdownOccurrenceResolver.displayDate(event, today)
                getString(R.string.widget_event_option, event.title, displayDate.format(formatter))
            })
        }
        eventList.adapter = ArrayAdapter(this, R.layout.item_widget_event, android.R.id.text1, labels)
        eventList.isEnabled = true
        emptyState.visibility = View.GONE
        eventList.visibility = View.VISIBLE

        val selectedIndex = when (selectedMode) {
            WidgetEventSelection.NEXT -> 0
            WidgetEventSelection.FIXED -> events.indexOfFirst { it.id == selectedEventId }.let { index ->
                if (index >= 0) index + 1 else -1
            }
        }
        if (selectedIndex >= 0) {
            eventList.setItemChecked(selectedIndex, true)
            setSaveEnabled(true)
        } else {
            selectedEventId = null
            setSaveEnabled(false)
        }
        updateContentPreview(today)
    }

    private fun updateStylePreview() {
        if (!::previewBackground.isInitialized) return
        val appearance = WidgetAppearance.entries.getOrElse(appearanceSpinner.selectedItemPosition) {
            WidgetAppearance.SYSTEM
        }
        val background = WidgetBackground.entries.getOrElse(backgroundSpinner.selectedItemPosition) {
            WidgetBackground.CLASSIC
        }
        val theme = WidgetThemeResolver.resolve(this, appearance)
        val (previewWidthDp, previewHeightDp) = previewDimensions()
        val previewSize = WidgetSize.fromDimensions(previewWidthDp, previewHeightDp)
        applyPreviewSize(previewSize)

        previewBackground.setImageBitmap(
            WidgetBackgroundRenderer.render(
                context = applicationContext,
                background = background,
                dark = theme.dark,
                widthDp = previewWidthDp,
                heightDp = previewHeightDp,
            ),
        )
        previewIcon.setColorFilter(theme.accentTextColor)
        previewTitle.setTextColor(theme.primaryTextColor)
        previewCount.setTextColor(theme.accentTextColor)
        previewUnit.setTextColor(theme.secondaryTextColor)
        updateContentPreview(CountdownTime.snapshot().today)
    }

    private fun previewDimensions(): Pair<Int, Int> {
        val options = AppWidgetManager.getInstance(applicationContext).getAppWidgetOptions(appWidgetId)
        val widthDp = options
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_PREVIEW_WIDTH_DP)
            .takeIf { it > 0 } ?: DEFAULT_PREVIEW_WIDTH_DP
        val heightDp = options
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_PREVIEW_HEIGHT_DP)
            .takeIf { it > 0 } ?: DEFAULT_PREVIEW_HEIGHT_DP
        return widthDp to heightDp
    }

    private fun applyPreviewSize(size: WidgetSize) {
        previewContent.removeAllViews()
        if (size == WidgetSize.SHORT) {
            previewContent.orientation = LinearLayout.HORIZONTAL
            previewContent.gravity = Gravity.CENTER_VERTICAL
            previewIcon.layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
            previewCount.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(6) }
            previewTitle.layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply { marginStart = dp(8) }
            previewContent.addView(previewIcon)
            previewContent.addView(previewCount)
            previewContent.addView(previewTitle)
            previewContent.addView(previewUnit)
            previewCount.textSize = 24f
            previewTitle.textSize = 10f
            previewTitle.maxLines = 2
            previewUnit.visibility = View.GONE
            return
        }

        previewContent.orientation = LinearLayout.VERTICAL
        previewContent.gravity = Gravity.CENTER
        previewTitle.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        val iconSizeDp = if (size == WidgetSize.LARGE) 30 else if (size == WidgetSize.STANDARD) 22 else 16
        previewIcon.layoutParams = LinearLayout.LayoutParams(dp(iconSizeDp), dp(iconSizeDp))
        previewCount.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        previewUnit.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        previewContent.addView(previewTitle)
        previewContent.addView(previewIcon)
        previewContent.addView(previewCount)
        previewContent.addView(previewUnit)
        previewTitle.maxLines = 2
        previewTitle.textSize = when (size) {
            WidgetSize.COMPACT -> 10f
            WidgetSize.STANDARD -> 13f
            WidgetSize.LARGE -> 17f
            WidgetSize.SHORT -> 10f
        }
        previewCount.textSize = when (size) {
            WidgetSize.COMPACT -> 26f
            WidgetSize.STANDARD -> 42f
            WidgetSize.LARGE -> 52f
            WidgetSize.SHORT -> 24f
        }
        previewUnit.visibility = if (size == WidgetSize.COMPACT) View.GONE else View.VISIBLE
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun updateContentPreview(today: LocalDate = CountdownTime.snapshot().today) {
        if (!::previewIcon.isInitialized) return
        val event = WidgetEventResolver.resolve(
            selection = selectedMode,
            eventId = selectedEventId,
            events = events,
            today = today,
        )

        if (event != null) {
            val content = WidgetEventContentFactory.from(event, today)
            previewIcon.setImageResource(content.iconRes)
            previewTitle.text = content.title
            previewCount.text = content.countText
            previewUnit.text = content.unitRes?.let(::getString).orEmpty()
            return
        }

        previewIcon.setImageResource(R.drawable.ic_event_calendar)
        previewTitle.setText(
            if (selectedMode == WidgetEventSelection.NEXT) {
                R.string.widget_no_upcoming
            } else {
                R.string.widget_select_countdown
            },
        )
        previewCount.setText(R.string.widget_preview_value)
        previewUnit.setText(R.string.widget_tap_to_configure)
    }

    private fun setSaveEnabled(enabled: Boolean) {
        saveButton.isEnabled = enabled
        saveButton.alpha = if (enabled) 1f else 0.45f
    }

    private fun saveConfiguration() {
        if (selectedMode == WidgetEventSelection.FIXED && selectedEventId == null) return
        val appearance = WidgetAppearance.entries[appearanceSpinner.selectedItemPosition]
        val background = WidgetBackground.entries[backgroundSpinner.selectedItemPosition]
        WidgetPreferences(applicationContext).save(
            appWidgetId = appWidgetId,
            eventId = selectedEventId,
            appearance = appearance,
            background = background,
            eventSelection = selectedMode,
        )

        val appContext = applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val snapshot = CountdownTime.snapshot()
        CountdownIo.execute {
            runCatching { CountdownWidgetProvider.updateWidget(appContext, manager, appWidgetId, snapshot) }
            runCatching { WidgetUpdateScheduler.ensureScheduled(appContext, snapshot) }
        }

        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        finish()
    }

    private fun <T : Enum<T>> enumValueOrDefault(name: String?, values: List<T>, default: T): T =
        values.firstOrNull { it.name == name } ?: default

    private companion object {
        const val DEFAULT_PREVIEW_WIDTH_DP = 180
        const val DEFAULT_PREVIEW_HEIGHT_DP = 110
        const val STATE_EVENT_ID = "selected_event_id"
        const val STATE_SELECTION_MODE = "selected_mode"
        const val STATE_APPEARANCE = "selected_appearance"
        const val STATE_BACKGROUND = "selected_background"
    }
}
