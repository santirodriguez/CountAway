package com.santiagorodriguez.countaway.data

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object CountdownIo {
    // Persistent repository mutations remain strictly serialized.
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "CountAway-IO")
    }
    // External document providers may stall indefinitely. Keep their bounded
    // workers and queue separate from event saves, reminder work and widgets.
    private val documentExecutor = ThreadPoolExecutor(
        2, 2, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(4),
        ThreadFactory { task -> Thread(task, "CountAway-DocumentIO").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )
    private val mainHandler = Handler(Looper.getMainLooper())

    fun execute(task: () -> Unit) {
        executor.execute(task)
    }

    fun <T> submit(task: () -> T, onComplete: (Result<T>) -> Unit) {
        executor.execute {
            val result = runCatching(task)
            mainHandler.post { onComplete(result) }
        }
    }

    /** Dispatch a potentially slow ContentResolver operation without blocking the storage queue. */
    fun <T> submitDocument(task: () -> T, onComplete: (Result<T>) -> Unit) {
        try {
            documentExecutor.execute {
                val result = runCatching(task)
                mainHandler.post { onComplete(result) }
            }
        } catch (error: RejectedExecutionException) {
            // Saturated providers cannot grow an unbounded queue or delay app persistence.
            mainHandler.post { onComplete(Result.failure(error)) }
        }
    }
}
