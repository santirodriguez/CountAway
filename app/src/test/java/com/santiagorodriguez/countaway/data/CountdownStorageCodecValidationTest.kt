package com.santiagorodriguez.countaway.data

import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate

class CountdownStorageCodecValidationTest {
    @Test
    fun duplicateIdsAreRejected() {
        val error = assertThrows(CountdownDataException::class.java) {
            CountdownValidation.validateStoredEvents(
                listOf(
                    event("same", "First"),
                    event("same", "Second"),
                ),
            )
        }

        assertEquals(CountdownDataProblem.CORRUPT, error.problem)
    }

    @Test
    fun blankIdsAndTitlesAreRejected() {
        listOf(
            event("", "Valid title"),
            event("valid-id", "   "),
        ).forEach { invalidEvent ->
            val error = assertThrows(CountdownDataException::class.java) {
                CountdownValidation.validateStoredEvents(listOf(invalidEvent))
            }
            assertEquals(CountdownDataProblem.CORRUPT, error.problem)
        }
    }

    @Test
    fun importedIdsAndTitlesHaveReasonableLimits() {
        listOf(
            event("i".repeat(CountdownValidation.MAX_ID_LENGTH + 1), "Valid title"),
            event("valid-id", "t".repeat(CountdownValidation.MAX_TITLE_LENGTH + 1)),
        ).forEach { invalidEvent ->
            val error = assertThrows(CountdownDataException::class.java) {
                CountdownValidation.validateImportedEvents(listOf(invalidEvent))
            }
            assertEquals(CountdownDataProblem.CORRUPT, error.problem)
        }
    }

    @Test
    fun existingOversizedFieldsRemainReadableForBackwardCompatibility() {
        val legacyEvent = event(
            "i".repeat(CountdownValidation.MAX_ID_LENGTH + 1),
            "t".repeat(CountdownValidation.MAX_TITLE_LENGTH + 1),
        )

        CountdownValidation.validateStoredEvents(listOf(legacyEvent))
    }

    @Test
    fun oversizedPayloadIsRejectedBeforeParsing() {
        val payload = " ".repeat(CountdownValidation.MAX_PAYLOAD_BYTES + 1)

        val error = assertThrows(CountdownDataException::class.java) {
            CountdownStorageCodec.decode(payload)
        }

        assertEquals(CountdownDataProblem.CORRUPT, error.problem)
    }

    @Test
    fun oversizedStreamIsRejectedBeforeMaterializingWholePayload() {
        val input = ByteArrayInputStream(
            ByteArray(CountdownValidation.MAX_PAYLOAD_BYTES + 1) { ' '.code.toByte() },
        )

        val error = assertThrows(CountdownDataException::class.java) {
            CountdownStorageCodec.readUtf8Payload(input)
        }

        assertEquals(CountdownDataProblem.CORRUPT, error.problem)
    }

    private fun event(id: String, title: String): CountdownEvent = CountdownEvent(
        id = id,
        title = title,
        date = LocalDate.of(2026, 12, 1),
        type = EventType.TRIP,
        createdAt = Instant.parse("2026-08-23T12:00:00Z"),
    )
}
