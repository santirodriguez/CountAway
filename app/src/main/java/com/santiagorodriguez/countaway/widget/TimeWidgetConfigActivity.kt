package com.santiagorodriguez.countaway.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownEventOrder
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.data.CountdownDataProblem
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.data.EventTransport
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.ui.BaseActivity
import com.santiagorodriguez.countaway.ui.EditorActivity
import com.santiagorodriguez.countaway.ui.InsetUtils
import com.santiagorodriguez.countaway.ui.SimpleItemSelectedListener
import com.santiagorodriguez.countaway.ui.TemporalInvalidationController
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Independent fixed-event configuration. Never offers classic widget NEXT semantics. */
class TimeWidgetConfigActivity : BaseActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var repository: CountdownRepository
    private lateinit var eventList: ListView
    private lateinit var header: View
    private lateinit var footer: View
    private lateinit var emptyState: TextView
    private lateinit var appearanceSpinner: Spinner
    private lateinit var backgroundSpinner: Spinner
    private lateinit var saveButton: Button
    private lateinit var preview: FrameLayout
    private lateinit var previewContainer: FrameLayout
    private lateinit var temporal: TemporalInvalidationController
    private var events: List<CountdownEvent> = emptyList()
    private var selectedId: String? = null
    private var generation = 0
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val manager = AppWidgetManager.getInstance(applicationContext)
        if (!WidgetInstanceValidator.isOwnedBy(manager, widgetId,
                ComponentName(applicationContext, TimeWidgetProvider::class.java))) {
            finish()
            return
        }
        setContentView(R.layout.activity_widget_config)
        InsetUtils.applySystemBarPadding(findViewById(R.id.widgetConfigRoot))
        repository = CountdownRepository(this)
        temporal = TemporalInvalidationController(this) { reload() }
        eventList = findViewById(R.id.widgetEventList)
        eventList.choiceMode = ListView.CHOICE_MODE_SINGLE
        header = layoutInflater.inflate(R.layout.time_widget_config_header, eventList, false)
        footer = layoutInflater.inflate(R.layout.widget_config_footer, eventList, false)
        eventList.addHeaderView(header, null, false)
        eventList.addFooterView(footer, null, false)
        emptyState = header.findViewById(R.id.widgetEmptyState)
        appearanceSpinner = footer.findViewById(R.id.widgetAppearanceSpinner)
        backgroundSpinner = footer.findViewById(R.id.widgetBackgroundSpinner)
        saveButton = footer.findViewById(R.id.widgetSaveButton)
        preview = footer.findViewById(R.id.widgetPreviewFrame)
        previewContainer = footer.findViewById(R.id.widgetPreviewContainer)
        val backgrounds = listOf(
            R.string.widget_background_classic, R.string.widget_background_mist,
            R.string.widget_background_horizon, R.string.widget_background_forest,
            R.string.widget_background_sunset, R.string.widget_background_pulse,
            R.string.widget_background_breeze, R.string.widget_background_ember,
            R.string.widget_background_six,
        )
        appearanceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            listOf(R.string.widget_appearance_system, R.string.widget_appearance_light,
                R.string.widget_appearance_dark).map(::getString)).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        backgroundSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            backgrounds.map(::getString)).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        val saved = WidgetPreferences(applicationContext).get(widgetId)
        val defaults = WidgetDefaultsPreferences(applicationContext).get()
        // The distinct time widget starts in its editorial Horizon treatment;
        // existing instances always restore their exact saved appearance/background.
        val style = saved?.let { WidgetStyleSelection(it.appearance, it.background) }
            ?: WidgetStyleSelection(defaults.appearance, WidgetBackground.HORIZON)
        selectedId = savedInstanceState?.getString(STATE_EVENT_ID) ?: saved?.eventId
        appearanceSpinner.setSelection(savedInstanceState?.getInt(STATE_APPEARANCE)
            ?: WidgetAppearance.entries.indexOf(style.appearance))
        backgroundSpinner.setSelection(savedInstanceState?.getInt(STATE_BACKGROUND)
            ?: WidgetBackground.entries.indexOf(style.background))
        val listener = SimpleItemSelectedListener { refreshPreview() }
        appearanceSpinner.onItemSelectedListener = listener
        backgroundSpinner.onItemSelectedListener = SimpleItemSelectedListener { refreshPreview() }
        eventList.setOnItemClickListener { _, _, position, _ ->
            if (loading) return@setOnItemClickListener
            val index = position - eventList.headerViewsCount
            if (index !in events.indices) return@setOnItemClickListener
            selectedId = events[index].id
            saveButton.isEnabled = true
            refreshPreview()
        }
        footer.findViewById<Button>(R.id.widgetCreateButton).setOnClickListener {
            startActivity(Intent(this, EditorActivity::class.java))
        }
        saveButton.setOnClickListener { save() }
        saveButton.isEnabled = false
        refreshPreview()
    }

    override fun onResume() {
        super.onResume()
        if (::temporal.isInitialized) {
            val snapshot = CountdownTime.snapshot()
            temporal.start(snapshot)
            reload()
        }
    }

    override fun onPause() {
        if (::temporal.isInitialized) temporal.stop()
        generation++
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::appearanceSpinner.isInitialized) {
            outState.putString(STATE_EVENT_ID, selectedId?.let(EventTransport::wireId))
            outState.putInt(STATE_APPEARANCE, appearanceSpinner.selectedItemPosition)
            outState.putInt(STATE_BACKGROUND, backgroundSpinner.selectedItemPosition)
        }
        super.onSaveInstanceState(outState)
    }

    private fun reload() {
        val current = ++generation
        loading = true
        saveButton.isEnabled = false
        CountdownIo.submit(
            task = { repository.loadResult() },
            onComplete = { result ->
                if (current != generation || isFinishing || isDestroyed) return@submit
                val load = result.getOrElse { CountdownLoadResult.Failure(CountdownDataProblem.CORRUPT) }
                loading = false
                if (load is CountdownLoadResult.Success) {
                    events = CountdownEventOrder.sortedForDisplay(load.events, CountdownTime.snapshot().today)
                        .filter { it.eventTime != null && it.eventZone != null }
                    selectedId = EventTransport.resolve(events, selectedId)?.id
                    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        .withLocale(resources.configuration.locales[0])
                    val choices = events.map { event ->
                        EventTransport.preview(event.title) + " · " + event.date.format(formatter) +
                            " · " + event.eventTime.toString()
                    }
                    eventList.adapter = ArrayAdapter(this, R.layout.item_widget_event, android.R.id.text1, choices)
                    emptyState.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
                    emptyState.setText(R.string.time_widget_no_events)
                    val index = events.indexOfFirst { it.id == selectedId }
                    eventList.clearChoices()
                    if (index >= 0) eventList.setItemChecked(index + eventList.headerViewsCount, true)
                    saveButton.isEnabled = index >= 0
                } else {
                    events = emptyList()
                    emptyState.visibility = View.VISIBLE
                    emptyState.setText(if ((load as CountdownLoadResult.Failure).problem ==
                        CountdownDataProblem.UNSUPPORTED_SCHEMA) R.string.widget_data_newer_version
                        else R.string.widget_data_error)
                    eventList.adapter = ArrayAdapter<String>(this, R.layout.item_widget_event,
                        android.R.id.text1, emptyList())
                }
                refreshPreview()
            },
        )
    }

    private fun refreshPreview() {
        if (!::preview.isInitialized) return
        val appearance = WidgetAppearance.entries.getOrElse(appearanceSpinner.selectedItemPosition) {
            WidgetAppearance.SYSTEM
        }
        val background = WidgetBackground.entries.getOrElse(backgroundSpinner.selectedItemPosition) {
            WidgetBackground.HORIZON
        }
        val configuration = WidgetConfiguration(selectedId, appearance, background)
        val event = events.firstOrNull { it.id == selectedId }
        val views = TimeWidgetRenderer.render(
            this, this, widgetId, configuration, event, null, CountdownTime.snapshot(),
            240, 180, hashMapOf(), interactive = false)
        runCatching {
            preview.removeAllViews()
            preview.addView(views.apply(this, preview))
            previewContainer.post {
                val fit = WidgetPreviewSizing.fit(previewContainer.width, previewContainer.height,
                    WidgetPreviewDimensions(240, 180))
                preview.layoutParams = FrameLayout.LayoutParams(fit.width, fit.height, Gravity.CENTER)
            }
        }.onFailure { android.util.Log.w("CountAwayTimeWidget", "Preview unavailable", it) }
        footer.findViewById<TextView>(R.id.widgetStyleSummary).text = getString(
            R.string.widget_style_summary,
            background.storageKey, appearance.storageKey)
    }

    private fun save() {
        val id = selectedId ?: return
        if (events.none { it.id == id && it.eventTime != null }) return
        val manager = AppWidgetManager.getInstance(applicationContext)
        if (!WidgetInstanceValidator.isOwnedBy(manager, widgetId,
                ComponentName(applicationContext, TimeWidgetProvider::class.java))) {
            Toast.makeText(this, R.string.widget_instance_unavailable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val appearance = WidgetAppearance.entries[appearanceSpinner.selectedItemPosition]
        val background = WidgetBackground.entries[backgroundSpinner.selectedItemPosition]
        WidgetPreferences(applicationContext).save(widgetId, id, appearance, background,
            WidgetEventSelection.FIXED)
        val appContext = applicationContext
        CountdownIo.execute {
            runCatching { TimeWidgetProvider.updateWidget(appContext, manager, widgetId) }
            runCatching { WidgetUpdateScheduler.ensureScheduled(appContext) }
        }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private companion object {
        const val STATE_EVENT_ID = "time_widget_event"
        const val STATE_APPEARANCE = "time_widget_appearance"
        const val STATE_BACKGROUND = "time_widget_background"
    }
}
