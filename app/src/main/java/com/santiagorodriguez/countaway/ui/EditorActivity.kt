package com.santiagorodriguez.countaway.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.NotificationManager
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
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownCalculator
import com.santiagorodriguez.countaway.countdown.CountdownDateDomain
import com.santiagorodriguez.countaway.countdown.CountdownOccurrenceResolver
import com.santiagorodriguez.countaway.countdown.CountdownStatus
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.countdown.CountdownTimeSnapshot
import com.santiagorodriguez.countaway.data.CountdownDataProblem
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownMutationResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.data.CountdownValidation
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventIcon
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.ReminderOption
import com.santiagorodriguez.countaway.model.RepeatRule
import com.santiagorodriguez.countaway.notification.ArrivalNotificationPolicy
import com.santiagorodriguez.countaway.notification.ArrivalNotificationScheduler
import com.santiagorodriguez.countaway.notification.ArrivalNotificationState
import com.santiagorodriguez.countaway.notification.ArrivalNotifier
import com.santiagorodriguez.countaway.widget.CountdownWidgetProvider
import com.santiagorodriguez.countaway.widget.WidgetUpdateScheduler
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

class EditorActivity : BaseActivity() {
    private val eventTypes = EventType.entries.toList()
    private var reminderOptions: List<ReminderOption> = emptyList()
    private lateinit var repository: CountdownRepository
    private lateinit var editorRoot: View
    private lateinit var titleInput: EditText
    private lateinit var typeGrid: GridLayout
    private lateinit var customIconSection: View
    private lateinit var iconGrid: GridLayout
    private lateinit var dateButton: Button
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
    private var suppressRepeatSelection = false
    private var suppressReminderSelection = false
    private var editorInitialized = false
    private var editorBusy = true
    private var baselineDraft: EditorDraft? = null
    private var backCallback: OnBackInvokedCallback? = null
    private var loadGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        editorRoot = findViewById(R.id.editorRoot)
        InsetUtils.applySystemBarPadding(editorRoot)

        repository = CountdownRepository(this)
        titleInput = findViewById(R.id.titleInput)
        typeGrid = findViewById(R.id.typeGrid)
        customIconSection = findViewById(R.id.customIconSection)
        iconGrid = findViewById(R.id.iconGrid)
        dateButton = findViewById(R.id.dateButton)
        saveButton = findViewById(R.id.saveButton)
        shareButton = findViewById(R.id.shareButton)
        deleteButton = findViewById(R.id.deleteButton)
        repeatSpinner = findViewById(R.id.repeatSpinner)
        reminderSpinner = findViewById(R.id.reminderSpinner)
        scheduleSummary = findViewById(R.id.editorScheduleSummary)
        registerBackCallback()
        temporalInvalidationController = TemporalInvalidationController(this) { snapshot ->
            if (editorInitialized) {
                refreshReminderSpinner(snapshot.today)
            }
        }

