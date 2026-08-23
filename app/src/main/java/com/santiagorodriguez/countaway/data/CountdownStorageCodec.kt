package com.santiagorodriguez.countaway.data

import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventIcon
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.ReminderOption
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate

enum class CountdownDataProblem {
    CORRUPT,
    UNSUPPORTED_SCHEMA,
}

class CountdownDataException(
    val problem: CountdownDataProblem,
    cause: Throwable? = null,
) : Exception(cause)

object CountdownStorageSchema {
    const val LEGACY_VERSION = 1
    const val PREVIOUS_VERSION = 2
    const val NOTIFICATION_VERSION = 3
    const val CURRENT_VERSION = 4

    fun isSupported(version: Int): Boolean = version in LEGACY_VERSION..CURRENT_VERSION

    fun problemFor(version: Int): CountdownDataProblem? = when {
        isSupported(version) -> null
        version > CURRENT_VERSION -> CountdownDataProblem.UNSUPPORTED_SCHEMA
        else -> CountdownDataProblem.CORRUPT
    }
}

object CountdownStorageCodec {
    fun readUtf8Payload(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(READ_BUFFER_BYTES)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_PAYLOAD_BYTES) {
                throw CountdownDataException(CountdownDataProblem.CORRUPT)
            }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    fun decode(payload: String): List<CountdownEvent> {
        if (payload.toByteArray(Charsets.UTF_8).size > MAX_PAYLOAD_BYTES) {
            throw CountdownDataException(CountdownDataProblem.CORRUPT)
        }

        try {
            val root = JSONObject(payload)
            if (!root.has(KEY_SCHEMA_VERSION) || !root.has(KEY_EVENTS)) {
                throw CountdownDataException(CountdownDataProblem.CORRUPT)
            }

            val schemaVersion = root.getInt(KEY_SCHEMA_VERSION)
            CountdownStorageSchema.problemFor(schemaVersion)?.let { problem ->
                throw CountdownDataException(problem)
            }

            val events = root.getJSONArray(KEY_EVENTS)
            if (events.length() > MAX_EVENTS) {
                throw CountdownDataException(CountdownDataProblem.CORRUPT)
            }

            val parsed = buildList {
                for (index in 0 until events.length()) {
                    add(parseEvent(events.getJSONObject(index), schemaVersion))
                }
            }
            validateEvents(parsed)
            return parsed
        } catch (error: CountdownDataException) {
            throw error
        } catch (error: Exception) {
            throw CountdownDataException(CountdownDataProblem.CORRUPT, error)
        }
    }

    fun encode(events: List<CountdownEvent>): String {
        validateEvents(events)
        return JSONObject()
            .put(KEY_SCHEMA_VERSION, CountdownStorageSchema.CURRENT_VERSION)
            .put(KEY_EVENTS, JSONArray().apply {
                events.forEach { put(toJson(it)) }
            })
            .toString()
    }

    internal fun validateEvents(events: List<CountdownEvent>) {
        if (events.size > MAX_EVENTS) throw CountdownDataException(CountdownDataProblem.CORRUPT)
        val ids = HashSet<String>(events.size)
        events.forEach { event ->
            if (event.id.isBlank() || event.title.isBlank() || !ids.add(event.id)) {
                throw CountdownDataException(CountdownDataProblem.CORRUPT)
            }
        }
    }

    private fun parseEvent(json: JSONObject, schemaVersion: Int): CountdownEvent {
        val rawType = json.getString(KEY_TYPE)
        val type = when (schemaVersion) {
            CountdownStorageSchema.LEGACY_VERSION -> EventType.fromLegacyName(rawType)
            CountdownStorageSchema.PREVIOUS_VERSION,
            CountdownStorageSchema.NOTIFICATION_VERSION,
            CountdownStorageSchema.CURRENT_VERSION,
            -> EventType.fromStorageKey(rawType)
            else -> null
        } ?: throw CountdownDataException(CountdownDataProblem.CORRUPT)

        val icon = when (schemaVersion) {
            CountdownStorageSchema.LEGACY_VERSION,
            CountdownStorageSchema.PREVIOUS_VERSION,
            -> EventIcon.defaultFor(type)
            CountdownStorageSchema.NOTIFICATION_VERSION ->
                EventIcon.fromStorageKey(json.optString(KEY_ICON)) ?: EventIcon.defaultFor(type)
            CountdownStorageSchema.CURRENT_VERSION ->
                EventIcon.fromStorageKey(json.getString(KEY_ICON))
                    ?: throw CountdownDataException(CountdownDataProblem.CORRUPT)
            else -> throw CountdownDataException(CountdownDataProblem.CORRUPT)
        }

        val reminder = when (schemaVersion) {
            CountdownStorageSchema.LEGACY_VERSION,
            CountdownStorageSchema.PREVIOUS_VERSION,
            -> ReminderOption.OFF
            CountdownStorageSchema.NOTIFICATION_VERSION ->
                if (json.optBoolean(KEY_NOTIFY_ON_ARRIVAL, false)) {
                    ReminderOption.ON_DAY
                } else {
                    ReminderOption.OFF
                }
            CountdownStorageSchema.CURRENT_VERSION ->
                ReminderOption.fromStorageKey(json.getString(KEY_REMINDER))
                    ?: throw CountdownDataException(CountdownDataProblem.CORRUPT)
            else -> throw CountdownDataException(CountdownDataProblem.CORRUPT)
        }

        return CountdownEvent(
            id = json.getString(KEY_ID),
            title = json.getString(KEY_TITLE),
            date = LocalDate.parse(json.getString(KEY_DATE)),
            type = type,
            icon = icon,
            reminder = reminder,
            createdAt = Instant.parse(json.getString(KEY_CREATED_AT)),
        )
    }

    private fun toJson(event: CountdownEvent): JSONObject = JSONObject()
        .put(KEY_ID, event.id)
        .put(KEY_TITLE, event.title)
        .put(KEY_DATE, event.date.toString())
        .put(KEY_TYPE, event.type.storageKey)
        .put(KEY_ICON, event.icon.storageKey)
        .put(KEY_REMINDER, event.reminder.storageKey)
        .put(KEY_CREATED_AT, event.createdAt.toString())

    private const val KEY_SCHEMA_VERSION = "schemaVersion"
    private const val KEY_EVENTS = "events"
    private const val KEY_ID = "id"
    private const val KEY_TITLE = "title"
    private const val KEY_DATE = "date"
    private const val KEY_TYPE = "type"
    private const val KEY_ICON = "iconKey"
    private const val KEY_REMINDER = "reminderKey"
    private const val KEY_NOTIFY_ON_ARRIVAL = "notifyOnArrival"
    private const val KEY_CREATED_AT = "createdAt"
    private const val MAX_EVENTS = 10_000
    private const val MAX_PAYLOAD_BYTES = 5 * 1024 * 1024
    private const val READ_BUFFER_BYTES = 16 * 1024
}
