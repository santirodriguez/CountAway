package com.santiagorodriguez.countaway.widget

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.ui.BaseActivity
import com.santiagorodriguez.countaway.ui.InsetUtils
import com.santiagorodriguez.countaway.ui.SimpleItemSelectedListener

class WidgetDefaultsActivity : BaseActivity() {
    private lateinit var appearanceSpinner: Spinner
    private lateinit var backgroundSpinner: Spinner
    private lateinit var previewBackground: ImageView
    private lateinit var previewIcon: ImageView
    private lateinit var previewTitle: TextView
    private lateinit var previewCount: TextView
    private lateinit var previewUnit: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_defaults)
        InsetUtils.applySystemBarPadding(findViewById(R.id.widgetDefaultsRoot))

        appearanceSpinner = findViewById(R.id.widgetDefaultsAppearanceSpinner)
        backgroundSpinner = findViewById(R.id.widgetDefaultsBackgroundSpinner)
        previewBackground = findViewById(R.id.widgetDefaultsPreviewBackground)
        previewIcon = findViewById(R.id.widgetDefaultsPreviewIcon)
        previewTitle = findViewById(R.id.widgetDefaultsPreviewTitle)
        previewCount = findViewById(R.id.widgetDefaultsPreviewCount)
        previewUnit = findViewById(R.id.widgetDefaultsPreviewUnit)

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

        appearanceSpinner.onItemSelectedListener =
            SimpleItemSelectedListener { updatePreview() }
        backgroundSpinner.onItemSelectedListener =
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
        if (!::previewBackground.isInitialized) return
        val selection = currentSelection()
        val theme = WidgetThemeResolver.resolve(this, selection.appearance)

        previewBackground.setImageBitmap(
            WidgetBackgroundRenderer.render(
                context = applicationContext,
                background = selection.background,
                dark = theme.dark,
                widthDp = PREVIEW_WIDTH_DP,
                heightDp = PREVIEW_HEIGHT_DP,
            ),
        )
        previewIcon.setColorFilter(theme.accentTextColor)
        previewTitle.setTextColor(theme.primaryTextColor)
        previewCount.setTextColor(theme.accentTextColor)
        previewUnit.setTextColor(theme.secondaryTextColor)
    }

    private companion object {
        const val PREVIEW_WIDTH_DP = 180
        const val PREVIEW_HEIGHT_DP = 110
        const val STATE_APPEARANCE = "default_appearance"
        const val STATE_BACKGROUND = "default_background"
    }
}
