package com.santiagorodriguez.countaway.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class CountdownStorageCodecValidationTest {
    @Test
    fun duplicateIdsAreRejected() {
        val payload = payload(
            event("same", "First"),
            event("same", "Second"),
        )

        val error = assertThrows(CountdownDataException::class.java) {
            CountdownStorageCodec.decode(payload)
        }

        assertEquals(CountdownDataProblem.CORRUPT, error.problem)
    }

    @Test
    fun blankIdsAndTitlesAreRejected() {
        listOf(
            payload(event("", "Valid title")),
            payload(event("valid-id", "   ")),
        ).forEach { payload ->
            val error = assertThrows(CountdownDataException::class.java) {
                CountdownStorageCodec.decode(payload)
            }
            assertEquals(CountdownDataProblem.CORRUPT, error.problem)
        }
    }

    @Test
    fun oversizedPayloadIsRejectedBeforeParsing() {
        val payload = " ".repeat(5 * 1024 * 1024 + 1)

        val error = assertThrows(CountdownDataException::class.java) {
            CountdownStorageCodec.decode(payload)
        }

        assertEquals(CountdownDataProblem.CORRUPT, error.problem)
    }

    @Test
    fun oversizedStreamIsRejectedBeforeMaterializingWholePayload() {
        val input = ByteArrayInputStream(ByteArray(5 * 1024 * 1024 + 1) { ' '.code.toByte() })

        val error = assertThrows(CountdownDataException::class.java) {
            CountdownStorageCodec.readUtf8Payload(input)
        }

        assertEquals(CountdownDataProblem.CORRUPT, error.problem)
    }

    private fun payload(vararg events: String): String =
        "{\"schemaVersion\":4,\"events\":[${events.joinToString(",")}] }"

    private fun event(id: String, title: String): String =
        """{"id":"$id","title":"$title","date":"2026-12-01","type":"trip","iconKey":"airplane","reminderKey":"off","createdAt":"2026-08-23T12:00:00Z"}"""
}
