package com.santiagorodriguez.countaway.backup

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.FutureTask
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Finite document workers and non-blocking JobService lifecycle callbacks. */
class AutoBackupJobService : JobService() {
    private val handler = Handler(Looper.getMainLooper())
    private val running = mutableMapOf<Int, FutureTask<Unit>>()

    override fun onStartJob(params: JobParameters): Boolean {
        if (!runCatching { AutoBackupStore.read(applicationContext).enabled }.getOrDefault(false))
            return false
        // This FutureTask is also the job's cancellation identity.
        lateinit var task: FutureTask<Unit>
        task = FutureTask {
            runCatching { AutoBackupEngine.backupNow(applicationContext) }
            handler.post {
                if (running[params.jobId] === task) {
                    running.remove(params.jobId)
                    jobFinished(params, false)
                    handler.postDelayed({
                        runCatching { AutoBackupScheduler.ensureScheduled(applicationContext) }
                    }, 1_000L)
                }
            }
            Unit
        }
        running[params.jobId] = task
        return try {
            EXECUTOR.execute(task)
            true
        } catch (_: Exception) {
            running.remove(params.jobId)
            false
        }
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel(true)
        // Periodic reconciliation remains registered; avoid unbounded immediate retries.
        return false
    }

    private companion object {
        val EXECUTOR = ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(2),
            { job -> Thread(job, "CountAway-AutoBackup").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        )
    }
}
