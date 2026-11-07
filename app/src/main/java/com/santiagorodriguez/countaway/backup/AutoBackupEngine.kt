package com.santiagorodriguez.countaway.backup

import android.content.Context
import android.net.Uri
import com.santiagorodriguez.countaway.data.CountdownRepository
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Single-flight independent document worker; never run on the UI or repository IO executor. */
internal object AutoBackupEngine {
    data class Outcome(val created: Boolean, val verifiedAt: Long)

    private val lock = Any()
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneOffset.UTC)

    fun enable(context: Context, destination: AutoBackupStore.Destination): Outcome =
        synchronized(lock) {
            val app = context.applicationContext
            val initial = AutoBackupStore.read(app)
            val attempt = System.currentTimeMillis()
            val prepared = prepare(app)
            val copy = create(app, destination, prepared, attempt)
            val final = AutoBackupStore.update(app) { current ->
                check(initial.generation == current.generation) {
                    "Destination changed while writing; backup not activated"
                }
                current.copy(
                    enabled = true, generation = current.generation + 1,
                    destination = destination,
                    savedRevision = initial.dirtyRevision,
                    lastAttempt = attempt, lastVerified = System.currentTimeMillis(),
                    lastError = null, failures = 0, fingerprint = copy.sha256, owned = listOf(copy),
                )
            }
            Outcome(true, final.lastVerified)
        }

    fun backupNow(context: Context): Outcome = synchronized(lock) {
        val app = context.applicationContext
        val initial = AutoBackupStore.read(app)
        check(initial.enabled && initial.destination != null) { "Automatic backups are disabled" }
        val attempt = System.currentTimeMillis()
        AutoBackupStore.update(app) { current ->
            if (current.generation == initial.generation)
                current.copy(lastAttempt = attempt, lastError = null) else current
        }
        try {
            val prepared = prepare(app)
            val last = initial.owned.lastOrNull()
            if (last != null && last.sha256 == prepared.sha256 &&
                AutoBackupFiles.verify(app, Uri.parse(last.uri), last.sha256)) {
                val updated = AutoBackupStore.update(app) { current ->
                    if (current.enabled && current.generation == initial.generation) {
                        current.copy(
                            lastVerified = System.currentTimeMillis(),
                            lastError = null, failures = 0, savedRevision = initial.dirtyRevision,
                            fingerprint = prepared.sha256,
                        )
                    } else current
                }
                return@synchronized Outcome(false, updated.lastVerified)
            }

            val copy = create(app, initial.destination, prepared, attempt)
            val updated = AutoBackupStore.update(app) { current ->
                check(current.enabled && current.generation == initial.generation) {
                    "Backup destination changed before verification completed"
                }
                current.copy(
                    savedRevision = initial.dirtyRevision,
                    lastVerified = System.currentTimeMillis(), lastError = null, failures = 0,
                    fingerprint = copy.sha256, owned = current.owned + copy,
                )
            }
            prune(app, updated.generation)
            Outcome(true, updated.lastVerified)
        } catch (error: Exception) {
            runCatching {
                AutoBackupStore.update(app) { current ->
                    if (current.enabled && current.generation == initial.generation)
                        current.copy(lastError = "BACKUP_FAILED", failures = (current.failures + 1).coerceAtMost(3))
                    else current
                }
            }
            throw error
        }
    }

    fun disable(context: Context) {
        val app = context.applicationContext
        AutoBackupStore.update(app) {
            it.copy(enabled = false, generation = it.generation + 1, lastError = null)
        }
        // No old backup file is deleted when opt-out happens.
    }

    private data class Prepared(val payload: String, val sha256: String)

    private fun prepare(context: Context): Prepared {
        check(!Thread.currentThread().isInterrupted) { "Backup cancelled" }
        // Repository export is already protected by its atomic lock and rejects corrupt data.
        val payload = CountdownRepository(context).exportPayload()
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        val digest = AutoBackupFiles.sha256(bytes)
        return Prepared(payload, digest)
    }

    private fun create(
        context: Context, target: AutoBackupStore.Destination,
        prepared: Prepared, whenStarted: Long,
    ): AutoBackupStore.OwnedCopy {
        val name = "CountAway-" + stamp.format(Instant.ofEpochMilli(whenStarted)) +
            "-" + UUID.randomUUID().toString().take(8) + ".json"
        val uri = AutoBackupFiles.createVerified(context, target, prepared.payload, prepared.sha256, name)
        return AutoBackupStore.OwnedCopy(uri.toString(), prepared.sha256, System.currentTimeMillis())
    }

    private fun prune(context: Context, expectedGeneration: Long) {
        val state = AutoBackupStore.read(context)
        if (!state.enabled || state.generation != expectedGeneration) return
        for (copy in AutoBackupRetention.stale(state.owned)) {
            if (!AutoBackupFiles.deleteVerifiedOwned(context, copy)) {
                runCatching {
                    AutoBackupStore.update(context) { current ->
                        if (current.generation == expectedGeneration)
                            current.copy(lastError = "CLEANUP_FAILED") else current
                    }
                }
                continue
            }
            AutoBackupStore.update(context) { current ->
                if (current.generation == expectedGeneration)
                    current.copy(owned = current.owned.filterNot { it.uri == copy.uri })
                else current
            }
        }
    }
}
