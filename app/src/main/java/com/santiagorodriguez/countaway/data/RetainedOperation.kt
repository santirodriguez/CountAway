package com.santiagorodriguez.countaway.data

/** Screen-owned work state. Accessed on the main thread, without retaining the screen. */
class RetainedOperation<T> {
    var running = false
        private set
    var result: Result<T>? = null
        private set
    var onChanged: (() -> Unit)? = null

    fun start(task: () -> T) {
        check(!running)
        running = true
        result = null
        CountdownIo.submit(task) { completed ->
            running = false
            result = completed
            onChanged?.invoke()
        }
    }

    fun takeResult(): Result<T>? = result.also { result = null }
}
