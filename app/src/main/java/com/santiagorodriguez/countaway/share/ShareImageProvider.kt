package com.santiagorodriguez.countaway.share

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.UUID

class ShareImageProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String {
        shareFile(uri, requireExists = true)
        return if (uri.pathSegments.firstOrNull() == PATH_TEXT) "text/plain" else MIME_TYPE
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Share files are read-only")
        return ParcelFileDescriptor.open(
            shareFile(uri, requireExists = true),
            ParcelFileDescriptor.MODE_READ_ONLY,
        )
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val file = shareFile(uri, requireExists = true)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(
                columns.map { column ->
                    when (column) {
                        OpenableColumns.DISPLAY_NAME -> file.name
                        OpenableColumns.SIZE -> file.length()
                        else -> null
                    }
                },
            )
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun shareFile(uri: Uri, requireExists: Boolean): File {
        val providerContext = context ?: throw FileNotFoundException("Provider is not attached")
        if (uri.scheme != "content" || uri.authority != authority(providerContext)) {
            throw FileNotFoundException("Unexpected share URI")
        }
        val segments = uri.pathSegments
        if (segments.size != 2) throw FileNotFoundException("Unexpected share path")
        val fileName = segments[1]
        val directory = when (segments[0]) {
            PATH_CARD -> {
                if (!FILE_NAME_PATTERN.matches(fileName)) throw FileNotFoundException("Unexpected share image")
                ShareImageStore.directory(providerContext)
            }
            PATH_TEXT -> {
                if (!TEXT_NAME_PATTERN.matches(fileName)) throw FileNotFoundException("Unexpected share text")
                ShareTextStore.directory(providerContext)
            }
            else -> throw FileNotFoundException("Unexpected share path")
        }.canonicalFile
        val file = File(directory, fileName).canonicalFile
        if (file.parentFile != directory || (requireExists && !file.isFile)) {
            throw FileNotFoundException("Share image not found")
        }
        return file
    }

    companion object {
        private const val MIME_TYPE = "image/png"
        private const val PATH_CARD = "card"
        private const val PATH_TEXT = "text"
        private val FILE_NAME_PATTERN = Regex("""countaway-share-[0-9]+-[0-9a-fA-F-]+\.png""")
        private val TEXT_NAME_PATTERN = Regex("""countaway-text-[0-9]+-[0-9a-fA-F-]+\.txt""")

        internal fun authority(context: Context): String = "${context.packageName}.share"

        internal fun uriFor(context: Context, file: File): Uri = Uri.Builder()
            .scheme("content")
            .authority(authority(context))
            .appendPath(PATH_CARD)
            .appendPath(file.name)
            .build()

        internal fun textUriFor(context: Context, file: File): Uri = Uri.Builder()
            .scheme("content")
            .authority(authority(context))
            .appendPath(PATH_TEXT)
            .appendPath(file.name)
            .build()
    }
}

internal object ShareImageStore {
    private const val DIRECTORY_NAME = "share-cards"
    private const val MAX_AGE_MS = 24L * 60L * 60L * 1000L

    fun write(context: Context, bitmap: Bitmap): Uri {
        val directory = directory(context)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Could not create share cache")
        }
        prune(directory)

        val file = File(
            directory,
            "countaway-share-${System.currentTimeMillis()}-${UUID.randomUUID()}.png",
        )
        try {
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Could not encode share card"
                }
            }
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
        return ShareImageProvider.uriFor(context, file)
    }

    internal fun directory(context: Context): File = File(context.cacheDir, DIRECTORY_NAME)

    private fun prune(directory: File) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        directory.listFiles()
            ?.filter { it.isFile && it.lastModified() < cutoff }
            ?.forEach(File::delete)
    }
}

/** Exposes complete oversized share text by URI, never as a multi-megabyte Intent extra. */
internal object ShareTextStore {
    private const val DIRECTORY_NAME = "share-texts"
    private const val MAX_AGE_MS = 24L * 60L * 60L * 1000L

    fun write(context: Context, text: String): Uri {
        val directory = directory(context)
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IllegalStateException("Could not create share-text cache")
        }
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        directory.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach(File::delete)
        val file = File(directory, "countaway-text-${System.currentTimeMillis()}-${UUID.randomUUID()}.txt")
        try {
            FileOutputStream(file).bufferedWriter(Charsets.UTF_8).use { it.write(text) }
        } catch (error: Exception) {
            file.delete()
            throw error
        }
        return ShareImageProvider.textUriFor(context, file)
    }

    internal fun directory(context: Context): File = File(context.cacheDir, DIRECTORY_NAME)
}
