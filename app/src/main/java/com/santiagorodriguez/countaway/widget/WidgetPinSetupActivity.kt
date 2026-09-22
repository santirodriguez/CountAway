package com.santiagorodriguez.countaway.widget

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.ui.BaseActivity
import com.santiagorodriguez.countaway.ui.InsetUtils
import com.santiagorodriguez.countaway.ui.SimpleItemSelectedListener

class WidgetPinSetupActivity : BaseActivity() {
    private lateinit var repository: CountdownRepository
    private lateinit var appearanceSpinner: Spinner
    private lateinit var backgroundSpinner: Spinner
    private lateinit var previewController: WidgetPreviewController
    private lateinit var addButton: Button
    private var event: CountdownEvent? = null
    private var loadGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        if (eventId.isNullOrBlank()) {
            finish()
            return
        }

        setContentView(R.layout.activity_widget_pin_setup)
        InsetUtils.applySystemBarPadding(findViewById(R.id.widgetPinSetupRoot))

        repository = CountdownRepository(this)
        appearanceSpinner = findViewById(R.id.widgetPinSetupAppearanceSpinner)
        backgroundSpinner = findViewById(R.id.widgetPinSetupBackgroundSpinner)
        previewController = WidgetPreviewController(
            context = this,
            container = findViewById(R.id.widgetPinSetupPreviewContainer),
            frame = findViewById(R.id.widgetPinSetupPreviewFrame),
        )
        addButton = findViewById(R.id.widgetPinSetupAddButton)
        setAddEnabled(false)

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

        val stored = WidgetDefaultsPreferences(applicationContext).get()
        val initial = if (savedInstanceState == null) {
            stored
        } else {
            WidgetStyleSelectionResolver.restore(
                baseline = stored,
                appearanceName = savedInstanceState.getString(STATE_APPEARANCE),
                backgroundName = savedInstanceState.getString(STATE_BACKGROUND),
            )
        }
        applySelection(initial)

        appearanceSpinner.onItemSelectedListener = SimpleItemSelectedListener { updatePreview() }
        backgroundSpinner.onItemSelectedListener = SimpleItemSelectedListener { updatePreview() }

        findViewById<Button>(R.id.widgetPinSetupCancelButton).setOnClickListener { finish() }
        addButton.setOnClickListener {
            val currentEvent = event ?: return@setOnClickListener
            val style = currentSelection()
            WidgetDefaultsPreferences(applicationContext).save(style)
            if (WidgetPinning.request(this, currentEvent, style)) {
                finish()
            } else {
                Toast.makeText(
                    this,
                    R.string.widget_pin_unavailable_guidance,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

        loadEvent(eventId)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val selection = currentSelection()
        outState.putString(STATE_APPEARANCE, selection.appearance.name)
        outState.putString(STATE_BACKGROUND, selection.background.name)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        loadGeneration += 1
        super.onDestroy()
    }

    private fun loadEvent(eventId: String) {
        val generation = ++loadGeneration
        CountdownIo.submit(
            task = { repository.loadResult() },
            onComplete = { result ->
                if (generation != loadGeneration || isFinishing || isDestroyed) return@submit
                val loaded = (result.getOrNull() as? CountdownLoadResult.Success)
                    ?.events
                    ?.firstOrNull { it.id == eventId }
                if (loaded == null) {
                    Toast.makeText(
                        this,
                        R.string.widget_pin_event_unavailable,
                        Toast.LENGTH_SHORT,
                    ).show()
                    finish()
                    return@submit
                }

                event = loaded
                findViewById<TextView>(R.id.widgetPinSetupEventTitle).text = loaded.title
                setAddEnabled(true)
                updatePreview()
            },
        )
    }

    private fun currentSelection(): WidgetStyleSelection = WidgetStyleSelection(
        appearance = WidgetAppearance.entries.getOrElse(appearanceSpinner.selectedItemPosition) {
            WidgetAppearance.SYSTEM
        },
        background = WidgetBackground.entries.getOrElse(backgroundSpinner.selectedItemPosition) {
            WidgetBackground.CLASSIC
        },
    )

    private fun applySelection(selection: WidgetStyleSelection) {
        appearanceSpinner.setSelection(WidgetAppearance.entries.indexOf(selection.appearance))
        backgroundSpinner.setSelection(WidgetBackground.entries.indexOf(selection.background))
    }

    private fun updatePreview() {
        val currentEvent = event ?: return
        previewController.renderStyle(
            selection = currentSelection(),
            dimensions = WidgetPreviewSizing.representative(WidgetSize.STANDARD),
        )
        previewController.renderEvent(
            WidgetEventContentFactory.from(currentEvent, CountdownTime.snapshot().today),
        )
    }

    private fun setAddEnabled(enabled: Boolean) {
        addButton.isEnabled = enabled
        addButton.alpha = if (enabled) 1f else 0.45f
    }

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
        private const val STATE_APPEARANCE = "pin_setup_appearance"
        private const val STATE_BACKGROUND = "pin_setup_background"
    }
}
