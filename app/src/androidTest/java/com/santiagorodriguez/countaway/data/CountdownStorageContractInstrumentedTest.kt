package com.santiagorodriguez.countaway.data

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import com.santiagorodriguez.countaway.model.ReminderOption
import com.santiagorodriguez.countaway.model.RepeatRule
import com.santiagorodriguez.countaway.notification.ArrivalNotificationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
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
    fun historicalLongTitleRoundTripsThroughImport() {
        val decoded = CountdownStorageCodec.decode(legacyLongTitleFixture())
        val exported = CountdownStorageCodec.encode(decoded)
        val restored = CountdownStorageCodec.decodeForImport(exported)

        assertTrue(decoded.single().title.length > CountdownValidation.MAX_TITLE_LENGTH)
        assertEquals(decoded, restored)
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
    fun schemaVersionStringIsRejectedInsteadOfCoerced() {
        assertDataProblem(
            CountdownDataProblem.CORRUPT,
            """{"schemaVersion":"5","events":[]}""",
        )
    }

    @Test
    fun dateOutsideProductDomainIsRejectedBeforeImportCanReplaceData() {
        assertDataProblem(CountdownDataProblem.CORRUPT, extremeDateFixture())
    }

    @Test
    fun repositoryRoundTripExercisesAndroidAtomicFileWithoutTouchingAppData() {
        withIsolatedRepository { _, repository ->
            val events = CountdownStorageCodec.decode(SCHEMA_5_FIXTURE)

            repository.save(events)

            assertEquals(events, successfulEvents(repository.loadResult()))
        }
    }

    @Test
    fun backupOnlyAtomicFileStateIsRecoveredInsteadOfReturningAnEmptyList() {
        withIsolatedRepository { filesDir, repository ->
            filesDir.mkdirs()
            File(filesDir, "countaways.json.bak").writeText(SCHEMA_5_FIXTURE, Charsets.UTF_8)

            val loaded = successfulEvents(repository.loadResult())

            assertEquals(CountdownStorageCodec.decode(SCHEMA_5_FIXTURE), loaded)
            assertTrue(File(filesDir, "countaways.json").isFile)
        }
    }

    @Test
    fun baseAndBackupAtomicFileStateRecoversTheLastCommittedBackup() {
        withIsolatedRepository { filesDir, repository ->
            filesDir.mkdirs()
            File(filesDir, "countaways.json").writeText(SCHEMA_5_FIXTURE, Charsets.UTF_8)
            File(filesDir, "countaways.json.bak").writeText(SCHEMA_4_FIXTURE, Charsets.UTF_8)

            val loaded = successfulEvents(repository.loadResult())

            assertEquals(CountdownStorageCodec.decode(SCHEMA_4_FIXTURE), loaded)
        }
    }

    @Test
    fun orphanNewFileIsNotMisclassifiedAsNeverHavingStoredData() {
        withIsolatedRepository { filesDir, repository ->
            filesDir.mkdirs()
            File(filesDir, "countaways.json.new").writeText("partial", Charsets.UTF_8)

            val loaded = repository.loadResult()

            assertTrue(loaded is CountdownLoadResult.Failure)
            assertEquals(
                CountdownDataProblem.CORRUPT,
                (loaded as CountdownLoadResult.Failure).problem,
            )
        }
    }

    @Test
    fun invalidImportLeavesExistingRepositoryUntouched() {
        withIsolatedRepository { _, repository ->
            val original = CountdownStorageCodec.decode(SCHEMA_5_FIXTURE)
            repository.save(original)

            try {
                repository.importPayload(extremeDateFixture())
                throw AssertionError("Expected CountdownDataException")
            } catch (error: CountdownDataException) {
                assertEquals(CountdownDataProblem.CORRUPT, error.problem)
            }

            assertEquals(original, successfulEvents(repository.loadResult()))
        }
    }

    @Test
    fun staleEditorSaveCannotOverwriteNewerEventState() {
        withIsolatedRepository { _, repository ->
            val original = event("shared", "Original")
            val newer = original.copy(title = "Newer")
            val stale = original.copy(title = "Stale")
            repository.save(listOf(original))
            repository.save(listOf(newer))

            assertEquals(
                CountdownMutationResult.CONFLICT,
                repository.saveEvent(original, stale),
            )
            assertEquals(listOf(newer), successfulEvents(repository.loadResult()))
        }
    }

    @Test
    fun staleEditorDeleteCannotDeleteNewerEventState() {
        withIsolatedRepository { _, repository ->
            val original = event("shared", "Original")
            val newer = original.copy(title = "Newer")
            repository.save(listOf(original))
            repository.save(listOf(newer))

            assertEquals(
                CountdownMutationResult.CONFLICT,
                repository.deleteEvent(original),
            )
            assertEquals(listOf(newer), successfulEvents(repository.loadResult()))
        }
    }

    @Test
    fun notificationFailuresAreBoundedWithoutBeingMarkedDelivered() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = ArrivalNotificationState(context)
        val event = event("retry-state", "Retry").copy(reminder = ReminderOption.ON_DAY)
        val scheduledDate = LocalDate.of(2026, 12, 1)
        state.remove(event.id)

        try {
            repeat(ArrivalNotificationState.MAX_DELIVERY_ATTEMPTS) {
                assertTrue(state.canAttempt(event, scheduledDate))
                state.recordFailure(event, scheduledDate)
            }

            assertFalse(state.canAttempt(event, scheduledDate))
            assertFalse(state.wasDelivered(event, scheduledDate))
        } finally {
            state.remove(event.id)
        }
    }

    @Test
    fun independentImportSnapshotsDoNotCrossPayloads() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = CountdownImportSnapshot.create(context)
        val second = CountdownImportSnapshot.create(context)

        try {
            assertFalse(first.identity == second.identity)
            first.write(SCHEMA_4_FIXTURE)
            second.write(SCHEMA_5_FIXTURE)

            first.clear()

            assertFalse(first.exists())
            assertTrue(second.exists())
            assertEquals(SCHEMA_5_FIXTURE, second.readPayload())
            assertEquals(second.identity, CountdownImportSnapshot.restore(context, second.identity)?.identity)
        } finally {
            first.clear()
            second.clear()
        }
    }

    private fun assertDataProblem(expected: CountdownDataProblem, payload: String) {
        try {
            CountdownStorageCodec.decodeForImport(payload)
            throw AssertionError("Expected CountdownDataException")
        } catch (error: CountdownDataException) {
            assertEquals(expected, error.problem)
        }
    }

    private fun successfulEvents(result: CountdownLoadResult): List<CountdownEvent> {
        assertTrue(result is CountdownLoadResult.Success)
        return (result as CountdownLoadResult.Success).events
    }

    private fun withIsolatedRepository(block: (File, CountdownRepository) -> Unit) {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val isolatedFilesDir = File(
            targetContext.cacheDir,
            "countaway-contract-${UUID.randomUUID()}",
        )
        val isolatedContext = object : ContextWrapper(targetContext) {
            override fun getFilesDir(): File = isolatedFilesDir
        }

        try {
            block(isolatedFilesDir, CountdownRepository(isolatedContext))
        } finally {
            isolatedFilesDir.deleteRecursively()
        }
    }

    private fun event(id: String, title: String): CountdownEvent = CountdownEvent(
        id = id,
        title = title,
        date = LocalDate.of(2026, 12, 1),
        type = EventType.EVENT,
        createdAt = Instant.parse("2026-08-23T12:00:00Z"),
    )

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

    private fun extremeDateFixture(): String = """
        {
          "schemaVersion": 5,
          "events": [
            {
              "id": "extreme-date",
              "title": "Extreme",
              "date": "+999999999-12-31",
              "type": "event",
              "iconKey": "calendar",
              "reminderKey": "on_day",
              "repeatRule": "none",
              "createdAt": "2020-01-01T00:00:00Z"
            }
          ]
        }
    """.trimIndent()

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
