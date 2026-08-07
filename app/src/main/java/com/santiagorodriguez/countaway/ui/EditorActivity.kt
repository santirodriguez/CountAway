package com.santiagorodriguez.countaway.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

class EditorActivity : Activity() {
    private val eventTypes = EventType.values().toList()
    private lateinit var repository: CountdownRepository
    private lateinit var titleInput: EditText
    private lateinit var typeSpinner: Spinner
    private lateinit var dateButton: Button
    private lateinit var deleteButton: Button
    private var existingEvent: CountdownEvent? = null
    private var selectedDate: LocalDate = LocalDate.now().plusDays(1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        repository = CountdownRepository(this)
        titleInput = findViewById(R.id.titleInput)
        typeSpinner = findViewById(R.id.typeSpinner)
        dateButton = findViewById(R.id.dateButton)
        deleteButton = findViewById(R.id.deleteButton)

        val requestedEventId = intent.getStringExtra(EXTRA_EVENT_ID)
        existingEvent = requestedEventId?.let { id -> repository.load().firstOrNull { it.id == id } }
        if (requestedEventId != null && existingEvent == null) {
            finish()
            return
        }

        findViewById<TextView>(R.id.editorHeading).setText(
            if (existingEvent == null) R.string.editor_new_title else R.string.editor_edit_title,
        )

        val labels = eventTypes.map { getString(EventTypePresentation.labelRes(it)) }
        typeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        existingEvent?.let { event ->
            titleInput.setText(event.title)
            selectedDate = event.date
            typeSpinner.setSelection(eventTypes.indexOf(event.type))
        }

        typeSpinner.onItemSelectedListener = SimpleItemSelectedListener { position ->
            val type = eventTypes[position]
            titleInput.hint = getString(EventTypePresentation.labelRes(type))
        }

        renderDate()
        dateButton.setOnClickListener { showDatePicker() }
        findViewById<Button>(R.id.saveButton).setOnClickListener { save() }

        deleteButton.visibility = if (existingEvent == null) View.GONE else View.VISIBLE
        deleteButton.setOnClickListener { confirmDelete() }
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

    private fun save() {
        val title = titleInput.text.toString().trim()
        if (title.isEmpty()) {
            titleInput.error = getString(R.string.title_required)
            return
        }

        val selectedType = eventTypes[typeSpinner.selectedItemPosition]
        val event = CountdownEvent(
            id = existingEvent?.id ?: UUID.randomUUID().toString(),
            title = title,
            date = selectedDate,
            type = selectedType,
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
                finish()
            }
            .show()
    }

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
    }
}
