package com.santiagorodriguez.countaway.backup

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/** Disposable DocumentsUI fixture. Included only in debug APKs, never release builds. */
class BackupFaultDocumentsProvider : DocumentsProvider() {
    private val app get() = requireNotNull(context)
    private val base get() = File(app.filesDir, "ci-fault-saf").apply { mkdirs() }
    private val settings get() = app.getSharedPreferences("ci_fault_saf", Context.MODE_PRIVATE)

    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection?.toList()?.toTypedArray() ?: ROOT_COLUMNS).apply {
            newRow().apply {
                add(DocumentsContract.Root.COLUMN_ROOT_ID, "root")
                add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, "root")
                add(DocumentsContract.Root.COLUMN_TITLE, "CountAway Test Storage")
                add(DocumentsContract.Root.COLUMN_SUMMARY, "Disposable backup failures")
                add(DocumentsContract.Root.COLUMN_ICON, android.R.drawable.ic_menu_save)
                add(DocumentsContract.Root.COLUMN_FLAGS,
                    DocumentsContract.Root.FLAG_LOCAL_ONLY or
                        DocumentsContract.Root.FLAG_SUPPORTS_CREATE)
                add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
            }
        }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection?.toList()?.toTypedArray() ?: DOCUMENT_COLUMNS).apply {
            addDocument(this, documentId)
        }

    override fun queryChildDocuments(
        parentDocumentId: String, projection: Array<out String>?, sortOrder: String?,
    ): Cursor = MatrixCursor(projection?.toList()?.toTypedArray() ?: DOCUMENT_COLUMNS).apply {
        when (parentDocumentId) {
            "root" -> addDocument(this, "folder")
            "folder" -> base.listFiles().orEmpty().filter {
                it.isFile && it.name.startsWith("f-")
            }.forEach { addDocument(this, it.name) }
            else -> throw FileNotFoundException(parentDocumentId)
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String) =
        (parentDocumentId == "root" && documentId == "folder") ||
            (parentDocumentId == "folder" && isFileId(documentId))

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        if (parentDocumentId != "folder" || mimeType != "application/json") {
            throw FileNotFoundException("Invalid fixture destination")
        }
        if (settings.getString("mode", "NORMAL") == "UNAVAILABLE") {
            throw FileNotFoundException("Provider temporarily unavailable")
        }
        val name = displayName.take(90).replace(Regex("[^A-Za-z0-9._-]"), "_")
        val id = "f-" + UUID.randomUUID().toString() + "__" + name
        if (!backing(id).createNewFile()) throw FileNotFoundException("Cannot create fixture")
        return id
    }

    override fun openDocument(
        documentId: String, mode: String, signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val file = backing(documentId)
        if (!file.isFile) throw FileNotFoundException(documentId)
        val fault = settings.getString("mode", "NORMAL")
        if (mode.contains("w")) {
            when (fault) {
                "NO_SPACE" -> throw FileNotFoundException("ENOSPC: fixture quota exhausted")
                "ABORT" -> {
                    file.writeText("{incomplete", Charsets.UTF_8)
                    throw FileNotFoundException("Provider aborted after partial output")
                }
                "SLOW" -> {
                    settings.edit().putBoolean("entered_slow_write", true).commit()
                    Thread.sleep(1800L)
                }
            }
        } else if (fault == "TRUNCATED" &&
            documentId != settings.getString("protected_document", null)) {
            val truncated = File(app.cacheDir, "ci-saf-truncated-read").apply {
                writeText("{partial", Charsets.UTF_8)
            }
            return ParcelFileDescriptor.open(truncated, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun deleteDocument(documentId: String) {
        if (!backing(documentId).delete()) throw FileNotFoundException(documentId)
    }

    private fun isFileId(id: String) =
        id.matches(Regex("f-[a-f0-9-]{36}__[A-Za-z0-9._-]{1,90}"))

    private fun backing(id: String): File {
        if (!isFileId(id)) throw FileNotFoundException("Unsafe document id")
        return File(base, id)
    }

    private fun addDocument(cursor: MatrixCursor, id: String) {
        val directory = id == "root" || id == "folder"
        val file = if (directory) null else backing(id).also {
            if (!it.isFile) throw FileNotFoundException(id)
        }
        cursor.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, id)
            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                when (id) {
                    "root" -> "CountAway Test Storage"
                    "folder" -> "Backup Scenarios"
                    else -> id.substringAfter("__")
                })
            add(DocumentsContract.Document.COLUMN_MIME_TYPE,
                if (directory) DocumentsContract.Document.MIME_TYPE_DIR else "application/json")
            add(DocumentsContract.Document.COLUMN_FLAGS,
                if (directory) DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE else
                    DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                        DocumentsContract.Document.FLAG_SUPPORTS_DELETE)
            add(DocumentsContract.Document.COLUMN_SIZE, file?.length() ?: 0L)
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                file?.lastModified() ?: System.currentTimeMillis())
        }
    }

    private companion object {
        val ROOT_COLUMNS = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE, DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_ICON, DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
        )
        val DOCUMENT_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
