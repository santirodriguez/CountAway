package com.santiagorodriguez.countaway.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownOccurrenceResolver
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.ui.BaseActivity
import com.santiagorodriguez.countaway.ui.EventIconPresentation
import com.santiagorodriguez.countaway.ui.InsetUtils
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class WidgetPinSetupActivity : BaseActivity() {
    private lateinit var repository: CountdownRepository
    private lateinit var appearanceGroup: RadioGroup
    private lateinit var backgroundRows: LinearLayout
    private lateinit var previewController: WidgetPreviewController
    private lateinit var addButton: Button
    private lateinit var eventId: String
    private var event: CountdownEvent? = null
    private var selectedBackground = WidgetBackground.CLASSIC
    private var loadGeneration = 0

    private var pendingSnapshot: WidgetPinRequestSnapshot? = null
    private var baselineWidgetIds: Set<Int> = emptySet()
    private var awaitingPinResult = false
    private var pinUiWasShown = false
    private var reconcileGeneration = 0
    private var reconcileScheduled = false
    private var reconcileAttempt = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requestedEventId = intent.getStringExtra(EXTRA_EVENT_ID)
        if (requestedEventId.isNullOrBlank()) {
            finish()
            return
        }
        eventId = requestedEventId

        setContentView(R.layout.activity_widget_pin_setup)
        InsetUtils.applySystemBarPadding(findViewById(R.id.widgetPinSetupRoot))

        repository = CountdownRepository(this)
        appearanceGroup = findViewById(R.id.widgetPinSetupAppearanceGroup)
        backgroundRows = findViewById(R.id.widgetPinSetupBackgroundRows)
        previewController = WidgetPreviewController(
            context = this,
            container = findViewById(R.id.widgetPinSetupPreviewContainer),
            frame = findViewById(R.id.widgetPinSetupPreviewFrame),
        )
        addButton = findViewById(R.id.widgetPinSetupAddButton)

        val stored = WidgetDefaultsPreferences(applicationContext).get()
        val restored = WidgetStyleSelectionResolver.restore(
            baseline = stored,
            appearanceName = savedInstanceState?.getString(STATE_APPEARANCE),
            backgroundName = savedInstanceState?.getString(STATE_BACKGROUND),
        )
        selectedBackground = restored.background
        applyAppearance(restored.appearance)

        awaitingPinResult = savedInstanceState?.getBoolean(STATE_AWAITING_PIN, false) == true
        pinUiWasShown = savedInstanceState?.getBoolean(STATE_PIN_UI_SHOWN, false) == true
        baselineWidgetIds = savedInstanceState
            ?.getIntArray(STATE_BASELINE_IDS)
            ?.toSet()
            .orEmpty()
        pendingSnapshot = restorePendingSnapshot(savedInstanceState)

        appearanceGroup.setOnCheckedChangeListener { _, _ ->
            renderBackgroundChoices()
            updatePreview()
        }

        findViewById<Button>(R.id.widgetPinSetupCancelButton).setOnClickListener { finish() }
        addButton.setOnClickListener { requestPin() }

        setAddEnabled(false)
        renderBackgroundChoices()
    }

    override fun onResume() {
        super.onResume()
        if (event == null) {
            loadEvent(eventId)
        }
        if (awaitingPinResult && pinUiWasShown) {
            scheduleReconcile()
        }
    }

    override fun onPause() {
        if (awaitingPinResult) {
            pinUiWasShown = true
        }
        loadGeneration += 1
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!awaitingPinResult) return
        if (!hasFocus) {
            pinUiWasShown = true
        } else if (pinUiWasShown) {
            scheduleReconcile()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val selection = currentSelection()
        outState.putString(STATE_APPEARANCE, selection.appearance.name)
        outState.putString(STATE_BACKGROUND, selection.background.name)
        outState.putBoolean(STATE_AWAITING_PIN, awaitingPinResult)
        outState.putBoolean(STATE_PIN_UI_SHOWN, pinUiWasShown)
        outState.putIntArray(STATE_BASELINE_IDS, baselineWidgetIds.toIntArray())
        pendingSnapshot?.let { snapshot ->
            outState.putString(STATE_REQUEST_TOKEN, snapshot.requestToken)
            outState.putString(STATE_REQUEST_EVENT_ID, snapshot.eventId)
            outState.putString(STATE_REQUEST_APPEARANCE, snapshot.style.appearance.name)
            outState.putString(STATE_REQUEST_BACKGROUND, snapshot.style.background.name)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        loadGeneration += 1
        reconcileGeneration += 1
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
                findViewById<ImageView>(R.id.widgetPinSetupEventIcon).apply {
                    setImageResource(EventIconPresentation.drawableRes(loaded.icon))
                    contentDescription = getString(EventIconPresentation.labelRes(loaded.icon))
                }
                findViewById<TextView>(R.id.widgetPinSetupEventTitle).text = loaded.title
                val locale = resources.configuration.locales[0]
                val displayDate = CountdownOccurrenceResolver.displayDate(
                    loaded,
                    CountdownTime.snapshot().today,
                )
                findViewById<TextView>(R.id.widgetPinSetupEventDate).text = displayDate.format(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale),
                )
                setAddEnabled(!awaitingPinResult)
                renderBackgroundChoices()
                updatePreview()
            },
        )
    }

    private fun requestPin() {
        val currentEvent = event ?: return
        val style = currentSelection()
        val snapshot = WidgetPinRequestSnapshot.create(currentEvent.id, style)
        baselineWidgetIds = providerWidgetIds()
        pendingSnapshot = snapshot
        awaitingPinResult = true
        pinUiWasShown = false
        reconcileAttempt = 0
        reconcileGeneration += 1
        setAddEnabled(false)

        if (!WidgetPinning.request(this, currentEvent, snapshot)) {
            clearPendingPin()
            Toast.makeText(
                this,
                R.string.widget_pin_unavailable_guidance,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun scheduleReconcile() {
        if (reconcileScheduled || !awaitingPinResult) return
        reconcileScheduled = true
        val generation = reconcileGeneration
        val delay = RECONCILE_DELAYS_MS.getOrElse(reconcileAttempt) { RECONCILE_DELAYS_MS.last() }
        window.decorView.postDelayed({
            reconcileScheduled = false
            if (
                generation == reconcileGeneration &&
                awaitingPinResult &&
                !isFinishing &&
                !isDestroyed
            ) {
                reconcilePinResult()
            }
        }, delay)
    }

    private fun reconcilePinResult() {
        val snapshot = pendingSnapshot ?: run {
            clearPendingPin()
            return
        }
        when (val outcome = WidgetPinFallbackResolver.resolve(
            baselineIds = baselineWidgetIds,
            currentIds = providerWidgetIds(),
        )) {
            WidgetPinFallbackOutcome.None -> {
                if (reconcileAttempt < RECONCILE_DELAYS_MS.lastIndex) {
                    reconcileAttempt += 1
                    scheduleReconcile()
                } else {
                    clearPendingPin()
                    Toast.makeText(
                        this,
                        R.string.widget_pin_not_added,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            is WidgetPinFallbackOutcome.Multiple -> {
                clearPendingPin()
                Toast.makeText(
                    this,
                    R.string.widget_pin_multiple_detected,
                    Toast.LENGTH_LONG,
                ).show()
            }
            is WidgetPinFallbackOutcome.Single -> finalizePin(outcome.appWidgetId, snapshot)
        }
    }

    private fun finalizePin(
        appWidgetId: Int,
        snapshot: WidgetPinRequestSnapshot,
    ) {
        val generation = reconcileGeneration
        CountdownIo.submit(
            task = {
                WidgetPinFinalizer.finalize(
                    context = applicationContext,
                    appWidgetId = appWidgetId,
                    snapshot = snapshot,
                )
            },
            onComplete = { result ->
                if (generation != reconcileGeneration || isFinishing || isDestroyed) return@submit
                val finalized = result.getOrNull()
                if (
                    finalized == WidgetPinFinalizeResult.APPLIED ||
                    finalized == WidgetPinFinalizeResult.ALREADY_MATCHING
                ) {
                    clearPendingPin()
                    finish()
                } else {
                    clearPendingPin()
                    val message = when (finalized) {
                        WidgetPinFinalizeResult.EVENT_MISSING -> R.string.widget_pin_event_unavailable
                        WidgetPinFinalizeResult.CONFLICT -> R.string.widget_pin_conflict
                        else -> R.string.widget_pin_not_added
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    private fun clearPendingPin() {
        awaitingPinResult = false
        pinUiWasShown = false
        pendingSnapshot = null
        baselineWidgetIds = emptySet()
        reconcileAttempt = 0
        reconcileGeneration += 1
        reconcileScheduled = false
        setAddEnabled(event != null)
    }

    private fun providerWidgetIds(): Set<Int> {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val provider = ComponentName(applicationContext, CountdownWidgetProvider::class.java)
        return runCatching { manager.getAppWidgetIds(provider).toSet() }.getOrDefault(emptySet())
    }

    private fun restorePendingSnapshot(state: Bundle?): WidgetPinRequestSnapshot? {
        if (state == null || !awaitingPinResult) return null
        val token = state.getString(STATE_REQUEST_TOKEN)?.takeIf(String::isNotBlank) ?: return null
        val eventId = state.getString(STATE_REQUEST_EVENT_ID)?.takeIf(String::isNotBlank) ?: return null
        val appearance = WidgetAppearance.entries.firstOrNull {
            it.name == state.getString(STATE_REQUEST_APPEARANCE)
        } ?: return null
        val background = WidgetBackground.entries.firstOrNull {
            it.name == state.getString(STATE_REQUEST_BACKGROUND)
        } ?: return null
        return WidgetPinRequestSnapshot.create(
            eventId = eventId,
            style = WidgetStyleSelection(appearance, background),
            requestToken = token,
        )
    }

    private fun currentSelection(): WidgetStyleSelection = WidgetStyleSelection(
        appearance = when (appearanceGroup.checkedRadioButtonId) {
            R.id.widgetPinSetupAppearanceLight -> WidgetAppearance.LIGHT
            R.id.widgetPinSetupAppearanceDark -> WidgetAppearance.DARK
            else -> WidgetAppearance.SYSTEM
        },
        background = selectedBackground,
    )

    private fun applyAppearance(appearance: WidgetAppearance) {
        appearanceGroup.check(
            when (appearance) {
                WidgetAppearance.SYSTEM -> R.id.widgetPinSetupAppearanceSystem
                WidgetAppearance.LIGHT -> R.id.widgetPinSetupAppearanceLight
                WidgetAppearance.DARK -> R.id.widgetPinSetupAppearanceDark
            },
        )
    }

    private fun renderBackgroundChoices() {
        backgroundRows.removeAllViews()
        val appearance = currentSelection().appearance
        WidgetBackground.entries.chunked(3).forEach { backgrounds ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            backgrounds.forEach { background ->
                row.addView(
                    backgroundChoice(background, appearance),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    },
                )
            }
            repeat(3 - backgrounds.size) {
                row.addView(
                    FrameLayout(this),
                    LinearLayout.LayoutParams(0, 1, 1f),
                )
            }
            backgroundRows.addView(row)
        }
    }

    private fun backgroundChoice(
        background: WidgetBackground,
        appearance: WidgetAppearance,
    ): LinearLayout {
        val selected = background == selectedBackground
        val label = getString(backgroundLabel(background))
        val theme = WidgetThemeResolver.resolve(this, appearance, background)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(dp(6), dp(6), dp(6), dp(7))
            setBackgroundResource(
                if (selected) R.drawable.language_chip_active else R.drawable.language_chip_inactive,
            )
            contentDescription = if (selected) {
                getString(R.string.widget_pin_setup_style_selected, label)
            } else {
                label
            }
            setOnClickListener {
                selectedBackground = background
                renderBackgroundChoices()
                updatePreview()
            }

            addView(
                ImageView(this@WidgetPinSetupActivity).apply {
                    scaleType = ImageView.ScaleType.FIT_XY
                    setImageBitmap(
                        WidgetBackgroundRenderer.render(
                            context = applicationContext,
                            background = background,
                            dark = theme.dark,
                            widthDp = 72,
                            heightDp = 44,
                        ),
                    )
                    contentDescription = null
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(44),
                ),
            )
            addView(
                TextView(this@WidgetPinSetupActivity).apply {
                    text = label
                    gravity = Gravity.CENTER
                    setTextColor(getColor(R.color.foreground))
                    textSize = 12f
                    maxLines = 1
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(5) },
            )
        }
    }

    private fun backgroundLabel(background: WidgetBackground): Int = when (background) {
        WidgetBackground.CLASSIC -> R.string.widget_background_classic
        WidgetBackground.MIST -> R.string.widget_background_mist
        WidgetBackground.HORIZON -> R.string.widget_background_horizon
        WidgetBackground.FOREST -> R.string.widget_background_forest
        WidgetBackground.SUNSET -> R.string.widget_background_sunset
        WidgetBackground.PULSE -> R.string.widget_background_pulse
        WidgetBackground.BREEZE -> R.string.widget_background_breeze
        WidgetBackground.EMBER -> R.string.widget_background_ember
        WidgetBackground.MONOGRAM -> R.string.widget_background_six
    }

    private fun updatePreview() {
        val selection = currentSelection()
        findViewById<TextView>(R.id.widgetPinSetupStyleSummary).text = getString(
            R.string.widget_style_summary,
            getString(backgroundLabel(selection.background)),
            getString(appearanceLabel(selection.appearance)),
        )
        val currentEvent = event ?: return
        previewController.renderStyle(
            selection = selection,
            dimensions = WidgetPreviewSizing.representative(WidgetSize.STANDARD),
        )
        previewController.renderEvent(
            WidgetEventContentFactory.from(currentEvent, CountdownTime.snapshot().today),
        )
    }

    private fun appearanceLabel(appearance: WidgetAppearance): Int = when (appearance) {
        WidgetAppearance.SYSTEM -> R.string.widget_appearance_system
        WidgetAppearance.LIGHT -> R.string.widget_appearance_light
        WidgetAppearance.DARK -> R.string.widget_appearance_dark
    }

    private fun setAddEnabled(enabled: Boolean) {
        addButton.isEnabled = enabled
        addButton.alpha = if (enabled) 1f else 0.45f
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_EVENT_ID = "event_id"

        private const val STATE_APPEARANCE = "pin_setup_appearance"
        private const val STATE_BACKGROUND = "pin_setup_background"
        private const val STATE_AWAITING_PIN = "pin_setup_awaiting_pin"
        private const val STATE_PIN_UI_SHOWN = "pin_setup_pin_ui_shown"
        private const val STATE_BASELINE_IDS = "pin_setup_baseline_ids"
        private const val STATE_REQUEST_TOKEN = "pin_setup_request_token"
        private const val STATE_REQUEST_EVENT_ID = "pin_setup_request_event_id"
        private const val STATE_REQUEST_APPEARANCE = "pin_setup_request_appearance"
        private const val STATE_REQUEST_BACKGROUND = "pin_setup_request_background"

        private val RECONCILE_DELAYS_MS = longArrayOf(250L, 700L, 1_400L)
    }
}
