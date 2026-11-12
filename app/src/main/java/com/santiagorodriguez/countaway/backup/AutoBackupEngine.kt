package com.santiagorodriguez.countaway.backup

import android.content.Context
import android.net.Uri
import android.content.Intent
import com.santiagorodriguez.countaway.data.CountdownIo
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
            if (initial.enabled &&
                initial.destination?.kind == destination.kind &&
                initial.destination.treeUri == destination.treeUri) {
                return@synchronized backupNow(app)
            }
            val attempt = System.currentTimeMillis()
            // Preparation can fail before a provider is contacted (e.g. corrupt
            // event data). Record the same visible destination error without
            // touching the previously committed folder or verified copies.
            val prepared = try {
                prepare(app)
            } catch (error: Exception) {
                markDestinationFailure(app, initial, attempt)
                throw error
            }
            val copy = try {
                create(app, destination, prepared, attempt)
            } catch (error: Exception) {
                markDestinationFailure(app, initial, attempt)
                throw error
            }
            // If opt-in cannot be persisted, remove only the new, still-untracked file.
            val final = try {
                AutoBackupStore.update(app) { current ->
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
            } catch (error: Exception) {
                discardUntrackedCopy(app, copy)
                markDestinationFailure(app, initial, attempt)
                throw error
            }
            // Setup must schedule even if the originating Activity is destroyed.
            runCatching { AutoBackupScheduler.ensureScheduled(app) }
            if (initial.destination?.treeUri != null &&
                initial.destination.treeUri != destination.treeUri) {
                releaseOldFolderGrant(app, initial.destination.treeUri)
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
            // Disabling/changing the destination can happen while a slow provider writes.
            // Never resurrect an old configuration or strand its newly written document.
            val updated = try {
                AutoBackupStore.update(app) { current ->
                    check(current.enabled && current.generation == initial.generation) {
                        "Backup destination changed before verification completed"
                    }
                    current.copy(
                        savedRevision = initial.dirtyRevision,
                        lastVerified = System.currentTimeMillis(), lastError = null, failures = 0,
                        fingerprint = copy.sha256, owned = current.owned + copy,
                    )
                }
            } catch (error: Exception) {
                discardUntrackedCopy(app, copy)
                throw error
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
        // Persist opt-out immediately. Provider I/O may still be in flight, so do
        // not revoke its SAF grant until the current single-flight write completes.
        val disabled = AutoBackupStore.update(app) {
            it.copy(enabled = false, generation = it.generation + 1, lastError = null)
        }
        runCatching { AutoBackupScheduler.cancel(app) }
        disabled.destination?.treeUri?.let { priorTree ->
            CountdownIo.submitDocument(
                task = {
                    synchronized(lock) {
                        // A later opt-in to the same folder must keep its grant.
                        val current = runCatching { AutoBackupStore.read(app) }.getOrNull()
                        if (current?.enabled != true || current.destination?.treeUri != priorTree) {
                            releaseOldFolderGrant(app, priorTree)
                        }
                    }
                },
                onComplete = { /* Revocation is best-effort; never erase saved files. */ },
            )
        }
        // Saved files survive opt-out, even when a slow provider cannot be stopped.
    }

    private fun releaseOldFolderGrant(context: Context, rawUri: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(rawUri), Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    /** Keep the previous verified destination active, and make a failed switch visible
     * after the screen is reopened. Never overwrite a newer committed selection. */
    private fun markDestinationFailure(
        context: Context,
        initial: AutoBackupStore.State,
        attemptedAt: Long,
    ) {
        runCatching {
            AutoBackupStore.update(context) { current ->
                if (current.generation == initial.generation &&
                    current.destination == initial.destination) {
                    current.copy(
                        lastAttempt = attemptedAt,
                        lastError = "DESTINATION_CHANGE_FAILED",
                        failures = (current.failures + 1).coerceAtMost(3),
                    )
                } else current
            }
        }
    }

    /** A failed ledger update must never delete a previously committed owned document. */
    private fun discardUntrackedCopy(context: Context, copy: AutoBackupStore.OwnedCopy) {
        val committed = runCatching { AutoBackupStore.read(context) }
            .getOrNull()?.owned?.any { it.uri == copy.uri } == true
        if (!committed) runCatching { AutoBackupFiles.deleteVerifiedOwned(context, copy) }
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
            val removed = AutoBackupFiles.deleteVerifiedOwned(context, copy) ||
                (state.destination?.kind == AutoBackupStore.Kind.DEFAULT_DOWNLOADS &&
                    AutoBackupFiles.confirmedMissingOwnedDownload(context, copy))
            if (!removed) {
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
