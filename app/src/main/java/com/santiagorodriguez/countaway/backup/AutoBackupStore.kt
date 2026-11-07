package com.santiagorodriguez.countaway.backup

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One atomic preferences document, separate from the portable event JSON. */
internal object AutoBackupStore {
    enum class Kind { DEFAULT_DOWNLOADS, SELECTED_FOLDER }

    data class Destination(
        val kind: Kind,
        val treeUri: String? = null,
        val label: String = "Downloads/CountAway",
    )

    data class OwnedCopy(
        val uri: String,
        val sha256: String,
        val createdAt: Long,
    )

    data class State(
        val enabled: Boolean = false,
        val generation: Long = 0,
        val destination: Destination? = null,
        val dirtyRevision: Long = 0,
        val savedRevision: Long = 0,
        val lastAttempt: Long = 0,
        val lastVerified: Long = 0,
        val lastError: String? = null,
        val fingerprint: String? = null,
        val failures: Int = 0,
        val owned: List<OwnedCopy> = emptyList(),
    )

    private val lock = Any()
    private const val PREFERENCES = "countaway_auto_backups"
    private const val STATE_KEY = "configuration_v1"
    private const val MAX_LEDGER = 512

    fun read(context: Context): State = synchronized(lock) { readUnsafe(context) }

    fun update(context: Context, transform: (State) -> State): State = synchronized(lock) {
        val current = readUnsafe(context)
        val replacement = transform(current)
        check(replacement.owned.size <= MAX_LEDGER) { "Backup ledger cannot grow unbounded" }
        val encoded = encode(replacement).toString()
        check(context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(STATE_KEY, encoded).commit()) { "Backup state was not saved" }
        replacement
    }

    private fun readUnsafe(context: Context): State {
        val saved = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(STATE_KEY, null) ?: return State()
        try {
            val o = JSONObject(saved)
            require(o.getInt("version") == 1) { "Unsupported backup metadata" }
            val dest = o.optJSONObject("destination")?.let {
                Destination(
                    kind = Kind.valueOf(it.getString("kind")),
                    treeUri = it.optString("treeUri").takeIf(String::isNotBlank),
                    label = it.getString("label").take(160),
                )
            }
            val copies = o.getJSONArray("owned")
            require(copies.length() <= MAX_LEDGER) { "Oversized backup ledger" }
            val owned = buildList {
                for (i in 0 until copies.length()) {
                    val entry = copies.getJSONObject(i)
                    add(OwnedCopy(entry.getString("uri"), entry.getString("sha256"),
                        entry.getLong("createdAt")))
                }
            }
            val enabled = o.getBoolean("enabled")
            require(!enabled || dest != null) { "Enabled backup has no destination" }
            return State(
                enabled = enabled,
                generation = o.getLong("generation"),
                destination = dest,
                dirtyRevision = o.getLong("dirtyRevision"),
                savedRevision = o.getLong("savedRevision"),
                lastAttempt = o.getLong("lastAttempt"),
                lastVerified = o.getLong("lastVerified"),
                lastError = o.optString("lastError").takeIf(String::isNotBlank),
                fingerprint = o.optString("fingerprint").takeIf(String::isNotBlank),
                failures = o.optInt("failures", 0).coerceIn(0, 3),
                owned = owned,
            )
        } catch (error: Exception) {
            // Never silently reset broken metadata and obtain authority to delete old files.
            throw IllegalStateException("Automatic backup metadata is unreadable", error)
        }
    }

    private fun encode(state: State) = JSONObject().apply {
        put("version", 1)
        put("enabled", state.enabled)
        put("generation", state.generation)
        put("destination", state.destination?.let { destination ->
            JSONObject().apply {
                put("kind", destination.kind.name)
                put("treeUri", destination.treeUri ?: "")
                put("label", destination.label)
            }
        })
        put("dirtyRevision", state.dirtyRevision)
        put("savedRevision", state.savedRevision)
        put("lastAttempt", state.lastAttempt)
        put("lastVerified", state.lastVerified)
        put("lastError", state.lastError ?: "")
        put("fingerprint", state.fingerprint ?: "")
        put("failures", state.failures)
        put("owned", JSONArray().apply {
            state.owned.forEach { entry ->
                put(JSONObject().apply {
                    put("uri", entry.uri)
                    put("sha256", entry.sha256)
                    put("createdAt", entry.createdAt)
                })
            }
        })
    }
}
