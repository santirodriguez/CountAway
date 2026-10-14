package com.santiagorodriguez.countaway.data

import com.santiagorodriguez.countaway.model.CountdownEvent
import java.security.MessageDigest

/**
 * Compact, versioned references for event identifiers sent through Android IPC.
 *
 * Stored identifiers and backup data never change. Legacy short identifiers stay
 * byte-for-byte identical. Ambiguous references deliberately resolve to nothing.
 */
object EventTransport {
    const val MAX_INLINE_ID_CHARS = 512
    private const val PREFIX = "sha256-v1:"
    private val FORMAT = Regex("""sha256-v1:[0-9a-f]{64}""")

    fun wireId(id: String): String =
        if (id.length <= MAX_INLINE_ID_CHARS) id else digest(id)

    fun isReference(value: String): Boolean = FORMAT.matches(value)

    fun resolve(events: List<CountdownEvent>, encoded: String?): CountdownEvent? {
        if (encoded.isNullOrEmpty()) return null
        return events.filter { event ->
            event.id == encoded ||
                (isReference(encoded) && event.id.length > MAX_INLINE_ID_CHARS && digest(event.id) == encoded)
        }.singleOrNull()
    }

    fun preview(text: String, limit: Int = 512): String {
        require(limit >= 2)
        return if (text.length <= limit) text else text.take(limit - 1).trimEnd() + "…"
    }

    private fun digest(id: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
        return PREFIX + hash.joinToString("") { "%02x".format(it) }
    }
}
