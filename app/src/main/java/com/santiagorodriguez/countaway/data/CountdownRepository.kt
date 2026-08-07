package com.santiagorodriguez.countaway.data

import android.content.Context
import android.util.AtomicFile
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate

class CountdownRepository(context: Context) {
    private val atomicFile = AtomicFile(File(context.filesDir, FILE_NAME))

    fun load(): List<CountdownEvent> {
        if (!atomicFile.baseFile.exists()) return emptyList()

        return runCatching {
            val payload = atomicFile.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(payload)
            val events = root.optJSONArray(KEY_EVENTS) ?: JSONArray()
            buildList {
                for (index in 0 until events.length()) {
                    parseEvent(events.optJSONObject(index))?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(events: List<CountdownEvent>) {
        val root = JSONObject()
            .put(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            .put(KEY_EVENTS, JSONArray().apply {
                events.forEach { put(toJson(it)) }
            })

        val stream = atomicFile.startWrite()
        try {
            stream.write(root.toString().toByteArray(Charsets.UTF_8))
            stream.flush()
            atomicFile.finishWrite(stream)
        } catch (error: Exception) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    private fun parseEvent(json: JSONObject?): CountdownEvent? = runCatching {
        requireNotNull(json)
        CountdownEvent(
            id = json.getString(KEY_ID),
            title = json.getString(KEY_TITLE),
            date = LocalDate.parse(json.getString(KEY_DATE)),
            type = EventType.valueOf(json.getString(KEY_TYPE)),
            createdAt = Instant.parse(json.getString(KEY_CREATED_AT)),
        )
    }.getOrNull()

    private fun toJson(event: CountdownEvent): JSONObject = JSONObject()
        .put(KEY_ID, event.id)
        .put(KEY_TITLE, event.title)
        .put(KEY_DATE, event.date.toString())
        .put(KEY_TYPE, event.type.name)
        .put(KEY_CREATED_AT, event.createdAt.toString())

    private companion object {
        const val FILE_NAME = "countaways.json"
        const val SCHEMA_VERSION = 1
        const val KEY_SCHEMA_VERSION = "schemaVersion"
        const val KEY_EVENTS = "events"
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_DATE = "date"
        const val KEY_TYPE = "type"
        const val KEY_CREATED_AT = "createdAt"
    }
}
