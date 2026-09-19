package com.santiagorodriguez.countaway.data

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

object CountdownIo {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "CountAway-IO")
    }
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
}
