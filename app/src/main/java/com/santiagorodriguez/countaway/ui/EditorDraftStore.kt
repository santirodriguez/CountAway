package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Lossless, app-private saved-state extension for unusually large editor drafts.
 * The Bundle only holds a UUID. No event IDs or user text enter a Binder parcel.
 */
internal object EditorDraftStore {
    const val INLINE_CHAR_LIMIT = 16_384
    private const val MAX_SNAPSHOT_BYTES = 16L * 1024L * 1024L

    fun save(context: Context, existingToken: String?, text: String): String {
        val token = existingToken?.takeIf(::validToken) ?: UUID.randomUUID().toString()
        val directory = directory(context)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create editor draft directory")
        val file = AtomicFile(File(directory, "$token.txt"))
        val stream = file.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
        return token
    }

    fun read(context: Context, token: String?): String? {
        if (!validToken(token)) return null
        val file = File(directory(context), "$token.txt")
        if (!file.isFile || file.length() > MAX_SNAPSHOT_BYTES) return null
        return runCatching {
            AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
    }

    fun delete(context: Context, token: String?) {
        if (validToken(token)) AtomicFile(File(directory(context), "$token.txt")).delete()
    }

    private fun directory(context: Context): File = File(context.noBackupFilesDir, "editor-drafts")

    private fun validToken(raw: String?): Boolean =
        raw != null && runCatching { UUID.fromString(raw).toString() == raw }.getOrDefault(false)
}
