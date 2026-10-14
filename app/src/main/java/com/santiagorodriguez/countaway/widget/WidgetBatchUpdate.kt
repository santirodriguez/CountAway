package com.santiagorodriguez.countaway.widget

/** A broken or removed widget must not prevent updates to its siblings. */
internal object WidgetBatchUpdate {
    fun forEach(
        ids: IntArray,
        update: (Int) -> Unit,
        onFailure: (Int, RuntimeException) -> Unit,
    ) {
        for (id in ids) {
            try {
                update(id)
            } catch (failure: RuntimeException) {
                onFailure(id, failure)
            }
        }
    }
}
