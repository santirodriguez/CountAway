package com.santiagorodriguez.countaway.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownEventOrder
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.ui.BaseActivity
import com.santiagorodriguez.countaway.ui.EditorActivity
import com.santiagorodriguez.countaway.ui.InsetUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class WidgetConfigActivity : BaseActivity() {
    private lateinit var repository: CountdownRepository
    private lateinit var eventList: ListView
    private lateinit var emptyState: TextView
    private lateinit var appearanceSpinner: Spinner
    private lateinit var backgroundSpinner: Spinner
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
                getString(R.string.widget_background_six),
            ),
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val existing = WidgetPreferences(this).get(appWidgetId)
        selectedEventId = existing?.eventId
        selectedMode = existing?.eventSelection ?: WidgetEventSelection.FIXED
        appearanceSpinner.setSelection(WidgetAppearance.entries.indexOf(existing?.appearance ?: WidgetAppearance.SYSTEM))
        backgroundSpinner.setSelection(WidgetBackground.entries.indexOf(existing?.background ?: WidgetBackground.CLASSIC))

        eventList.choiceMode = ListView.CHOICE_MODE_SINGLE
        eventList.setOnItemClickListener { _, _, position, _ ->
            if (position == 0) {
                selectedMode = WidgetEventSelection.NEXT
                selectedEventId = null
            } else {
                selectedMode = WidgetEventSelection.FIXED
                selectedEventId = events[position - 1].id
            }
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

    private fun reloadEvents() {
        events = CountdownEventOrder.sortedForDisplay(repository.load(), LocalDate.now())
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
}
