package com.santiagorodriguez.countaway.widget

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.ui.BaseActivity
import com.santiagorodriguez.countaway.ui.InsetUtils
import com.santiagorodriguez.countaway.ui.SimpleItemSelectedListener

class WidgetDefaultsActivity : BaseActivity() {
    private lateinit var appearanceSpinner: Spinner
    private lateinit var backgroundSpinner: Spinner
    private lateinit var previewSizeSpinner: Spinner
    private lateinit var previewController: WidgetPreviewController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_defaults)
        InsetUtils.applySystemBarPadding(findViewById(R.id.widgetDefaultsRoot))

        appearanceSpinner = findViewById(R.id.widgetDefaultsAppearanceSpinner)
        backgroundSpinner = findViewById(R.id.widgetDefaultsBackgroundSpinner)
        previewSizeSpinner = findViewById(R.id.widgetDefaultsPreviewSizeSpinner)
        previewController = WidgetPreviewController(
            context = this,
            container = findViewById(R.id.widgetDefaultsPreviewContainer),
            frame = findViewById(R.id.widgetDefaultsPreviewFrame),
        )

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

        previewSizeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf(
                getString(R.string.widget_size_compact),
                getString(R.string.widget_size_short),
                getString(R.string.widget_size_standard),
                getString(R.string.widget_size_large),
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
        val restoredPreviewSize = savedInstanceState
            ?.getString(STATE_PREVIEW_SIZE)
            ?.let { name -> WidgetSize.entries.firstOrNull { it.name == name } }
            ?: WidgetSize.STANDARD
        previewSizeSpinner.setSelection(
            WidgetPreviewSizing.orderedSizes.indexOf(restoredPreviewSize).coerceAtLeast(0),
        )

        appearanceSpinner.onItemSelectedListener =
            SimpleItemSelectedListener { updatePreview() }
        backgroundSpinner.onItemSelectedListener =
            SimpleItemSelectedListener { updatePreview() }
        previewSizeSpinner.onItemSelectedListener =
            SimpleItemSelectedListener { updatePreview() }
        updatePreview()

        findViewById<Button>(R.id.widgetDefaultsResetButton).setOnClickListener {
            applySelection(WidgetStyleSelection.FACTORY)
            updatePreview()
        }
        findViewById<Button>(R.id.widgetDefaultsCancelButton).setOnClickListener {
            finish()
        }
        findViewById<Button>(R.id.widgetDefaultsSaveButton).setOnClickListener {
            WidgetDefaultsPreferences(applicationContext).save(currentSelection())
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val selection = currentSelection()
        outState.putString(STATE_APPEARANCE, selection.appearance.name)
        outState.putString(STATE_BACKGROUND, selection.background.name)
        WidgetPreviewSizing.orderedSizes.getOrNull(previewSizeSpinner.selectedItemPosition)?.let {
            outState.putString(STATE_PREVIEW_SIZE, it.name)
        }
        super.onSaveInstanceState(outState)
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
        if (!::previewController.isInitialized) return
        val size = WidgetPreviewSizing.orderedSizes
            .getOrElse(previewSizeSpinner.selectedItemPosition) { WidgetSize.STANDARD }
        previewController.renderStyle(
            selection = currentSelection(),
            dimensions = WidgetPreviewSizing.representative(size),
        )
        previewController.renderPlaceholder(
            title = getString(R.string.widget_defaults_preview_title),
            unit = getString(R.string.widget_days_left),
        )
    }

    private companion object {
        const val STATE_APPEARANCE = "default_appearance"
        const val STATE_BACKGROUND = "default_background"
        const val STATE_PREVIEW_SIZE = "default_preview_size"
    }
}
