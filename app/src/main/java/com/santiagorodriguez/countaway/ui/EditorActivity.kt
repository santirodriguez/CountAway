package com.santiagorodriguez.countaway.ui

import android.Manifest
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
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.data.CountdownDataProblem
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventIcon
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.ReminderOption
import com.santiagorodriguez.countaway.notification.ArrivalNotificationPolicy
import com.santiagorodriguez.countaway.notification.ArrivalNotificationScheduler
import com.santiagorodriguez.countaway.notification.ArrivalNotificationState
import com.santiagorodriguez.countaway.widget.CountdownWidgetProvider
import com.santiagorodriguez.countaway.widget.WidgetUpdateScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

class EditorActivity : BaseActivity() {
    private val eventTypes = EventType.entries.toList()
    private val reminderOptions = ReminderOption.entries.toList()
    private lateinit var repository: CountdownRepository
    private lateinit var titleInput: EditText
    private lateinit var typeGrid: GridLayout
    private lateinit var customIconSection: View
    private lateinit var iconGrid: GridLayout
    private lateinit var dateButton: Button
    private lateinit var deleteButton: Button
    private lateinit var reminderSpinner: Spinner
    private var existingEvent: CountdownEvent? = null
    private var selectedDate: LocalDate = LocalDate.now().plusDays(1)
    private var selectedType: EventType = EventType.TRIP
    private var selectedIcon: EventIcon = EventIcon.defaultFor(EventType.TRIP)
    private var selectedReminder: ReminderOption = ReminderOption.OFF

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        InsetUtils.applySystemBarPadding(findViewById(R.id.editorRoot))

        repository = CountdownRepository(this)
        titleInput = findViewById(R.id.titleInput)
        typeGrid = findViewById(R.id.typeGrid)
        customIconSection = findViewById(R.id.customIconSection)
        iconGrid = findViewById(R.id.iconGrid)
        dateButton = findViewById(R.id.dateButton)
        deleteButton = findViewById(R.id.deleteButton)
        reminderSpinner = findViewById(R.id.reminderSpinner)

