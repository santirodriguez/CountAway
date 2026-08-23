package com.santiagorodriguez.countaway.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownEventOrder
import com.santiagorodriguez.countaway.data.CountdownDataProblem
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.ui.BaseActivity
import com.santiagorodriguez.countaway.ui.EditorActivity
import com.santiagorodriguez.countaway.ui.InsetUtils
import com.santiagorodriguez.countaway.ui.SimpleItemSelectedListener
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
    private lateinit var previewIcon: ImageView
    private lateinit var previewTitle: TextView
    private lateinit var previewCount: TextView
    private lateinit var previewUnit: TextView
    private lateinit var saveButton: Button
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var events: List<CountdownEvent> = emptyList()
    private var selectedEventId: String? = null
    private var selectedMode: WidgetEventSelection = WidgetEventSelection.FIXED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContentView(R.layout.activity_widget_config)
        InsetUtils.applySystemBarPadding(findViewById(R.id.widgetConfigRoot))

        repository = CountdownRepository(this)
        eventList = findViewById(R.id.widgetEventList)
        emptyState = findViewById(R.id.widgetEmptyState)
        appearanceSpinner = findViewById(R.id.widgetAppearanceSpinner)
        backgroundSpinner = findViewById(R.id.widgetBackgroundSpinner)
        previewBackground = findViewById(R.id.widgetPreviewBackground)
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

        val existing = WidgetPreferences(this).get(appWidgetId)
        if (savedInstanceState == null) {
            selectedEventId = existing?.eventId
            selectedMode = existing?.eventSelection ?: WidgetEventSelection.FIXED
            appearanceSpinner.setSelection(
                WidgetAppearance.entries.indexOf(existing?.appearance ?: WidgetAppearance.SYSTEM),
            )
            backgroundSpinner.setSelection(
                WidgetBackground.entries.indexOf(existing?.background ?: WidgetBackground.CLASSIC),
            )
        } else {
            selectedEventId = savedInstanceState.getString(STATE_EVENT_ID)
            selectedMode = enumValueOrDefault(
                savedInstanceState.getString(STATE_SELECTION_MODE),
                WidgetEventSelection.entries,
                existing?.eventSelection ?: WidgetEventSelection.FIXED,
            )
            val appearance = enumValueOrDefault(
                savedInstanceState.getString(STATE_APPEARANCE),
                WidgetAppearance.entries,
                existing?.appearance ?: WidgetAppearance.SYSTEM,
            )
            val background = enumValueOrDefault(
                savedInstanceState.getString(STATE_BACKGROUND),
                WidgetBackground.entries,
                existing?.background ?: WidgetBackground.CLASSIC,
            )
            appearanceSpinner.setSelection(WidgetAppearance.entries.indexOf(appearance))
            backgroundSpinner.setSelection(WidgetBackground.entries.indexOf(background))
        }

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
            updateContentPreview()
            setSaveEnabled(true)
        }

        findViewById<Button>(R.id.widgetCreateButton).setOnClickListener {
            startActivity(Intent(this, EditorActivity::class.java))
        }
        saveButton.setOnClickListener { saveConfiguration() }
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) reloadEvents()
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

    private fun reloadEvents() {
        val result = repository.loadResult()
        if (result is CountdownLoadResult.Failure) {
            events = emptyList()
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
            updateContentPreview()
            return
        }

        events = CountdownEventOrder.sortedForDisplay((result as CountdownLoadResult.Success).events, LocalDate.now())
        val locale = resources.configuration.locales[0]
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        val labels = buildList {
            add(getString(R.string.widget_next_countdown))
            addAll(events.map { event ->
                getString(R.string.widget_event_option, event.title, event.date.format(formatter))
            })
        }
        eventList.adapter = ArrayAdapter(this, R.layout.item_widget_event, android.R.id.text1, labels)
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
        updateContentPreview()
    }

    private fun updateStylePreview() {
        if (!::previewBackground.isInitialized) return
        val appearance = WidgetAppearance.entries.getOrElse(appearanceSpinner.selectedItemPosition) {
            WidgetAppearance.SYSTEM
        }
        val background = WidgetBackground.entries.getOrElse(backgroundSpinner.selectedItemPosition) {
            WidgetBackground.CLASSIC
        }
        val systemDark = applicationContext.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val dark = appearance.resolveDark(systemDark)

        previewBackground.setImageBitmap(
            WidgetBackgroundRenderer.render(
                context = this,
                background = background,
                dark = dark,
                widthDp = PREVIEW_WIDTH_DP,
                heightDp = PREVIEW_HEIGHT_DP,
            ),
        )
        val primaryColor = getColor(if (dark) R.color.widget_dark_text else R.color.widget_light_text)
        val secondaryColor = getColor(
            if (dark) R.color.widget_dark_secondary_text else R.color.widget_light_secondary_text,
        )
        val accentColor = getColor(if (dark) R.color.widget_dark_accent else R.color.widget_light_accent)
        previewIcon.setColorFilter(accentColor)
        previewTitle.setTextColor(primaryColor)
        previewCount.setTextColor(accentColor)
        previewUnit.setTextColor(secondaryColor)
        updateContentPreview()
    }

    private fun updateContentPreview() {
        if (!::previewIcon.isInitialized) return
        val event = WidgetEventResolver.resolve(
            selection = selectedMode,
            eventId = selectedEventId,
            events = events,
            today = LocalDate.now(),
        )

        if (event != null) {
            val content = WidgetEventContentFactory.from(event, LocalDate.now())
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
        WidgetPreferences(this).save(
            appWidgetId = appWidgetId,
            eventId = selectedEventId,
            appearance = appearance,
            background = background,
            eventSelection = selectedMode,
        )

        val manager = AppWidgetManager.getInstance(this)
        CountdownWidgetProvider.updateWidget(this, manager, appWidgetId)
        WidgetUpdateScheduler.ensureScheduled(this)

        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        finish()
    }

    private fun <T : Enum<T>> enumValueOrDefault(name: String?, values: List<T>, default: T): T =
        values.firstOrNull { it.name == name } ?: default

    private companion object {
        const val PREVIEW_WIDTH_DP = 320
        const val PREVIEW_HEIGHT_DP = 132
        const val STATE_EVENT_ID = "selected_event_id"
        const val STATE_SELECTION_MODE = "selected_mode"
        const val STATE_APPEARANCE = "selected_appearance"
        const val STATE_BACKGROUND = "selected_background"
    }
}