        setEditorBusy(true)
        loadEditorData(savedInstanceState)
    }

    private fun loadEditorData(savedInstanceState: Bundle?) {
        val generation = ++loadGeneration
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

    private fun initializeEditor(
        loadedEvents: List<CountdownEvent>,
        savedInstanceState: Bundle?,
    ) {
        val requestedEventId = intent.getStringExtra(EXTRA_EVENT_ID)
        existingEvent = requestedEventId?.let { id -> loadedEvents.firstOrNull { it.id == id } }
        if (requestedEventId != null && existingEvent == null) {
            finish()
            return
        }

        findViewById<TextView>(R.id.editorHeading).setText(
            if (existingEvent == null) R.string.editor_new_title else R.string.editor_edit_title,
        )

        existingEvent?.let { event ->
            titleInput.setText(event.title)
            selectedDate = event.date
            selectedType = event.type
            selectedIcon = event.icon
            selectedRepeatRule = event.repeatRule
            selectedReminder = event.reminder
        }
        baselineDraft = currentDraft()
        savedInstanceState?.let(::restoreEditorState)
        titleInput.filters = titleInput.filters + InputFilter.LengthFilter(CountdownValidation.MAX_TITLE_LENGTH)

        renderTypeGrid()
        renderCustomIconGrid()
        renderTitleHint()
        renderDate()
        configureRepeatSpinner()
        configureReminderSpinner()

        dateButton.setOnClickListener { showDatePicker() }
        saveButton.setOnClickListener { save() }
        shareButton.setOnClickListener { share() }

        deleteButton.visibility = if (existingEvent == null) View.GONE else View.VISIBLE
        deleteButton.setOnClickListener { confirmDelete() }
        editorInitialized = true
        setEditorBusy(false)
    }

    override fun onResume() {
        super.onResume()
        val snapshot = CountdownTime.snapshot()
        temporalInvalidationController.start(snapshot)
        if (editorInitialized) {
            refreshReminderSpinner(snapshot.today)
        }
    }

    override fun onPause() {
        temporalInvalidationController.stop()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (editorInitialized) {
            outState.putString(STATE_TITLE, titleInput.text.toString())
            outState.putString(STATE_DATE, selectedDate.toString())
            outState.putString(STATE_TYPE, selectedType.name)
            outState.putString(STATE_ICON, selectedIcon.name)
            outState.putString(STATE_REPEAT_RULE, selectedRepeatRule.name)
            outState.putString(STATE_REMINDER, selectedReminder.name)
        }
        super.onSaveInstanceState(outState)
    }

    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            super.onBackPressed()
        } else {
            handleBackRequest()
        }
    }

    override fun onDestroy() {
        temporalInvalidationController.stop()
        unregisterBackCallback()
        loadGeneration += 1
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_NOTIFICATIONS) return

        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            selectedReminder = ReminderOption.OFF
            refreshReminderSpinner(CountdownTime.snapshot().today)
            Toast.makeText(this, R.string.notification_permission_denied, Toast.LENGTH_SHORT).show()
        } else {
            warnIfNotificationsBlocked(CountdownTime.snapshot().today)
        }
    }

    private fun configureRepeatSpinner() {
        repeatSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            RepeatRule.entries.map(::repeatLabel),
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        suppressRepeatSelection = true
        repeatSpinner.setSelection(RepeatRule.entries.indexOf(selectedRepeatRule))
        suppressRepeatSelection = false
        repeatSpinner.onItemSelectedListener = SimpleItemSelectedListener { position ->
            if (suppressRepeatSelection) return@SimpleItemSelectedListener
            val next = RepeatRule.entries.getOrNull(position) ?: return@SimpleItemSelectedListener
            if (next == selectedRepeatRule) return@SimpleItemSelectedListener
            selectedRepeatRule = next
            val snapshot = CountdownTime.snapshot()
            refreshReminderSpinner(snapshot.today)
            handleReminderSelectionEffect(
                ReminderEditorPolicy.repeatChangeEffect(
                    existingEvent = existingEvent,
                    selectedDate = selectedDate,
                    selectedReminder = selectedReminder,
                    selectedRepeatRule = selectedRepeatRule,
                    today = snapshot.today,
                ),
                snapshot.today,
            )
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
            if (!suppressReminderSelection) {
                val next = reminderOptions.getOrNull(position) ?: return@SimpleItemSelectedListener
                val snapshot = CountdownTime.snapshot()
                val effect = ReminderEditorPolicy.selectionEffect(
                    currentReminder = selectedReminder,
                    nextReminder = next,
                    existingEvent = existingEvent,
                    selectedDate = selectedDate,
                    today = snapshot.today,
                    selectedRepeatRule = selectedRepeatRule,
                )
                selectedReminder = next
                refreshReminderSpinner(snapshot.today)
                handleReminderSelectionEffect(effect, snapshot.today)
            }
        }
    }

    private fun refreshReminderSpinner(today: LocalDate) {
        val nextOptions = ReminderEditorPolicy.availableOptions(
            existingEvent = existingEvent,
            selectedDate = selectedDate,
            selectedReminder = selectedReminder,
            today = today,
            selectedRepeatRule = selectedRepeatRule,
        )
        val optionsChanged = nextOptions != reminderOptions
        reminderOptions = nextOptions

        suppressReminderSelection = true
        if (optionsChanged || reminderSpinner.adapter == null) {
            reminderSpinner.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                reminderOptions.map(::reminderLabel),
            ).apply {
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

    private fun renderTypeGrid() {
        typeGrid.removeAllViews()
        eventTypes.forEach { type ->
            val button = TextView(this).apply {
                text = getString(EventTypePresentation.labelRes(type))
                contentDescription = text
                isSelected = type == selectedType
                gravity = Gravity.CENTER
                textSize = 12f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(getColor(R.color.foreground))
                minHeight = dp(78)
                setPadding(dp(6), dp(9), dp(6), dp(9))
                setCompoundDrawablesWithIntrinsicBounds(0, EventTypePresentation.iconRes(type), 0, 0)
                compoundDrawablePadding = dp(6)
                compoundDrawableTintList = ColorStateList.valueOf(getColor(R.color.accent))
                setBackgroundResource(
                    if (isSelected) R.drawable.language_chip_active else R.drawable.control_surface,
                )
                setOnClickListener { selectType(type) }
            }
            typeGrid.addView(button, gridParams())
        }
    }

    private fun selectType(type: EventType) {
        selectedType = type
        selectedIcon = if (type == EventType.CUSTOM) {
            selectedIcon.takeIf { it in EventIcon.customChoices } ?: EventIcon.STAR
        } else {
            EventIcon.defaultFor(type)
        }
        renderTypeGrid()
        renderCustomIconGrid()
        renderTitleHint()
    }

    private fun renderCustomIconGrid() {
        customIconSection.visibility = if (selectedType == EventType.CUSTOM) View.VISIBLE else View.GONE
        iconGrid.removeAllViews()
        if (selectedType != EventType.CUSTOM) return

        EventIcon.customChoices.forEach { icon ->
            val button = ImageButton(this).apply {
                setImageResource(EventIconPresentation.drawableRes(icon))
                imageTintList = ColorStateList.valueOf(getColor(R.color.accent))
                backgroundTintList = null
                isSelected = icon == selectedIcon
                setBackgroundResource(
                    if (isSelected) R.drawable.language_chip_active else R.drawable.control_surface,
                )
                contentDescription = getString(EventIconPresentation.labelRes(icon))
                tooltipText = contentDescription
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                minimumHeight = dp(56)
                setPadding(dp(15), dp(15), dp(15), dp(15))
                setOnClickListener {
                    selectedIcon = icon
                    renderCustomIconGrid()
                }
            }
            iconGrid.addView(button, gridParams())
        }
    }

    private fun renderTitleHint() {
        titleInput.hint = getString(EventTypePresentation.labelRes(selectedType))
    }

    private fun showDatePicker() {
        val pickerSnapshot = CountdownTime.snapshot()
        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val previousDate = selectedDate
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                renderDate()
                if (selectedDate != previousDate) {
                    val snapshot = CountdownTime.snapshot()
                    refreshReminderSpinner(snapshot.today)
                    handleReminderSelectionEffect(
                        ReminderEditorPolicy.dateChangeEffect(
                            existingEvent = existingEvent,
                            selectedDate = selectedDate,
                            selectedReminder = selectedReminder,
                            today = snapshot.today,
                            selectedRepeatRule = selectedRepeatRule,
                        ),
                        snapshot.today,
                    )
                }
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth,
        )
        dialog.datePicker.minDate =
            CountdownDateDomain.MIN_DATE.atStartOfDay(pickerSnapshot.zone).toInstant().toEpochMilli()
        dialog.datePicker.maxDate =
            CountdownDateDomain.MAX_DATE.atStartOfDay(pickerSnapshot.zone).toInstant().toEpochMilli()
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
        val displayDate = CountdownOccurrenceResolver.displayDate(
            selectedDate,
            selectedRepeatRule,
            snapshot.today,
        )
        val countdown = CountdownCalculator.value(snapshot.today, displayDate)
        val status = when (countdown.status) {
            CountdownStatus.FUTURE,
            CountdownStatus.THREE_DAYS,
            CountdownStatus.TWO_DAYS,
            -> resources.getQuantityString(
                R.plurals.share_days_left,
                countdown.days.toInt(),
                countdown.days,
            )
            CountdownStatus.TOMORROW -> getString(R.string.share_tomorrow)
            CountdownStatus.TODAY -> getString(R.string.share_today)
            CountdownStatus.DONE -> elapsedStatus(countdown.elapsedDays)
        }
        val locale = resources.configuration.locales[0]
        val date = displayDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
        val text = getString(R.string.share_countdown_format, title, status, date)
        val sendIntent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        runCatching {
            startActivity(Intent.createChooser(sendIntent, null))
        }.onFailure {
            Toast.makeText(this, R.string.share_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun elapsedStatus(elapsedDays: Long): String {
        val quantity = elapsedDays.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        return resources.getQuantityString(R.plurals.status_days_ago, quantity, elapsedDays)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun handleReminderSelectionEffect(
        effect: ReminderSelectionEffect,
        today: LocalDate,
    ) {
        when (effect) {
            ReminderSelectionEffect.NONE -> Unit
            ReminderSelectionEffect.SHOW_SCHEDULE_UNAVAILABLE ->
                Toast.makeText(this, R.string.reminder_schedule_unavailable, Toast.LENGTH_LONG).show()
            ReminderSelectionEffect.CHECK_NOTIFICATIONS -> {
                if (!ArrivalNotificationScheduler.hasNotificationPermission(this)) {
                    requestNotificationPermission()
                } else {
                    warnIfNotificationsBlocked(today)
                }
            }
        }
    }

    private fun canSaveSelectedReminder(today: LocalDate): Boolean = ReminderEditorPolicy.canSave(
        existingEvent = existingEvent,
        selectedDate = selectedDate,
        selectedReminder = selectedReminder,
        today = today,
        selectedRepeatRule = selectedRepeatRule,
    )

    private fun warnIfNotificationsBlocked(today: LocalDate) {
        if (
            selectedReminder == ReminderOption.OFF ||
            !ArrivalNotificationPolicy.isSchedulePossible(
                selectedDate,
                selectedReminder,
                selectedRepeatRule,
                today,
            ) ||
            !ArrivalNotificationScheduler.hasNotificationPermission(this) ||
            ArrivalNotificationScheduler.canPostNotifications(this)
        ) {
            return
        }

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
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }

        runCatching { startActivity(intent) }.onFailure {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:$packageName")),
                )
            }.onFailure {
                Toast.makeText(this, R.string.external_action_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun save() {
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
            id = existingEvent?.id ?: UUID.randomUUID().toString(),
            title = title,
            date = selectedDate,
            type = selectedType,
            icon = selectedIcon,
            reminder = selectedReminder,
            createdAt = existingEvent?.createdAt ?: snapshot.now.toInstant(),
            repeatRule = selectedRepeatRule,
        )
        val expectedEvent = existingEvent
        setEditorBusy(true)

        CountdownIo.submit(
            task = { repository.saveEvent(expectedEvent, event) },
            onComplete = { result ->
                if (isFinishing || isDestroyed) return@submit
                setEditorBusy(false)

                val mutation = result.getOrElse {
                    Toast.makeText(this, R.string.data_save_failed, Toast.LENGTH_LONG).show()
                    return@submit
                }
                when (mutation) {
                    CountdownMutationResult.APPLIED -> {
                        if (ArrivalNotificationPolicy.shouldResetDeliveryState(expectedEvent, event)) {
                            ArrivalNotificationState(this).remove(event.id)
                        }
                        ArrivalNotifier.cancelEvent(this, event.id)
                        refreshBackgroundStateInBackground()
                        finish()
                    }
                    CountdownMutationResult.CONFLICT -> {
                        if (expectedEvent == null) {
                            Toast.makeText(this, R.string.data_save_failed, Toast.LENGTH_LONG).show()
                        } else {
                            showDataConflict()
                        }
                    }
                }
            },
        )
    }

    private fun confirmDelete() {
        val event = existingEvent ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_message, event.title))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                setEditorBusy(true)
                CountdownIo.submit(
                    task = { repository.deleteEvent(event) },
                    onComplete = { result ->
                        if (isFinishing || isDestroyed) return@submit
                        setEditorBusy(false)

                        val mutation = result.getOrElse {
                            Toast.makeText(this, R.string.data_delete_failed, Toast.LENGTH_LONG).show()
                            return@submit
                        }
                        when (mutation) {
                            CountdownMutationResult.APPLIED -> {
                                ArrivalNotificationState(this).remove(event.id)
                                ArrivalNotifier.cancelEvent(this, event.id)
                                refreshBackgroundStateInBackground()
                                finish()
                            }
                            CountdownMutationResult.CONFLICT -> showDataConflict()
                        }
                    },
                )
            }
            .show()
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
    }

    private fun showDataConflict() {
        AlertDialog.Builder(this)
            .setTitle(R.string.data_conflict_title)
            .setMessage(R.string.data_conflict_message)
            .setNegativeButton(R.string.data_conflict_keep_editing, null)
            .setPositiveButton(R.string.data_conflict_reload) { _, _ -> recreate() }
            .show()
    }

    private fun setEditorBusy(busy: Boolean) {
        editorBusy = busy
        setViewTreeEnabled(editorRoot, !busy)
    }

    private fun setViewTreeEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                setViewTreeEnabled(view.getChildAt(index), enabled)
            }
        }
    }

    private fun refreshBackgroundStateInBackground() {
        val context = applicationContext
        CountdownIo.execute {
            val snapshot = CountdownTime.snapshot()
            runCatching { CountdownWidgetProvider.updateAllWidgets(context, snapshot) }
            runCatching { WidgetUpdateScheduler.ensureScheduled(context, snapshot) }
            runCatching { ArrivalNotificationScheduler.ensureScheduled(context, snapshot) }
        }
    }

    private fun renderScheduleSummary(today: LocalDate) {
        val lines = mutableListOf<String>()
        val locale = resources.configuration.locales[0]
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

        if (selectedRepeatRule != RepeatRule.NONE) {
            val occurrence = CountdownOccurrenceResolver.displayDate(
                selectedDate,
                selectedRepeatRule,
                today,
            )
            lines += getString(
                R.string.schedule_next_occurrence,
                occurrence.format(formatter),
                repeatLabel(selectedRepeatRule).lowercase(locale),
            )
        }

        ArrivalNotificationPolicy.scheduledDate(
            selectedDate,
            selectedRepeatRule,
            selectedReminder,
            today,
        )?.let { reminderDate ->
            lines += getString(
                R.string.schedule_next_reminder,
                reminderDate.format(formatter),
            )
        }

        if (
            selectedRepeatRule == RepeatRule.MONTHLY &&
            selectedDate.dayOfMonth >= 29
        ) {
            lines += getString(R.string.schedule_month_end_note)
        }

        if (
            selectedRepeatRule == RepeatRule.YEARLY &&
            selectedDate.monthValue == 2 &&
            selectedDate.dayOfMonth == 29
        ) {
            lines += getString(R.string.schedule_leap_day_note)
        }

        scheduleSummary.text = lines.joinToString("\n")
        scheduleSummary.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun registerBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || backCallback != null) return
        val callback = OnBackInvokedCallback { handleBackRequest() }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            callback,
        )
        backCallback = callback
    }

    private fun unregisterBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        backCallback?.let(onBackInvokedDispatcher::unregisterOnBackInvokedCallback)
        backCallback = null
    }

    private fun handleBackRequest() {
        if (editorBusy) return
        if (!editorInitialized || baselineDraft == currentDraft()) {
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
        title = titleInput.text.toString(),
        date = selectedDate,
        type = selectedType,
        icon = selectedIcon,
        repeatRule = selectedRepeatRule,
        reminder = selectedReminder,
    )

    private data class EditorDraft(
        val title: String,
        val date: LocalDate,
        val type: EventType,
        val icon: EventIcon,
        val repeatRule: RepeatRule,
        val reminder: ReminderOption,
    )

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
    }
}
