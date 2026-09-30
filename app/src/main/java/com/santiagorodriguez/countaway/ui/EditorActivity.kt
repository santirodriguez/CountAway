package com.santiagorodriguez.countaway.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.NotificationManager
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownDateDomain
import com.santiagorodriguez.countaway.countdown.CountdownOccurrenceResolver
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.data.CountdownDataProblem
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownMutationResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.data.CountdownValidation
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountUpPreset
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventIcon
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.ReminderOption
import com.santiagorodriguez.countaway.model.RepeatRule
import com.santiagorodriguez.countaway.notification.ArrivalNotificationPolicy
import com.santiagorodriguez.countaway.notification.ArrivalNotificationScheduler
import com.santiagorodriguez.countaway.share.ShareCardContentFactory
import com.santiagorodriguez.countaway.share.ShareCardRenderer
import com.santiagorodriguez.countaway.share.ShareImageStore
import com.santiagorodriguez.countaway.data.CountdownMutations
import com.santiagorodriguez.countaway.data.EventRevision
import com.santiagorodriguez.countaway.data.RetainedOperation
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

class EditorActivity : BaseActivity() {
    private val eventTypes = EventType.entries.toList()
    private val customIcons = (EventIcon.customChoices + listOf(EventIcon.BOOK, EventIcon.HOURGLASS)).distinct()
    private var reminderOptions: List<ReminderOption> = emptyList()
    private lateinit var repository: CountdownRepository
    private lateinit var editorRoot: View
    private lateinit var titleInput: EditText
    private lateinit var typeGrid: GridLayout
    private lateinit var customIconSection: View
    private lateinit var iconGrid: GridLayout
    private lateinit var dateButton: Button
    private lateinit var modeButton: Button
    private lateinit var countUpModeButton: Button
    private lateinit var saveButton: Button
    private lateinit var shareButton: Button
    private lateinit var deleteButton: Button
    private lateinit var repeatSpinner: Spinner
    private lateinit var reminderSpinner: Spinner
    private lateinit var scheduleSummary: TextView
    private lateinit var temporalInvalidationController: TemporalInvalidationController
    private var existingEvent: CountdownEvent? = null
    private var selectedDate: LocalDate = CountdownTime.snapshot().today.plusDays(1)
    private var selectedType: EventType = EventType.TRIP
    private var selectedIcon: EventIcon = EventIcon.defaultFor(EventType.TRIP)
    private var selectedRepeatRule: RepeatRule = RepeatRule.NONE
    private var selectedReminder: ReminderOption = ReminderOption.OFF
    private var selectedCountMode: CountMode = CountMode.COUNT_DOWN
    private var selectedPreset: CountUpPreset? = null
    private var dateChosen = false
    private var typeChosen = false
    private var pendingCountUpConfirmation = false
    private var modeDialog: AlertDialog? = null
    private var suppressRepeatSelection = false
    private var suppressReminderSelection = false
    private var editorInitialized = false
    private var editorBusy = true
    private lateinit var session: EditorSession
    private var backCallback: OnBackInvokedCallback? = null
    private var loadGeneration = 0
    private var pendingEditorState: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        editorRoot = findViewById(R.id.editorRoot)
        InsetUtils.applySystemBarPadding(editorRoot)

