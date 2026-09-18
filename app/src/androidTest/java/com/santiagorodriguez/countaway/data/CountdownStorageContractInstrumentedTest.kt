package com.santiagorodriguez.countaway.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.model.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CountdownStorageContractInstrumentedTest {
    @Test
    fun releasedSchemaFixturesDecodeOnAndroidRuntime() {
        val fixtures = listOf(
            SCHEMA_1_FIXTURE,
            SCHEMA_2_FIXTURE,
            SCHEMA_3_FIXTURE,
            SCHEMA_4_FIXTURE,
            SCHEMA_5_FIXTURE,
        )

        fixtures.forEach { payload ->
            assertEquals(1, CountdownStorageCodec.decode(payload).size)
        }
    }

    @Test
    fun schemasBeforeVersion5MigrateToNonRepeatingEvents() {
        listOf(
            SCHEMA_1_FIXTURE,
            SCHEMA_2_FIXTURE,
            SCHEMA_3_FIXTURE,
            SCHEMA_4_FIXTURE,
        ).forEach { payload ->
            assertEquals(RepeatRule.NONE, CountdownStorageCodec.decode(payload).single().repeatRule)
        }
    }

    @Test
    fun currentSchemaKeepsYearlyLeapDayAndRoundTripsSemantically() {
        val decoded = CountdownStorageCodec.decode(SCHEMA_5_FIXTURE)
        val event = decoded.single()

        assertEquals(RepeatRule.YEARLY, event.repeatRule)
        assertEquals("2024-02-29", event.date.toString())
        assertEquals(decoded, CountdownStorageCodec.decode(CountdownStorageCodec.encode(decoded)))
    }

    @Test
    fun historicalLongTitleFixtureRemainsReadableAsStoredData() {
        val event = CountdownStorageCodec.decode(legacyLongTitleFixture()).single()

        assertTrue(event.title.length > CountdownValidation.MAX_TITLE_LENGTH)
    }

    @Test
    fun futureSchemaFixtureIsClassifiedAsUnsupported() {
        assertDataProblem(CountdownDataProblem.UNSUPPORTED_SCHEMA, FUTURE_SCHEMA_FIXTURE)
    }

    @Test
    fun corruptFixtureIsClassifiedAsCorrupt() {
        assertDataProblem(CountdownDataProblem.CORRUPT, CORRUPT_FIXTURE)
    }

    @Test
    fun repositoryRoundTripExercisesAndroidAtomicFileWithoutTouchingAppData() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val isolatedFilesDir = File(
            targetContext.cacheDir,
            "countaway-contract-${UUID.randomUUID()}",
        )
        val isolatedContext = object : ContextWrapper(targetContext) {
            override fun getFilesDir(): File = isolatedFilesDir
        }

        try {
            val repository = CountdownRepository(isolatedContext)
            val events = CountdownStorageCodec.decode(SCHEMA_5_FIXTURE)

            repository.save(events)

            val loaded = repository.loadResult()
            assertTrue(loaded is CountdownLoadResult.Success)
            assertEquals(events, (loaded as CountdownLoadResult.Success).events)
        } finally {
            isolatedFilesDir.deleteRecursively()
        }
    }

    private fun assertDataProblem(expected: CountdownDataProblem, payload: String) {
        try {
            CountdownStorageCodec.decode(payload)
            throw AssertionError("Expected CountdownDataException")
        } catch (error: CountdownDataException) {
            assertEquals(expected, error.problem)
        }
    }

    private fun legacyLongTitleFixture(): String {
        val longTitle = "L".repeat(CountdownValidation.MAX_TITLE_LENGTH + 1)
        return """
            {
              "schemaVersion": 4,
              "events": [
                {
                  "id": "legacy-long-title",
                  "title": "$longTitle",
                  "date": "2026-12-24",
                  "type": "event",
                  "iconKey": "calendar",
                  "reminderKey": "off",
                  "createdAt": "2020-01-01T00:00:00Z"
                }
              ]
            }
        """.trimIndent()
    }

    private companion object {
        val SCHEMA_1_FIXTURE = """
            {
              "schemaVersion": 1,
              "events": [
                {
                  "id": "schema-1",
                  "title": "Legacy birthday",
                  "date": "2024-02-29",
                  "type": "BIRTHDAY",
                  "createdAt": "2020-01-01T00:00:00Z"
                }
              ]
            }
        """.trimIndent()

        val SCHEMA_2_FIXTURE = """
            {
              "schemaVersion": 2,
              "events": [
                {
                  "id": "schema-2",
                  "title": "Stored key",
                  "date": "2026-11-12",
                  "type": "birthday",
                  "createdAt": "2020-01-01T00:00:00Z"
                }
              ]
            }
        """.trimIndent()

        val SCHEMA_3_FIXTURE = """
            {
              "schemaVersion": 3,
              "events": [
                {
                  "id": "schema-3",
                  "title": "Notification migration",
                  "date": "2026-10-02",
                  "type": "birthday",
                  "iconKey": "cake",
                  "notifyOnArrival": true,
                  "createdAt": "2020-01-01T00:00:00Z"
                }
              ]
            }
        """.trimIndent()

        val SCHEMA_4_FIXTURE = """
            {
              "schemaVersion": 4,
              "events": [
                {
                  "id": "schema-4",
                  "title": "Reminder migration",
                  "date": "2026-10-03",
                  "type": "event",
                  "iconKey": "calendar",
                  "reminderKey": "one_day",
                  "createdAt": "2020-01-01T00:00:00Z"
                }
              ]
            }
        """.trimIndent()

        val SCHEMA_5_FIXTURE = """
            {
              "schemaVersion": 5,
              "events": [
                {
                  "id": "schema-5",
                  "title": "Yearly leap day",
                  "date": "2024-02-29",
                  "type": "birthday",
                  "iconKey": "cake",
                  "reminderKey": "seven_days",
                  "repeatRule": "yearly",
                  "createdAt": "2020-01-01T00:00:00Z"
                }
              ]
            }
        """.trimIndent()

        val FUTURE_SCHEMA_FIXTURE = """
            {
              "schemaVersion": 6,
              "events": []
            }
        """.trimIndent()

        val CORRUPT_FIXTURE = """
            {
              "schemaVersion": 5,
              "events": [
                {
                  "id": "broken"
                }
              ]
            }
        """.trimIndent()
    }
}
