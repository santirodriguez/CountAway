package com.santiagorodriguez.countaway.backup

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.santiagorodriguez.countaway.data.CountdownStorageCodec
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Only newly created URIs and explicitly recorded owned URIs can be touched. */
internal object AutoBackupFiles {
    private fun requireGrantedFolder(context: Context, target: AutoBackupStore.Destination): Uri {
        val raw = target.treeUri ?: error("Missing selected folder")
        val tree = Uri.parse(raw)
        require(tree.scheme == "content") { "Unsupported folder" }
        val allowed = context.contentResolver.persistedUriPermissions.any {
            it.uri == tree && it.isReadPermission && it.isWritePermission
        }
        check(allowed) { "Folder permissions unavailable" }
        return tree
    }

    private fun create(context: Context, target: AutoBackupStore.Destination, name: String): Uri {
        val resolver = context.contentResolver
        return when (target.kind) {
            AutoBackupStore.Kind.DEFAULT_DOWNLOADS -> {
                check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "Shared Downloads require Android 10 or newer"
                }
                createDefaultDownloads(context, name)
            }
            AutoBackupStore.Kind.SELECTED_FOLDER -> {
                val tree = requireGrantedFolder(context, target)
                val parent = DocumentsContract.buildDocumentUriUsingTree(
                    tree, DocumentsContract.getTreeDocumentId(tree))
                DocumentsContract.createDocument(resolver, parent, "application/json", name)
                    ?: error("Selected provider cannot create documents")
            }
        }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.Q)
    private fun createDefaultDownloads(context: Context, name: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CountAway/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Downloads file cannot be created")
    }

    private fun publish(context: Context, target: AutoBackupStore.Destination, uri: Uri) {
        if (target.kind == AutoBackupStore.Kind.DEFAULT_DOWNLOADS) {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            check(context.contentResolver.update(uri, values, null, null) == 1) {
                "Verified Downloads file could not be published"
            }
        }
    }

    fun verify(context: Context, uri: Uri, expectedSha256: String): Boolean {
        return try {
        val payload = context.contentResolver.openInputStream(uri)?.use {
            CountdownStorageCodec.readUtf8Payload(it)
        } ?: return false
        CountdownStorageCodec.decodeForImport(payload)
        sha256(payload.toByteArray(StandardCharsets.UTF_8)) == expectedSha256
    } catch (_: Exception) {
        false
    }
    }

    fun createVerified(
        context: Context,
        destination: AutoBackupStore.Destination,
        payload: String,
        expectedSha256: String,
        fileName: String,
    ): Uri {
        val uri = create(context, destination, fileName)
        var completed = false
        try {
            check(!Thread.currentThread().isInterrupted) { "Backup cancelled" }
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                output.write(payload.toByteArray(StandardCharsets.UTF_8))
                output.flush()
            } ?: error("Cannot open backup output")
            check(!Thread.currentThread().isInterrupted) { "Backup cancelled" }
            check(verify(context, uri, expectedSha256)) { "Backup read-back mismatch" }
            publish(context, destination, uri)
            check(verify(context, uri, expectedSha256)) { "Published backup cannot be read" }
            check(!Thread.currentThread().isInterrupted) { "Backup cancelled" }
            completed = true
            return uri
        } finally {
            if (!completed) runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }

    fun deleteVerifiedOwned(
        context: Context,
        copy: AutoBackupStore.OwnedCopy,
    ): Boolean {
        val uri = Uri.parse(copy.uri)
        // Modified documents are no longer ours to remove: retain them untouched.
        if (!verify(context, uri, copy.sha256)) return false
        return runCatching { context.contentResolver.delete(uri, null, null) == 1 }
            .getOrDefault(false)
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it)
        }
}