        repository = CountdownRepository(applicationContext)
        session = lastNonConfigurationInstance as? EditorSession ?: EditorSession(savedInstanceState)
        session.operation.onChanged = { consumeOperation() }
        titleInput = findViewById(R.id.titleInput)
        typeGrid = findViewById(R.id.typeGrid)
        typeGrid.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (editorInitialized && right > left && right - left != oldRight - oldLeft) renderTypeGrid()
        }
        customIconSection = findViewById(R.id.customIconSection)
        iconGrid = findViewById(R.id.iconGrid)
        dateButton = findViewById(R.id.dateButton)
        modeButton = findViewById(R.id.modeButton)
        countUpModeButton = findViewById(R.id.countUpModeButton)
        ChoiceAccessibility.apply(modeButton)
        ChoiceAccessibility.apply(countUpModeButton)
        saveButton = findViewById(R.id.saveButton)
        shareButton = findViewById(R.id.shareButton)
        deleteButton = findViewById(R.id.deleteButton)
        repeatSpinner = findViewById(R.id.repeatSpinner)
        reminderSpinner = findViewById(R.id.reminderSpinner)
        scheduleSummary = findViewById(R.id.editorScheduleSummary)
        registerBackCallback()
        temporalInvalidationController = TemporalInvalidationController(this) { snapshot ->
            if (editorInitialized) refreshReminderSpinner(snapshot.today)
        }

        pendingEditorState = savedInstanceState
        setEditorBusy(true)
        loadEditorData(savedInstanceState)
    }

    private fun loadEditorData(savedInstanceState: Bundle?) {
        val generation = ++loadGeneration
        val repository = repository
        CountdownIo.submit(
            task = { repository.loadResult() },
            onComplete = { result ->
                if (generation != loadGeneration || isFinishing || isDestroyed) return@submit
                when (val loaded = result.getOrElse {
                    CountdownLoadResult.Failure(CountdownDataProblem.CORRUPT)
                }) {
                    is CountdownLoadResult.Success -> initializeEditor(loaded.events, savedInstanceState)
                    is CountdownLoadResult.Failure -> {
                        val message = if (loaded.problem == CountdownDataProblem.UNSUPPORTED_SCHEMA) {
                            R.string.data_newer_version_edit_blocked
                        } else {
                            R.string.data_error_edit_blocked
                        }
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            },
        )
    }

    private fun initializeEditor(loadedEvents: List<CountdownEvent>, savedInstanceState: Bundle?) {
        val requestedEventId = intent.getStringExtra(EXTRA_EVENT_ID)
        if (requestedEventId == null && loadedEvents.any { it.id == session.id }) {
            finish()
            return
        }
        existingEvent = requestedEventId?.let { id -> loadedEvents.firstOrNull { it.id == id } }
        if (requestedEventId != null && existingEvent == null) {
            finish()
            return
        }
        findViewById<TextView>(R.id.editorHeading).setText(
            if (existingEvent == null) R.string.event_new_title else R.string.event_edit_title,
        )
        dateChosen = existingEvent != null
        typeChosen = existingEvent != null
        selectedPreset = null
        existingEvent?.let { event ->
            titleInput.setText(event.title)
            selectedDate = event.date
            selectedType = event.type
            selectedIcon = event.icon
            selectedRepeatRule = event.repeatRule
            selectedReminder = event.reminder
            selectedCountMode = event.countMode
        }
        if (selectedCountMode == CountMode.COUNT_UP) selectedPreset = CountUpPreset.forStoredType(selectedType)
        if (!session.initialized) {
            session.originalRevision = existingEvent?.let(EventRevision::of)
            session.baselineRevision = currentDraft().revision()
            session.initialized = true
        }
        savedInstanceState?.let(::restoreEditorState)
        titleInput.filters = titleInput.filters + InputFilter.LengthFilter(CountdownValidation.MAX_TITLE_LENGTH)

        renderTypeGrid()
        renderCustomIconGrid()
        renderTitleHint()
        renderDate()
        renderMode()
        configureRepeatSpinner()
        configureReminderSpinner()
        modeButton.setOnClickListener { selectCountMode(CountMode.COUNT_DOWN) }
        countUpModeButton.setOnClickListener { selectCountMode(CountMode.COUNT_UP) }
        dateButton.setOnClickListener { showDatePicker() }
        saveButton.setOnClickListener { save() }
        shareButton.setOnClickListener { share() }
        deleteButton.visibility = if (existingEvent == null) View.GONE else View.VISIBLE
        deleteButton.setOnClickListener { confirmDelete() }
        pendingEditorState = null
        editorInitialized = true
        consumeOperation()
        if (pendingCountUpConfirmation && !editorBusy) showCountUpConfirmation()
    }

    override fun onRetainNonConfigurationInstance(): Any = session

    override fun onResume() {
        super.onResume()
        val snapshot = CountdownTime.snapshot()
        temporalInvalidationController.start(snapshot)
        if (editorInitialized) refreshReminderSpinner(snapshot.today)
    }

    override fun onPause() {
        temporalInvalidationController.stop()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (!editorInitialized) pendingEditorState?.let(outState::putAll)
        if (editorInitialized) {
            outState.putString(STATE_TITLE, titleInput.text.toString())
            outState.putString(STATE_DATE, selectedDate.toString())
            outState.putString(STATE_TYPE, selectedType.name)
            outState.putString(STATE_ICON, selectedIcon.name)
            outState.putString(STATE_REPEAT_RULE, selectedRepeatRule.name)
            outState.putString(STATE_REMINDER, selectedReminder.name)
            outState.putString(STATE_COUNT_MODE, selectedCountMode.name)
            outState.putString(STATE_PRESET, selectedPreset?.name)
            outState.putBoolean(STATE_DATE_CHOSEN, dateChosen)
            outState.putBoolean(STATE_TYPE_CHOSEN, typeChosen)
            outState.putBoolean(STATE_MODE_CONFIRMATION, pendingCountUpConfirmation)
        }
        session.writeState(outState)
        super.onSaveInstanceState(outState)
    }

    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) super.onBackPressed() else handleBackRequest()
    }

    override fun onDestroy() {
        modeDialog?.setOnDismissListener(null)
        modeDialog?.dismiss()
        modeDialog = null
        session.operation.onChanged = null
        temporalInvalidationController.stop()
        unregisterBackCallback()
        loadGeneration += 1
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_NOTIFICATIONS || selectedCountMode == CountMode.COUNT_UP) return
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            selectedReminder = ReminderOption.OFF
            refreshReminderSpinner(CountdownTime.snapshot().today)
            Toast.makeText(this, R.string.notification_permission_denied, Toast.LENGTH_SHORT).show()
        } else {
            warnIfNotificationsBlocked(CountdownTime.snapshot().today)
        }
    }

    private fun renderMode() {
        val vertical = resources.configuration.fontScale >= 1.5f
        findViewById<LinearLayout>(R.id.modeSelector).orientation =
            if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        listOf(modeButton, countUpModeButton).forEachIndexed { index, button ->
            button.layoutParams = (button.layoutParams as LinearLayout.LayoutParams).apply {
                width = if (vertical) ViewGroup.LayoutParams.MATCH_PARENT else 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                weight = if (vertical) 0f else 1f
                marginEnd = if (!vertical && index == 0) dp(4) else 0
                bottomMargin = if (vertical && index == 0) dp(4) else 0
            }
        }
        modeButton.isSelected = selectedCountMode == CountMode.COUNT_DOWN
        countUpModeButton.isSelected = selectedCountMode == CountMode.COUNT_UP
        modeButton.contentDescription = getString(R.string.count_mode_label) + ": " + modeButton.text
        countUpModeButton.contentDescription = getString(R.string.count_mode_label) + ": " + countUpModeButton.text
        findViewById<TextView>(R.id.presetHint).setText(
            if (selectedCountMode == CountMode.COUNT_UP) R.string.preset_count_up_hint else R.string.preset_count_down_hint,
        )
        findViewById<TextView>(R.id.dateLabel).setText(
            if (selectedCountMode == CountMode.COUNT_UP) R.string.count_up_start_date else R.string.field_date,
        )
        val visibility = if (selectedCountMode == CountMode.COUNT_UP) View.GONE else View.VISIBLE
        findViewById<View>(R.id.repeatSection).visibility = visibility
        findViewById<View>(R.id.reminderSection).visibility = visibility
    }

    private fun selectCountMode(next: CountMode) {
        if (editorBusy || modeDialog?.isShowing == true || next == selectedCountMode) return
        if (next == CountMode.COUNT_UP &&
            (selectedRepeatRule != RepeatRule.NONE || selectedReminder != ReminderOption.OFF)) {
            pendingCountUpConfirmation = true
            showCountUpConfirmation()
        } else {
            applyCountMode(next)
        }
    }

    private fun showCountUpConfirmation() {
        if (selectedCountMode == CountMode.COUNT_UP) {
            pendingCountUpConfirmation = false
            return
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.count_mode_confirm_title)
            .setMessage(R.string.count_mode_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.count_mode_confirm_action) { _, _ -> applyCountMode(CountMode.COUNT_UP) }
            .create()
        modeDialog = dialog
        dialog.setOnDismissListener {
            pendingCountUpConfirmation = false
            if (modeDialog === dialog) modeDialog = null
            modeButton.requestFocusFromTouch()
        }
        dialog.show()
    }

    private fun applyCountMode(mode: CountMode) {
        val snapshot = CountdownTime.snapshot()
        val nextType = EventCreationDefaults.typeForMode(selectedType, mode, existingEvent == null, typeChosen)
        if (nextType != selectedType) {
            selectedType = nextType
            selectedIcon = EventIcon.defaultFor(nextType)
        }
        selectedDate = EventCreationDefaults.dateForMode(selectedDate, mode, snapshot.today,
            existingEvent == null, dateChosen)
        selectedCountMode = mode
        if (mode == CountMode.COUNT_UP && selectedPreset?.type != selectedType) {
            selectedPreset = CountUpPreset.forStoredType(selectedType)
        }
        pendingCountUpConfirmation = false
        if (mode == CountMode.COUNT_UP) {
            selectedRepeatRule = RepeatRule.NONE
            selectedReminder = ReminderOption.OFF
            suppressRepeatSelection = true
            repeatSpinner.setSelection(RepeatRule.entries.indexOf(RepeatRule.NONE))
            suppressRepeatSelection = false
        }
        renderMode()
        renderTypeGrid()
        renderCustomIconGrid()
        renderTitleHint()
        renderDate()
        refreshReminderSpinner(snapshot.today)
    }

    private fun configureRepeatSpinner() {
        repeatSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            RepeatRule.entries.map(::repeatLabel)).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        suppressRepeatSelection = true
        repeatSpinner.setSelection(RepeatRule.entries.indexOf(selectedRepeatRule))
        suppressRepeatSelection = false
        repeatSpinner.onItemSelectedListener = SimpleItemSelectedListener { position ->
            if (suppressRepeatSelection || selectedCountMode == CountMode.COUNT_UP) return@SimpleItemSelectedListener
            val next = RepeatRule.entries.getOrNull(position) ?: return@SimpleItemSelectedListener
            if (next == selectedRepeatRule) return@SimpleItemSelectedListener
            selectedRepeatRule = next
            val snapshot = CountdownTime.snapshot()
            refreshReminderSpinner(snapshot.today)
            handleReminderSelectionEffect(ReminderEditorPolicy.repeatChangeEffect(
                existingEvent = existingEvent, selectedDate = selectedDate, selectedReminder = selectedReminder,
                selectedRepeatRule = selectedRepeatRule, today = snapshot.today,
            ), snapshot.today)
        }
    }

    private fun repeatLabel(repeatRule: RepeatRule): String = when (repeatRule) {
        RepeatRule.NONE -> getString(R.string.repeat_never)
        RepeatRule.WEEKLY -> getString(R.string.repeat_weekly)
        RepeatRule.MONTHLY -> getString(R.string.repeat_monthly)
        RepeatRule.YEARLY -> getString(R.string.repeat_yearly)
    }

    private fun configureReminderSpinner() {
        refreshReminderSpinner(CountdownTime.snapshot().today)
        reminderSpinner.onItemSelectedListener = SimpleItemSelectedListener { position ->
            if (!suppressReminderSelection && selectedCountMode == CountMode.COUNT_DOWN) {
                val next = reminderOptions.getOrNull(position) ?: return@SimpleItemSelectedListener
                val snapshot = CountdownTime.snapshot()
                val effect = ReminderEditorPolicy.selectionEffect(
                    currentReminder = selectedReminder, nextReminder = next, existingEvent = existingEvent,
                    selectedDate = selectedDate, today = snapshot.today, selectedRepeatRule = selectedRepeatRule,
                )
                selectedReminder = next
                refreshReminderSpinner(snapshot.today)
                handleReminderSelectionEffect(effect, snapshot.today)
            }
        }
    }

    private fun refreshReminderSpinner(today: LocalDate) {
        val nextOptions = if (selectedCountMode == CountMode.COUNT_UP) listOf(ReminderOption.OFF) else
            ReminderEditorPolicy.availableOptions(existingEvent, selectedDate, selectedReminder, today, selectedRepeatRule)
        val optionsChanged = nextOptions != reminderOptions
        reminderOptions = nextOptions
        suppressReminderSelection = true
        if (optionsChanged || reminderSpinner.adapter == null) {
            reminderSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
                reminderOptions.map(::reminderLabel)).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        reminderSpinner.setSelection(reminderOptions.indexOf(selectedReminder).coerceAtLeast(0))
        suppressReminderSelection = false
        renderScheduleSummary(today)
    }

    private fun reminderLabel(reminder: ReminderOption): String = when (reminder) {
        ReminderOption.OFF -> getString(R.string.reminder_off)
        ReminderOption.ON_DAY -> getString(R.string.reminder_on_day)
        ReminderOption.ONE_DAY -> getString(R.string.reminder_one_day)
        ReminderOption.THREE_DAYS -> getString(R.string.reminder_three_days)
        ReminderOption.SEVEN_DAYS -> getString(R.string.reminder_seven_days)
    }

    private data class PresetChoice(
        val key: String, val type: EventType, val icon: EventIcon, val labelRes: Int,
        val preset: CountUpPreset? = null,
    )

    private fun presetChoices(): List<PresetChoice> = if (selectedCountMode == CountMode.COUNT_UP) {
        CountUpPreset.entries.map { preset ->
            PresetChoice("up:${preset.name}", preset.type, preset.icon,
                CountUpPresetPresentation.labelRes(preset), preset)
        }
    } else {
        eventTypes.map { type ->
            PresetChoice("down:${type.name}", type, EventIcon.defaultFor(type), EventTypePresentation.labelRes(type))
        }
    }

    private fun renderTypeGrid() {
        val choices = presetChoices()
        val availableWidth = typeGrid.width.takeIf { it > 0 }
            ?: dp((resources.configuration.screenWidthDp - 48).coerceAtLeast(112))
        val preferredCardWidth = dp(112) * resources.configuration.fontScale.coerceAtLeast(1f)
        val columns = (availableWidth / preferredCardWidth).toInt().coerceIn(1, 3)
        val selectedKey = if (selectedCountMode == CountMode.COUNT_UP) {
            (selectedPreset ?: CountUpPreset.forStoredType(selectedType))?.let { "up:${it.name}" }
        } else "down:${selectedType.name}"
        val notice = findViewById<TextView>(R.id.presetPreservedNotice)
        notice.visibility = if (selectedCountMode == CountMode.COUNT_UP && selectedKey == null) View.VISIBLE else View.GONE
        if (notice.visibility == View.VISIBLE) {
            notice.text = getString(R.string.preset_preserved_type, getString(EventTypePresentation.labelRes(selectedType)))
        }
        val sameChoices = typeGrid.columnCount == columns && typeGrid.childCount == choices.size &&
            choices.indices.all { typeGrid.getChildAt(it).tag == choices[it].key }
        if (!sameChoices) {
            typeGrid.removeAllViews()
            typeGrid.columnCount = columns
            choices.forEach { choice ->
                val button = TextView(this).apply {
                    ChoiceAccessibility.apply(this)
                    tag = choice.key
                    text = getString(choice.labelRes)
                    contentDescription = text
                    gravity = Gravity.CENTER
                    textSize = 13f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(getColorStateList(R.color.preset_choice_text))
                    minHeight = dp(88)
                    setPadding(dp(8), dp(12), dp(8), dp(12))
                    setCompoundDrawablesWithIntrinsicBounds(0, EventIconPresentation.drawableRes(choice.icon), 0, 0)
                    compoundDrawablePadding = dp(8)
                    compoundDrawableTintList = ColorStateList.valueOf(getColor(R.color.accent_text))
                    setBackgroundResource(R.drawable.preset_choice_background)
                    setOnClickListener { selectPresetChoice(choice) }
                }
                typeGrid.addView(button, gridParams().apply {
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL)
                })
            }
        }
        choices.forEachIndexed { index, choice ->
            typeGrid.getChildAt(index).isSelected = choice.key == selectedKey
        }
    }

    private fun selectPresetChoice(choice: PresetChoice) {
        if (editorBusy) return
        typeChosen = true
        selectedType = choice.type
        selectedPreset = choice.preset
        selectedIcon = if (choice.type == EventType.CUSTOM &&
            (choice.preset == null || choice.preset == CountUpPreset.CUSTOM)) {
            selectedIcon.takeIf { it in customIcons } ?: EventIcon.STAR
        } else choice.icon
        choice.preset?.let { preset ->
            val current = titleInput.text.toString()
            val suggested = preset.titleFor(current, getString(choice.labelRes))
            if (suggested != current) titleInput.setText(suggested)
        }
        renderTypeGrid()
        renderCustomIconGrid()
        renderTitleHint()
    }

    private fun renderCustomIconGrid() {
        customIconSection.visibility = if (selectedType == EventType.CUSTOM) View.VISIBLE else View.GONE
        if (selectedType != EventType.CUSTOM) return
        if (iconGrid.childCount > 0) {
            customIcons.forEachIndexed { index, icon ->
                iconGrid.getChildAt(index).isSelected = icon == selectedIcon
            }
            return
        }
        customIcons.forEach { icon ->
            val button = ImageButton(this).apply {
                ChoiceAccessibility.apply(this)
                setImageResource(EventIconPresentation.drawableRes(icon))
                imageTintList = ColorStateList.valueOf(getColor(R.color.accent_text))
                backgroundTintList = null
                isSelected = icon == selectedIcon
                setBackgroundResource(R.drawable.preset_choice_background)
                contentDescription = getString(EventIconPresentation.labelRes(icon))
                tooltipText = contentDescription
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                minimumHeight = dp(56)
                setPadding(dp(15), dp(15), dp(15), dp(15))
                setOnClickListener {
                    selectedIcon = icon
                    typeChosen = true
                    selectedPreset = CountUpPreset.CUSTOM
                    renderTypeGrid()
                    renderCustomIconGrid()
                    renderTitleHint()
                }
            }
            iconGrid.addView(button, gridParams())
        }
    }

    private fun renderTitleHint() {
        val preset = selectedPreset.takeIf { selectedCountMode == CountMode.COUNT_UP }
        titleInput.hint = getString(preset?.let(CountUpPresetPresentation::labelRes)
            ?: EventTypePresentation.labelRes(selectedType))
    }

    private fun showDatePicker() {
        val pickerSnapshot = CountdownTime.snapshot()
        val dialog = DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val previousDate = selectedDate
            selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
            dateChosen = true
            renderDate()
            if (selectedDate != previousDate) {
                val snapshot = CountdownTime.snapshot()
                refreshReminderSpinner(snapshot.today)
                if (selectedCountMode == CountMode.COUNT_DOWN) handleReminderSelectionEffect(
                    ReminderEditorPolicy.dateChangeEffect(existingEvent, selectedDate, selectedReminder,
                        snapshot.today, selectedRepeatRule), snapshot.today,
                )
            }
        }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
        dialog.datePicker.minDate = CountdownDateDomain.MIN_DATE.atStartOfDay(pickerSnapshot.zone).toInstant().toEpochMilli()
        dialog.datePicker.maxDate = CountdownDateDomain.MAX_DATE.atStartOfDay(pickerSnapshot.zone).toInstant().toEpochMilli()
        dialog.show()
    }

    private fun renderDate() {
        val locale = resources.configuration.locales[0]
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
        dateButton.text = selectedDate.format(formatter)
    }

    private fun share() {
        val title = titleInput.text.toString().trim()
        if (title.isEmpty()) {
            titleInput.error = getString(R.string.title_required)
            return
        }
        val snapshot = CountdownTime.snapshot()
        val text = ShareCardContentFactory.text(this, title, selectedDate, selectedRepeatRule,
            snapshot.today, selectedCountMode)
        val renderContext = ShareCardRenderer.captureContext(this)
        val appContext = applicationContext
        val cardContent = ShareCardContentFactory.create(
            context = renderContext, title = title, date = selectedDate, icon = selectedIcon,
            repeatRule = selectedRepeatRule, today = snapshot.today, countMode = selectedCountMode,
        )
        val dark = ShareCardRenderer.resolveDark(this)
        shareButton.isEnabled = false
        CountdownIo.submit(
            task = {
                val bitmap = ShareCardRenderer.render(renderContext, cardContent, dark)
                try { ShareImageStore.write(appContext, bitmap) } finally { bitmap.recycle() }
            },
            onComplete = { result ->
                if (isFinishing || isDestroyed) return@submit
                shareButton.isEnabled = true
                val imageUri = result.getOrNull()
                if (imageUri != null && launchShare(imageShareIntent(title, text, imageUri))) return@submit
                if (!launchShare(textShareIntent(text))) {
                    Toast.makeText(this, R.string.share_unavailable, Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    private fun imageShareIntent(title: String, text: String, imageUri: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, imageUri)
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_TITLE, title)
        clipData = ClipData.newUri(contentResolver, title, imageUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun textShareIntent(text: String): Intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)

    private fun launchShare(sendIntent: Intent): Boolean = runCatching {
        startActivity(Intent.createChooser(sendIntent, getString(R.string.action_share)))
        true
    }.getOrDefault(false)

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun handleReminderSelectionEffect(effect: ReminderSelectionEffect, today: LocalDate) {
        when (effect) {
            ReminderSelectionEffect.NONE -> Unit
            ReminderSelectionEffect.SHOW_SCHEDULE_UNAVAILABLE ->
                Toast.makeText(this, R.string.reminder_schedule_unavailable, Toast.LENGTH_LONG).show()
            ReminderSelectionEffect.CHECK_NOTIFICATIONS -> {
                if (!ArrivalNotificationScheduler.hasNotificationPermission(this)) requestNotificationPermission()
                else warnIfNotificationsBlocked(today)
            }
        }
    }

    private fun canSaveSelectedReminder(today: LocalDate): Boolean =
        if (selectedCountMode == CountMode.COUNT_UP) {
            selectedReminder == ReminderOption.OFF && selectedRepeatRule == RepeatRule.NONE
        } else {
            ReminderEditorPolicy.canSave(existingEvent, selectedDate, selectedReminder, today, selectedRepeatRule)
        }

    private fun warnIfNotificationsBlocked(today: LocalDate) {
        if (selectedCountMode == CountMode.COUNT_UP || selectedReminder == ReminderOption.OFF ||
            !ArrivalNotificationPolicy.isSchedulePossible(selectedDate, selectedReminder, selectedRepeatRule, today) ||
            !ArrivalNotificationScheduler.hasNotificationPermission(this) ||
            ArrivalNotificationScheduler.canPostNotifications(this)) return
        AlertDialog.Builder(this)
            .setTitle(R.string.notification_blocked_title)
            .setMessage(R.string.notification_blocked_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.notification_open_settings) { _, _ -> openNotificationSettings() }
            .show()
    }

    private fun openNotificationSettings() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channelExists = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            notificationManager.getNotificationChannel(ArrivalNotificationScheduler.CHANNEL_ID) != null
        val intent = if (channelExists) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, ArrivalNotificationScheduler.CHANNEL_ID)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        runCatching { startActivity(intent) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))) }.onFailure {
                Toast.makeText(this, R.string.external_action_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun save() {
        if (editorBusy || pendingCountUpConfirmation) return
        if (existingEvent?.let(EventRevision::of) != session.originalRevision) {
            showDataConflict()
            return
        }
        val snapshot = CountdownTime.snapshot()
        val title = titleInput.text.toString().trim()
        if (title.isEmpty()) {
            titleInput.error = getString(R.string.title_required)
            return
        }
        val previousTitle = existingEvent?.title?.trim()
        if (!CountdownValidation.isTitleWithinLimit(title) && title != previousTitle) {
            titleInput.error = getString(R.string.title_too_long, CountdownValidation.MAX_TITLE_LENGTH)
            return
        }
        if (!canSaveSelectedReminder(snapshot.today)) {
            Toast.makeText(this, R.string.reminder_schedule_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        val event = CountdownEvent(
            id = existingEvent?.id ?: session.id,
            title = title,
            date = selectedDate,
            type = selectedType,
            icon = selectedIcon,
            reminder = selectedReminder,
            createdAt = existingEvent?.createdAt ?: session.createdAt,
            repeatRule = selectedRepeatRule,
            countMode = selectedCountMode,
        )
        val previous = existingEvent
        val revision = session.originalRevision
        val mutations = CountdownMutations(applicationContext)
        session.deleting = false
        setEditorBusy(true)
        session.operation.start { mutations.save(revision, previous, event) }
    }

    private fun confirmDelete() {
        if (editorBusy) return
        val event = existingEvent ?: return
        val revision = session.originalRevision ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_message, event.title))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                val mutations = CountdownMutations(applicationContext)
                session.deleting = true
                setEditorBusy(true)
                session.operation.start { mutations.delete(event.id, revision) }
            }
            .show()
    }

    private fun consumeOperation() {
        if (!editorInitialized || isFinishing || isDestroyed) return
        setEditorBusy(session.operation.running)
        val result = session.operation.takeResult() ?: return
        result.onSuccess { mutation ->
            when (mutation) {
                CountdownMutationResult.APPLIED -> finish()
                CountdownMutationResult.CONFLICT -> showDataConflict()
            }
        }.onFailure {
            Toast.makeText(this, if (session.deleting) R.string.data_delete_failed else R.string.data_save_failed,
                Toast.LENGTH_LONG).show()
        }
    }

    private fun restoreEditorState(state: Bundle) {
        state.getString(STATE_TITLE)?.let(titleInput::setText)
        state.getString(STATE_DATE)?.let { raw ->
            runCatching { LocalDate.parse(raw) }.getOrNull()?.let { selectedDate = it }
        }
        state.getString(STATE_TYPE)?.let { raw ->
            EventType.entries.firstOrNull { it.name == raw }?.let { selectedType = it }
        }
        state.getString(STATE_ICON)?.let { raw ->
            EventIcon.entries.firstOrNull { it.name == raw }?.let { selectedIcon = it }
        }
        state.getString(STATE_REPEAT_RULE)?.let { raw ->
            RepeatRule.entries.firstOrNull { it.name == raw }?.let { selectedRepeatRule = it }
        }
        state.getString(STATE_REMINDER)?.let { raw ->
            ReminderOption.entries.firstOrNull { it.name == raw }?.let { selectedReminder = it }
        }
        state.getString(STATE_COUNT_MODE)?.let { raw ->
            CountMode.entries.firstOrNull { it.name == raw }?.let { selectedCountMode = it }
        }
        selectedPreset = state.getString(STATE_PRESET)?.let { raw ->
            CountUpPreset.entries.firstOrNull { it.name == raw && it.type == selectedType }
        } ?: CountUpPreset.forStoredType(selectedType)
        dateChosen = state.getBoolean(STATE_DATE_CHOSEN, state.containsKey(STATE_DATE))
        typeChosen = state.getBoolean(STATE_TYPE_CHOSEN, state.containsKey(STATE_TYPE))
        pendingCountUpConfirmation = state.getBoolean(STATE_MODE_CONFIRMATION)
    }

    private fun showDataConflict() {
        AlertDialog.Builder(this)
            .setTitle(R.string.data_conflict_title)
            .setMessage(R.string.data_conflict_message)
            .setNegativeButton(R.string.data_conflict_keep_editing, null)
            .setPositiveButton(R.string.data_conflict_reload) { _, _ ->
                pendingEditorState = null
                pendingCountUpConfirmation = false
                session.initialized = false
                editorInitialized = false
                titleInput.filters = emptyArray()
                setEditorBusy(true)
                loadEditorData(null)
            }
            .show()
    }

    private fun setEditorBusy(busy: Boolean) {
        editorBusy = busy
        setViewTreeEnabled(editorRoot, !busy)
    }

    private fun setViewTreeEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) setViewTreeEnabled(view.getChildAt(index), enabled)
        }
    }

    private fun renderScheduleSummary(today: LocalDate) {
        if (selectedCountMode == CountMode.COUNT_UP) {
            scheduleSummary.setText(R.string.count_up_explanation)
            scheduleSummary.visibility = View.VISIBLE
            return
        }
        val lines = mutableListOf<String>()
        val locale = resources.configuration.locales[0]
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        if (selectedRepeatRule != RepeatRule.NONE) {
            val occurrence = CountdownOccurrenceResolver.displayDate(selectedDate, selectedRepeatRule, today)
            lines += getString(R.string.schedule_next_occurrence, occurrence.format(formatter),
                repeatLabel(selectedRepeatRule).lowercase(locale))
        }
        ArrivalNotificationPolicy.scheduledDate(selectedDate, selectedRepeatRule, selectedReminder, today)?.let { date ->
            lines += getString(R.string.schedule_next_reminder, date.format(formatter))
        }
        if (selectedRepeatRule == RepeatRule.MONTHLY && selectedDate.dayOfMonth >= 29) {
            lines += getString(R.string.schedule_month_end_note)
        }
        if (selectedRepeatRule == RepeatRule.YEARLY && selectedDate.monthValue == 2 && selectedDate.dayOfMonth == 29) {
            lines += getString(R.string.schedule_leap_day_note)
        }
        scheduleSummary.text = lines.joinToString("\n")
        scheduleSummary.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun registerBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || backCallback != null) return
        val callback = OnBackInvokedCallback { handleBackRequest() }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        backCallback = callback
    }

    private fun unregisterBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        backCallback?.let(onBackInvokedDispatcher::unregisterOnBackInvokedCallback)
        backCallback = null
    }

    private fun handleBackRequest() {
        if (editorBusy) return
        if (!editorInitialized || session.baselineRevision == currentDraft().revision()) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.unsaved_changes_title)
            .setMessage(R.string.unsaved_changes_message)
            .setNegativeButton(R.string.data_conflict_keep_editing, null)
            .setPositiveButton(R.string.unsaved_changes_discard) { _, _ -> finish() }
            .show()
    }

    private fun currentDraft(): EditorDraft = EditorDraft(
        title = titleInput.text.toString(), date = selectedDate, type = selectedType, icon = selectedIcon,
        repeatRule = selectedRepeatRule, reminder = selectedReminder, countMode = selectedCountMode,
    )

    private data class EditorDraft(
        val title: String, val date: LocalDate, val type: EventType, val icon: EventIcon,
        val repeatRule: RepeatRule, val reminder: ReminderOption, val countMode: CountMode,
    ) {
        fun revision(): String = EventRevision.ofFields(title, date.toString(), type.name,
            icon.name, repeatRule.name, reminder.name, countMode.name)
    }

    private class EditorSession(state: Bundle?) {
        val id: String = state?.getString("draft_id") ?: UUID.randomUUID().toString()
        val createdAt: Instant = state?.getString("draft_created")?.let(Instant::parse) ?: Instant.now()
        var initialized = state?.getBoolean("basis_initialized") ?: false
        var originalRevision = state?.getString("original_revision")
        var baselineRevision = state?.getString("baseline_revision")
        var deleting = state?.getBoolean("deleting") ?: false
        val operation = RetainedOperation<CountdownMutationResult>()
        fun writeState(state: Bundle) {
            state.putString("draft_id", id)
            state.putString("draft_created", createdAt.toString())
            state.putBoolean("basis_initialized", initialized)
            state.putString("original_revision", originalRevision)
            state.putString("baseline_revision", baselineRevision)
            state.putBoolean("deleting", deleting)
        }
    }

    private fun gridParams(): GridLayout.LayoutParams = GridLayout.LayoutParams().apply {
        width = 0
        height = ViewGroup.LayoutParams.WRAP_CONTENT
        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        setMargins(dp(4), dp(4), dp(4), dp(4))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
        private const val REQUEST_NOTIFICATIONS = 2401
        private const val STATE_TITLE = "editor_title"
        private const val STATE_DATE = "editor_date"
        private const val STATE_TYPE = "editor_type"
        private const val STATE_ICON = "editor_icon"
        private const val STATE_REPEAT_RULE = "editor_repeat_rule"
        private const val STATE_REMINDER = "editor_reminder"
        private const val STATE_COUNT_MODE = "editor_count_mode"
        private const val STATE_PRESET = "editor_creation_preset"
        private const val STATE_DATE_CHOSEN = "editor_date_chosen"
        private const val STATE_TYPE_CHOSEN = "editor_type_chosen"
        private const val STATE_MODE_CONFIRMATION = "editor_mode_confirmation"
    }
}
