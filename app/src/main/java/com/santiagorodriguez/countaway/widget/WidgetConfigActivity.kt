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
    private lateinit var saveButton: Button
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var events: List<CountdownEvent> = emptyList()
    private var selectedEventId: String? = null

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
        saveButton = findViewById(R.id.widgetSaveButton)

        val appearanceValues = WidgetAppearance.entries
        appearanceSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf(
                getString(R.string.widget_appearance_system),
                getString(R.string.widget_appearance_light),
                getString(R.string.widget_appearance_dark),
            ),
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val existing = WidgetPreferences(this).get(appWidgetId)
        selectedEventId = existing?.eventId
        appearanceSpinner.setSelection(appearanceValues.indexOf(existing?.appearance ?: WidgetAppearance.SYSTEM))

        eventList.choiceMode = ListView.CHOICE_MODE_SINGLE
        eventList.setOnItemClickListener { _, _, position, _ ->
            selectedEventId = events[position].id
            saveButton.isEnabled = true
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
        val labels = events.map { event ->
            getString(R.string.widget_event_option, event.title, event.date.format(formatter))
        }
        eventList.adapter = ArrayAdapter(this, R.layout.item_widget_event, android.R.id.text1, labels)
        emptyState.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
        eventList.visibility = if (events.isEmpty()) View.GONE else View.VISIBLE

        val selectedIndex = events.indexOfFirst { it.id == selectedEventId }
        if (selectedIndex >= 0) {
            eventList.setItemChecked(selectedIndex, true)
            saveButton.isEnabled = true
        } else {
            selectedEventId = null
            saveButton.isEnabled = false
        }
    }

    private fun saveConfiguration() {
        val eventId = selectedEventId ?: return
        val appearance = WidgetAppearance.entries[appearanceSpinner.selectedItemPosition]
        WidgetPreferences(this).save(appWidgetId, eventId, appearance)

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
