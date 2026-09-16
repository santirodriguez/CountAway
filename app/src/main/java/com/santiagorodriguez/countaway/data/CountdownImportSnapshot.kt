package com.santiagorodriguez.countaway.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class CountdownImportSnapshot(private val file: File) {
    constructor(context: Context) : this(File(context.cacheDir, FILE_NAME))

    fun write(payload: String) {
        CountdownValidation.validatePayloadSize(payload)
        val parent = file.parentFile ?: throw IOException("Import snapshot has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Could not create import snapshot directory")
        }

        val temporary = temporaryFile()
        temporary.delete()
        try {
            FileOutputStream(temporary).use { output ->
                output.write(payload.toByteArray(Charsets.UTF_8))
                output.flush()
                output.fd.sync()
            }
            if (file.exists() && !file.delete()) {
                throw IOException("Could not replace import snapshot")
            }
            if (!temporary.renameTo(file)) {
                throw IOException("Could not finalize import snapshot")
            }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    fun readPayload(): String = file.inputStream().use(CountdownStorageCodec::readUtf8Payload)

    fun exists(): Boolean = file.isFile

    fun clear() {
        file.delete()
        temporaryFile().delete()
    }

    private fun temporaryFile(): File = File(file.parentFile, "${file.name}.tmp")

    private companion object {
        const val FILE_NAME = "pending-countaway-import.json"
    }
}
