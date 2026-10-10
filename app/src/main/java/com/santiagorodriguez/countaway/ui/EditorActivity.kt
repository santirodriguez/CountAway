package com.santiagorodriguez.countaway.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
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
import com.santiagorodriguez.countaway.share.EventShareLauncher
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
    private val customIcons = EventIcon.customChoices
    private var reminderOptions: List<ReminderOption> = emptyList()
    private lateinit var repository: CountdownRepository
    private lateinit var editorRoot: View
    private lateinit var titleInput: EditText
    private lateinit var typeGrid: GridLayout
    private lateinit var choiceLayout: EditorChoiceLayout
    private lateinit var customIconSection: View
    private lateinit var iconButton: Button
    private lateinit var dateButton: Button
    private lateinit var modeButton: Button
    private lateinit var dialogFocus: EditorDialogFocus
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
    private var autoSuggestedTitle: String? = null
    private var changingSuggestedTitle = false
    private var dateChosen = false
    private var typeChosen = false
    private var pendingCountUpConfirmation = false
    private var modeDialog: AlertDialog? = null
    private var iconDialog: AlertDialog? = null
    private var dateDialog: DatePickerDialog? = null
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
        titleInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                // Once edited, even text equal to the former suggestion belongs to the user.
                if (editorInitialized && !changingSuggestedTitle) autoSuggestedTitle = null
            }
        })
        typeGrid = findViewById(R.id.typeGrid)
        choiceLayout = EditorChoiceLayout(typeGrid)
        typeGrid.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (editorInitialized && right > left && right - left != oldRight - oldLeft) renderTypeGrid()
        }
        customIconSection = findViewById(R.id.customIconSection)
        iconButton = findViewById(R.id.iconButton)
        dateButton = findViewById(R.id.dateButton)
        modeButton = findViewById(R.id.modeButton)
        dialogFocus = EditorDialogFocus(this)
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
        val duplicateEventId = intent.getStringExtra(EXTRA_DUPLICATE_EVENT_ID)
        if (requestedEventId != null && duplicateEventId != null) {
            finish()
            return
        }
        if (requestedEventId == null && loadedEvents.any { it.id == session.id }) {
            finish()
            return
        }
        existingEvent = requestedEventId?.let { id -> loadedEvents.firstOrNull { it.id == id } }
        if (requestedEventId != null && existingEvent == null) {
            finish()
            return
        }
        // Validate the selected saved snapshot once. A restored copy owns its own draft.
        val copy = if (duplicateEventId != null && !session.initialized) {
            val source = loadedEvents.firstOrNull { it.id == duplicateEventId }
            if (source == null || EventRevision.of(source) != intent.getStringExtra(EXTRA_SOURCE_REVISION)) {
                Toast.makeText(this, R.string.copy_source_changed, Toast.LENGTH_LONG).show()
                finish()
                return
            }
            EventCopyDraft.create(source, session.id, session.createdAt)
        } else null
        findViewById<TextView>(R.id.editorHeading).setText(
            when {
                existingEvent != null -> R.string.event_edit_title
                duplicateEventId != null -> R.string.event_copy_title
                else -> R.string.event_new_title
            },
        )
        dateChosen = existingEvent != null || duplicateEventId != null
        typeChosen = existingEvent != null || duplicateEventId != null
        selectedPreset = null
        autoSuggestedTitle = null
        (existingEvent ?: copy)?.let { event ->
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
            // An unsaved copy is already meaningful content, even before the first edit.
            session.baselineRevision = if (duplicateEventId == null) currentDraft().revision() else null
            session.initialized = true
        }
        savedInstanceState?.let(::restoreEditorState)
        titleInput.filters = titleInput.filters + InputFilter.LengthFilter(CountdownValidation.MAX_TITLE_LENGTH)

        renderTypeGrid()
        renderIconControl()
        renderTitleHint()
        renderMode()
        renderDate()
        configureRepeatSpinner()
        configureReminderSpinner()
        modeButton.setOnClickListener { showModePicker() }
        iconButton.setOnClickListener { showIconPicker() }
        dateButton.setOnClickListener { showDatePicker() }
        saveButton.setOnClickListener { save() }
        shareButton.setOnClickListener { share() }
        deleteButton.visibility = if (existingEvent == null) View.GONE else View.VISIBLE
        deleteButton.setOnClickListener { confirmDelete() }
        pendingEditorState = null
        editorInitialized = true
        consumeOperation()
        if (pendingCountUpConfirmation && !editorBusy) {
            dialogFocus.capture(modeButton)
            showCountUpConfirmation()
        }
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
            outState.putInt(STATE_TITLE_SELECTION_START, titleInput.selectionStart)
            outState.putInt(STATE_TITLE_SELECTION_END, titleInput.selectionEnd)
            outState.putString(STATE_DATE, selectedDate.toString())
            outState.putString(STATE_TYPE, selectedType.name)
            outState.putString(STATE_ICON, selectedIcon.name)
            outState.putString(STATE_REPEAT_RULE, selectedRepeatRule.name)
            outState.putString(STATE_REMINDER, selectedReminder.name)
            outState.putString(STATE_COUNT_MODE, selectedCountMode.name)
            outState.putString(STATE_PRESET, selectedPreset?.name)
            outState.putString(STATE_AUTO_SUGGESTED_TITLE, autoSuggestedTitle)
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
        listOfNotNull(modeDialog, iconDialog, dateDialog).forEach {
            it.setOnDismissListener(null)
            it.dismiss()
        }
        modeDialog = null
        iconDialog = null
        dateDialog = null
        dialogFocus.clear()
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
        modeButton.setText(EventCountText.modeLabel(selectedCountMode))
        modeButton.contentDescription = getString(R.string.count_mode_label) + ": " + modeButton.text
        EditorControlIcons.apply(modeButton, if (selectedCountMode == CountMode.COUNT_UP)
            R.drawable.editor_count_up else R.drawable.editor_count_down)
        findViewById<TextView>(R.id.dateLabel).setText(
            if (selectedCountMode == CountMode.COUNT_UP) R.string.count_up_start_date else R.string.field_date,
        )
        val visibility = if (selectedCountMode == CountMode.COUNT_UP) View.GONE else View.VISIBLE
        findViewById<View>(R.id.repeatSection).visibility = visibility
        findViewById<View>(R.id.reminderSection).visibility = visibility
    }

    private fun showModePicker() {
        if (editorBusy || modeDialog?.isShowing == true) return
        dialogFocus.capture(modeButton)
        val modes = CountMode.entries
        var choice: CountMode? = null
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_single_choice,
            modes.map { getString(EventCountText.modeLabel(it)) }) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).also {
                    EditorControlIcons.apply(it, if (modes[position] == CountMode.COUNT_UP)
                        R.drawable.editor_count_up else R.drawable.editor_count_down, false)
                }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.count_mode_label)
            .setSingleChoiceItems(adapter, modes.indexOf(selectedCountMode)) { picker, which ->
                choice = modes[which]
                picker.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        modeDialog = dialog
        dialog.setOnDismissListener {
            if (modeDialog === dialog) modeDialog = null
            if (isFinishing || isDestroyed) return@setOnDismissListener
            choice?.let(::selectCountMode)
            if (!pendingCountUpConfirmation) dialogFocus.restore(modeButton)
        }
        dialog.show()
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
            dialogFocus.restore(modeButton)
        }
        dialog.show()
        DialogPresentation.polish(dialog)
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
        renderIconControl()
        renderTitleHint()
        renderDate()
        refreshReminderSpinner(snapshot.today)
    }

    private fun configureRepeatSpinner() {
        repeatSpinner.adapter = EditorOptionAdapter(this, RepeatRule.entries.map(::repeatLabel), R.drawable.editor_repeat)
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
            reminderSpinner.adapter = EditorOptionAdapter(this, reminderOptions.map(::reminderLabel), R.drawable.editor_reminder)
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
        val glyphRes: Int, val preset: CountUpPreset? = null,
    )

    private fun presetChoices(): List<PresetChoice> = if (selectedCountMode == CountMode.COUNT_UP) {
        CountUpPreset.entries.map { preset ->
            PresetChoice("up:${preset.name}", preset.type, preset.icon,
                CountUpPresetPresentation.labelRes(preset), CountUpPresetPresentation.glyphRes(preset), preset)
        }
    } else {
        eventTypes.map { type ->
            PresetChoice("down:${type.name}", type, EventIcon.defaultFor(type), EventTypePresentation.labelRes(type),
                if (type == EventType.CUSTOM) R.drawable.editor_customize
                else EventIconPresentation.drawableRes(EventIcon.defaultFor(type)))
        }
    }

    private fun renderTypeGrid() {
        val choices = presetChoices()
        val selectedKey = if (selectedCountMode == CountMode.COUNT_UP) {
            (selectedPreset ?: CountUpPreset.forStoredType(selectedType))?.let { "up:${it.name}" }
        } else "down:${selectedType.name}"
        val notice = findViewById<TextView>(R.id.presetPreservedNotice)
        notice.visibility = if (selectedCountMode == CountMode.COUNT_UP && selectedKey == null) View.VISIBLE else View.GONE
        if (notice.visibility == View.VISIBLE) {
            notice.text = getString(R.string.preset_preserved_type, getString(EventTypePresentation.labelRes(selectedType)))
        }
        choiceLayout.render(choices.map {
            EditorChoiceLayout.Choice(it.key, getString(it.labelRes), it.glyphRes)
        }, selectedKey, !editorBusy) { key ->
            choices.firstOrNull { it.key == key }?.let(::selectPresetChoice)
        }
    }

    private fun setSuggestedTitle(value: String) {
        changingSuggestedTitle = true
        try {
            titleInput.setText(value)
            titleInput.setSelection(titleInput.text.length)
        } finally { changingSuggestedTitle = false }
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
        val currentTitle = titleInput.text.toString()
        val mayReplaceSuggestion = currentTitle.isBlank() || currentTitle == autoSuggestedTitle
        if (choice.preset?.suggestsTitle == true) {
            if (mayReplaceSuggestion) {
                val suggested = getString(choice.labelRes)
                setSuggestedTitle(suggested)
                autoSuggestedTitle = suggested
            } else {
                autoSuggestedTitle = null
            }
        } else {
            if (currentTitle == autoSuggestedTitle) setSuggestedTitle("")
            autoSuggestedTitle = null
        }
        renderTypeGrid()
        renderIconControl()
        renderTitleHint()
    }

    private fun renderIconControl() {
        customIconSection.visibility = if (selectedType == EventType.CUSTOM) View.VISIBLE else View.GONE
        if (selectedType != EventType.CUSTOM) return
        iconButton.text = getString(R.string.editor_change_icon, getString(EventIconPresentation.labelRes(selectedIcon)))
        EditorControlIcons.apply(iconButton, EventIconPresentation.drawableRes(selectedIcon))
    }

    private fun showIconPicker() {
        if (editorBusy || iconDialog?.isShowing == true || selectedType != EventType.CUSTOM) return
        dialogFocus.capture(iconButton)
        val icons = if (selectedIcon in customIcons) customIcons else listOf(selectedIcon) + customIcons
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_single_choice,
            icons.map { getString(EventIconPresentation.labelRes(it)) }) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).also {
                    EditorControlIcons.apply(it, EventIconPresentation.drawableRes(icons[position]), false)
                }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.field_icon)
            .setSingleChoiceItems(adapter, icons.indexOf(selectedIcon)) { picker, position ->
                val icon = icons[position]
                if (icon != selectedIcon) {
                    selectedIcon = icon
                    typeChosen = true
                    selectedPreset = CountUpPreset.CUSTOM
                    renderTypeGrid()
                    renderIconControl()
                    renderTitleHint()
                }
                picker.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        iconDialog = dialog
        dialog.setOnDismissListener {
            if (iconDialog === dialog) iconDialog = null
            dialogFocus.restore(iconButton)
        }
        dialog.show()
    }

    private fun renderTitleHint() {
        val preset = selectedPreset.takeIf { selectedCountMode == CountMode.COUNT_UP }
        titleInput.hint = getString(preset?.let(CountUpPresetPresentation::labelRes)
            ?: EventTypePresentation.labelRes(selectedType))
    }

    private fun showDatePicker() {
        if (editorBusy || dateDialog?.isShowing == true) return
        dialogFocus.capture(dateButton)
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
        dateDialog = dialog
        dialog.setOnDismissListener {
            if (dateDialog === dialog) dateDialog = null
            dialogFocus.restore(dateButton)
        }
        dialog.datePicker.minDate = CountdownDateDomain.MIN_DATE.atStartOfDay(pickerSnapshot.zone).toInstant().toEpochMilli()
        dialog.datePicker.maxDate = CountdownDateDomain.MAX_DATE.atStartOfDay(pickerSnapshot.zone).toInstant().toEpochMilli()
        dialog.show()
    }

    private fun renderDate() {
        val locale = resources.configuration.locales[0]
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        dateButton.text = selectedDate.format(formatter)
        val fullDate = selectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
        dateButton.contentDescription = getString(if (selectedCountMode == CountMode.COUNT_UP)
            R.string.count_up_start_date else R.string.field_date) + ": " + fullDate
        EditorControlIcons.apply(dateButton, EventIconPresentation.drawableRes(EventIcon.CALENDAR))
    }

    private fun share() {
        val title = titleInput.text.toString().trim()
        if (title.isEmpty()) {
            titleInput.error = getString(R.string.title_required)
            return
        }
        shareButton.isEnabled = false
        EventShareLauncher.share(
            activity = this, title = title, date = selectedDate, icon = selectedIcon,
            repeatRule = selectedRepeatRule, countMode = selectedCountMode,
            onFinished = { shareButton.isEnabled = true },
        )
    }

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
            .show().also { DialogPresentation.polish(it) }
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
            .show().also { DialogPresentation.polish(it, DialogPresentation.PositiveTone.DANGER) }
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
        if (state.containsKey(STATE_TITLE_SELECTION_START)) {
            titleInput.setSelection(state.getInt(STATE_TITLE_SELECTION_START).coerceIn(0, titleInput.length()),
                state.getInt(STATE_TITLE_SELECTION_END).coerceIn(0, titleInput.length()))
        }
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
        autoSuggestedTitle = state.getString(STATE_AUTO_SUGGESTED_TITLE)
            ?.takeIf { it == titleInput.text.toString() }
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
            .show().also { DialogPresentation.polish(it) }
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
            .show().also { DialogPresentation.polish(it, DialogPresentation.PositiveTone.DANGER) }
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

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_DUPLICATE_EVENT_ID = "duplicate_event_id"
        const val EXTRA_SOURCE_REVISION = "source_revision"
        private const val REQUEST_NOTIFICATIONS = 2401
        private const val STATE_TITLE = "editor_title"
        private const val STATE_TITLE_SELECTION_START = "editor_title_selection_start"
        private const val STATE_TITLE_SELECTION_END = "editor_title_selection_end"
        private const val STATE_DATE = "editor_date"
        private const val STATE_TYPE = "editor_type"
        private const val STATE_ICON = "editor_icon"
        private const val STATE_REPEAT_RULE = "editor_repeat_rule"
        private const val STATE_REMINDER = "editor_reminder"
        private const val STATE_COUNT_MODE = "editor_count_mode"
        private const val STATE_PRESET = "editor_creation_preset"
        private const val STATE_AUTO_SUGGESTED_TITLE = "editor_auto_suggested_title"
        private const val STATE_DATE_CHOSEN = "editor_date_chosen"
        private const val STATE_TYPE_CHOSEN = "editor_type_chosen"
        private const val STATE_MODE_CONFIRMATION = "editor_mode_confirmation"
    }
}