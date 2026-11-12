package com.santiagorodriguez.countaway.backup

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context

/** Persisted daily check and coalesced best-effort mutation backups. */
internal object AutoBackupScheduler {
    const val CHANGE_JOB_ID = 1276201
    const val DAILY_JOB_ID = 1276202
    private const val DAY = 24L * 60 * 60 * 1000
    private const val HOUR = 60L * 60 * 1000

    fun mutationCommitted(context: Context) {
        val app = context.applicationContext
        runCatching {
            AutoBackupStore.update(app) { state ->
                if (state.enabled) state.copy(
                    dirtyRevision = state.dirtyRevision + 1,
                    failures = 0,
                ) else state
            }
            ensureScheduled(app)
        }
        // A scheduler failure cannot roll back successful event persistence.
    }

    fun ensureScheduled(context: Context) {
        val app = context.applicationContext
        val state = AutoBackupStore.read(app)
        val scheduler = app.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        if (!state.enabled) {
            cancel(app)
            return
        }
        val component = ComponentName(app, AutoBackupJobService::class.java)
        if (scheduler.getPendingJob(DAILY_JOB_ID) == null) {
            val daily = JobInfo.Builder(DAILY_JOB_ID, component)
                .setPeriodic(DAY, HOUR).setPersisted(true).build()
            scheduleOrReport(app, scheduler, daily, state.generation)
        }
        if (state.dirtyRevision > state.savedRevision && state.failures < 3 &&
            scheduler.getPendingJob(CHANGE_JOB_ID) == null) {
            val delay = when (state.failures) {
                0 -> 60_000L
                1 -> 15L * 60_000L
                else -> HOUR
            }
            val pending = JobInfo.Builder(CHANGE_JOB_ID, component)
                .setMinimumLatency(delay).build()
            scheduleOrReport(app, scheduler, pending, state.generation)
        }
    }

    fun cancel(context: Context) {
        val scheduler = context.applicationContext
            .getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        scheduler.cancel(CHANGE_JOB_ID)
        scheduler.cancel(DAILY_JOB_ID)
    }

    private fun scheduleOrReport(
        context: Context, scheduler: JobScheduler, info: JobInfo, generation: Long,
    ) {
        val result = runCatching { scheduler.schedule(info) }.getOrDefault(JobScheduler.RESULT_FAILURE)
        if (result != JobScheduler.RESULT_SUCCESS) {
            runCatching {
                AutoBackupStore.update(context) { state ->
                    if (state.generation == generation) state.copy(lastError = "SCHEDULE_FAILED")
                    else state
                }
            }
        }
    }
}
