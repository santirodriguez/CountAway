package com.santiagorodriguez.countaway.data

import android.content.Context
import android.util.AtomicFile
import com.santiagorodriguez.countaway.model.CountdownEvent
import java.io.File

sealed interface CountdownLoadResult {
    data class Success(val events: List<CountdownEvent>) : CountdownLoadResult
    data class Failure(val problem: CountdownDataProblem) : CountdownLoadResult
}

class CountdownRepository(context: Context) {
    private val atomicFile = AtomicFile(File(context.filesDir, FILE_NAME))

    fun loadResult(): CountdownLoadResult {
        if (!atomicFile.baseFile.exists()) return CountdownLoadResult.Success(emptyList())

        return try {
            val payload = atomicFile.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            CountdownLoadResult.Success(CountdownStorageCodec.decode(payload))
        } catch (error: CountdownDataException) {
            CountdownLoadResult.Failure(error.problem)
        } catch (_: Exception) {
            CountdownLoadResult.Failure(CountdownDataProblem.CORRUPT)
        }
    }

    fun load(): List<CountdownEvent> = when (val result = loadResult()) {
        is CountdownLoadResult.Success -> result.events
        is CountdownLoadResult.Failure -> emptyList()
    }

    fun exportPayload(): String = when (val result = loadResult()) {
        is CountdownLoadResult.Success -> CountdownStorageCodec.encode(result.events)
        is CountdownLoadResult.Failure -> throw CountdownDataException(result.problem)
    }

    fun previewImport(payload: String): Int = CountdownStorageCodec.decode(payload).size

    fun importPayload(payload: String): Int {
        val events = CountdownStorageCodec.decode(payload)
        save(events)
        return events.size
    }

    fun save(events: List<CountdownEvent>) {
        val payload = CountdownStorageCodec.encode(events).toByteArray(Charsets.UTF_8)
        val stream = atomicFile.startWrite()
        try {
            stream.write(payload)
            stream.flush()
            atomicFile.finishWrite(stream)
        } catch (error: Exception) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    private companion object {
        const val FILE_NAME = "countaways.json"
    }
}
