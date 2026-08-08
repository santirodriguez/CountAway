package com.santiagorodriguez.countaway.ui

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventIcon
import com.santiagorodriguez.countaway.model.EventType
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
    private lateinit var repository: CountdownRepository
    private lateinit var titleInput: EditText
    private lateinit var typeGrid: GridLayout
    private lateinit var customIconSection: View
    private lateinit var iconGrid: GridLayout
    private lateinit var dateButton: Button
    private lateinit var deleteButton: Button
    private lateinit var notifySwitch: Switch
    private var existingEvent: CountdownEvent? = null
    private var selectedDate: LocalDate = LocalDate.now().plusDays(1)
    private var selectedType: EventType = EventType.TRIP
    private var selectedIcon: EventIcon = EventIcon.defaultFor(EventType.TRIP)

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
        notifySwitch = findViewById(R.id.notifySwitch)

        val requestedEventId = intent.getStringExtra(EXTRA_EVENT_ID)
        existingEvent = requestedEventId?.let { id -> repository.load().firstOrNull { it.id == id } }
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
            notifySwitch.isChecked = event.notifyOnArrival
        }

        savedInstanceState?.let { state ->
            state.getString(STATE_SELECTED_DATE)?.let { rawDate ->
                runCatching { LocalDate.parse(rawDate) }.getOrNull()?.let { selectedDate = it }
            }
            selectedType = state.getString(STATE_SELECTED_TYPE)
                ?.let(EventType::fromStorageKey)
                ?: selectedType
            selectedIcon = state.getString(STATE_SELECTED_ICON)
                ?.let(EventIcon::fromStorageKey)
                ?: EventIcon.defaultFor(selectedType)
            notifySwitch.isChecked = state.getBoolean(STATE_NOTIFY_ON_ARRIVAL, notifySwitch.isChecked)
        }

        renderTypeGrid()
        renderCustomIconGrid()
        renderTitleHint()
        renderDate()

        dateButton.setOnClickListener { showDatePicker() }
        findViewById<Button>(R.id.saveButton).setOnClickListener { save() }
        notifySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !ArrivalNotificationScheduler.canPostNotifications(this)) {
                requestNotificationPermission()
            }
        }

        deleteButton.visibility = if (existingEvent == null) View.GONE else View.VISIBLE
        deleteButton.setOnClickListener { confirmDelete() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SELECTED_DATE, selectedDate.toString())
        outState.putString(STATE_SELECTED_TYPE, selectedType.storageKey)
        outState.putString(STATE_SELECTED_ICON, selectedIcon.storageKey)
        outState.putBoolean(STATE_NOTIFY_ON_ARRIVAL, notifySwitch.isChecked)
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
            notifySwitch.isChecked = false
            Toast.makeText(this, R.string.notification_permission_denied, Toast.LENGTH_SHORT).show()
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

    private fun save() {
        val title = titleInput.text.toString().trim()
        if (title.isEmpty()) {
            titleInput.error = getString(R.string.title_required)
            return
        }

        val event = CountdownEvent(
            id = existingEvent?.id ?: UUID.randomUUID().toString(),
            title = title,
            date = selectedDate,
            type = selectedType,
            icon = selectedIcon,
            notifyOnArrival = notifySwitch.isChecked,
            createdAt = existingEvent?.createdAt ?: Instant.now(),
        )

        val events = repository.load().toMutableList()
        val existingIndex = events.indexOfFirst { it.id == event.id }
        if (existingIndex >= 0) {
            events[existingIndex] = event
        } else {
            events.add(event)
        }
        repository.save(events)
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
                val events = repository.load().filterNot { it.id == event.id }
                repository.save(events)
                ArrivalNotificationState(this).remove(event.id)
                refreshBackgroundState()
                finish()
            }
            .show()
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
        private const val STATE_SELECTED_DATE = "selected_date"
        private const val STATE_SELECTED_TYPE = "selected_type"
        private const val STATE_SELECTED_ICON = "selected_icon"
        private const val STATE_NOTIFY_ON_ARRIVAL = "notify_on_arrival"
    }
}