        val loadedEvents = loadEventsOrFinish() ?: return
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
            selectedReminder = event.reminder
        }
        savedInstanceState?.let(::restoreEditorState)

        renderTypeGrid()
        renderCustomIconGrid()
        renderTitleHint()
        renderDate()
        configureReminderSpinner()

        dateButton.setOnClickListener { showDatePicker() }
        findViewById<Button>(R.id.saveButton).setOnClickListener { save() }

        deleteButton.visibility = if (existingEvent == null) View.GONE else View.VISIBLE
        deleteButton.setOnClickListener { confirmDelete() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_TITLE, titleInput.text.toString())
        outState.putString(STATE_DATE, selectedDate.toString())
        outState.putString(STATE_TYPE, selectedType.name)
        outState.putString(STATE_ICON, selectedIcon.name)
        outState.putString(STATE_REMINDER, selectedReminder.name)
        super.onSaveInstanceState(outState)
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
            reminderSpinner.setSelection(reminderOptions.indexOf(ReminderOption.OFF))
            Toast.makeText(this, R.string.notification_permission_denied, Toast.LENGTH_SHORT).show()
        } else {
            warnIfNotificationsBlocked()
        }
    }

    private fun configureReminderSpinner() {
        reminderSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf(
                getString(R.string.reminder_off),
                getString(R.string.reminder_on_day),
                getString(R.string.reminder_one_day),
                getString(R.string.reminder_three_days),
                getString(R.string.reminder_seven_days),
            ),
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        reminderSpinner.setSelection(reminderOptions.indexOf(selectedReminder))
        reminderSpinner.onItemSelectedListener = SimpleItemSelectedListener { position ->
            val next = reminderOptions[position]
            selectedReminder = next
            if (next == ReminderOption.OFF) return@SimpleItemSelectedListener

            warnIfReminderScheduleImpossible()
            if (!ArrivalNotificationScheduler.hasNotificationPermission(this)) {
                requestNotificationPermission()
            } else {
                warnIfNotificationsBlocked()
            }
        }
    }

    private fun renderTypeGrid() {
        typeGrid.removeAllViews()
        eventTypes.forEach { type ->
            val button = TextView(this).apply {
                text = getString(EventTypePresentation.labelRes(type))
                contentDescription = text
                gravity = Gravity.CENTER
                textSize = 12f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(getColor(R.color.foreground))
                setPadding(dp(6), dp(9), dp(6), dp(9))
                setCompoundDrawablesWithIntrinsicBounds(0, EventTypePresentation.iconRes(type), 0, 0)
                compoundDrawablePadding = dp(6)
                compoundDrawableTintList = ColorStateList.valueOf(getColor(R.color.accent))
                setBackgroundResource(
                    if (type == selectedType) R.drawable.language_chip_active else R.drawable.control_surface,
                )
                setOnClickListener { selectType(type) }
            }
            typeGrid.addView(button, gridParams(heightDp = 78))
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
                setBackgroundResource(
                    if (icon == selectedIcon) R.drawable.language_chip_active else R.drawable.control_surface,
                )
                contentDescription = getString(EventIconPresentation.labelRes(icon))
                tooltipText = contentDescription
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(15), dp(15), dp(15), dp(15))
                setOnClickListener {
                    selectedIcon = icon
                    renderCustomIconGrid()
                }
            }
            iconGrid.addView(button, gridParams(heightDp = 56))
        }
    }

    private fun renderTitleHint() {
        titleInput.hint = getString(EventTypePresentation.labelRes(selectedType))
    }

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                renderDate()
                warnIfReminderScheduleImpossible()
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth,
        ).show()
    }

    private fun renderDate() {
        val locale = resources.configuration.locales[0]
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
        dateButton.text = selectedDate.format(formatter)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun warnIfReminderScheduleImpossible() {
        if (selectedReminder == ReminderOption.OFF || isReminderSchedulePossible()) return
        Toast.makeText(this, R.string.reminder_schedule_unavailable, Toast.LENGTH_LONG).show()
    }

    private fun isReminderSchedulePossible(): Boolean = ArrivalNotificationPolicy.isSchedulePossible(
        selectedDate,
        selectedReminder,
        LocalDate.now(),
    )

    private fun warnIfNotificationsBlocked() {
        if (
            selectedReminder == ReminderOption.OFF ||
            !isReminderSchedulePossible() ||
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
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName")),
            )
        }
    }

    private fun save() {
        val title = titleInput.text.toString().trim()
        if (title.isEmpty()) {
            titleInput.error = getString(R.string.title_required)
            return
        }
        if (!isReminderSchedulePossible()) {
            Toast.makeText(this, R.string.reminder_schedule_unavailable, Toast.LENGTH_LONG).show()
            return
        }

        val events = loadEventsOrFinish()?.toMutableList() ?: return
        val event = CountdownEvent(
            id = existingEvent?.id ?: UUID.randomUUID().toString(),
            title = title,
            date = selectedDate,
            type = selectedType,
            icon = selectedIcon,
            reminder = selectedReminder,
            createdAt = existingEvent?.createdAt ?: Instant.now(),
        )

        val existingIndex = events.indexOfFirst { it.id == event.id }
        if (existingIndex >= 0) {
            events[existingIndex] = event
        } else {
            events.add(event)
        }
        try {
            repository.save(events)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.data_save_failed, Toast.LENGTH_LONG).show()
            return
        }
        if (ArrivalNotificationPolicy.shouldResetDeliveryState(existingEvent, event)) {
            ArrivalNotificationState(this).remove(event.id)
        }
        refreshBackgroundState()
        finish()
    }

    private fun confirmDelete() {
        val event = existingEvent ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_message, event.title))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                val events = loadEventsOrFinish()?.filterNot { it.id == event.id }
                    ?: return@setPositiveButton
                try {
                    repository.save(events)
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.data_delete_failed, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                ArrivalNotificationState(this).remove(event.id)
                refreshBackgroundState()
                finish()
            }
            .show()
    }

    private fun loadEventsOrFinish(): List<CountdownEvent>? = when (val result = repository.loadResult()) {
        is CountdownLoadResult.Success -> result.events
        is CountdownLoadResult.Failure -> {
            val message = if (result.problem == CountdownDataProblem.UNSUPPORTED_SCHEMA) {
                R.string.data_newer_version_edit_blocked
            } else {
                R.string.data_error_edit_blocked
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            finish()
            null
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
        state.getString(STATE_REMINDER)?.let { raw ->
            ReminderOption.entries.firstOrNull { it.name == raw }?.let { selectedReminder = it }
        }
    }

    private fun refreshBackgroundState() {
        CountdownWidgetProvider.updateAllWidgets(this)
        WidgetUpdateScheduler.ensureScheduled(this)
        ArrivalNotificationScheduler.ensureScheduled(this)
    }

    private fun gridParams(heightDp: Int): GridLayout.LayoutParams = GridLayout.LayoutParams().apply {
        width = 0
        height = dp(heightDp)
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
        private const val STATE_REMINDER = "editor_reminder"
    }
}
