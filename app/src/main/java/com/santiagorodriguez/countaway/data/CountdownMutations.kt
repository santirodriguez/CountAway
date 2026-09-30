package com.santiagorodriguez.countaway.data

import android.content.Context
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.notification.ArrivalNotificationPolicy
import com.santiagorodriguez.countaway.notification.ArrivalNotificationScheduler
import com.santiagorodriguez.countaway.notification.ArrivalNotificationState
import com.santiagorodriguez.countaway.notification.ArrivalNotifier
import com.santiagorodriguez.countaway.widget.CountdownWidgetProvider
import com.santiagorodriguez.countaway.widget.WidgetUpdateScheduler

/** Called on CountdownIo: persistence and its follow-up work outlive the initiating UI. */
class CountdownMutations(context: Context) {
    private val context = context.applicationContext
    private val repository = CountdownRepository(this.context)

    fun save(expectedRevision: String?, previous: CountdownEvent?, event: CountdownEvent): CountdownMutationResult {
        val result = repository.saveEventRevision(expectedRevision, event)
        if (result == CountdownMutationResult.APPLIED) {
            if (ArrivalNotificationPolicy.shouldResetDeliveryState(previous, event)) {
                runCatching { ArrivalNotificationState(context).remove(event.id) }
            }
            runCatching { ArrivalNotifier.cancelEvent(context, event.id) }
            reconcile()
        }
        return result
    }

    fun delete(id: String, expectedRevision: String): CountdownMutationResult {
        val result = repository.deleteEventRevision(id, expectedRevision)
        if (result == CountdownMutationResult.APPLIED) {
            runCatching { ArrivalNotificationState(context).remove(id) }
            runCatching { ArrivalNotifier.cancelEvent(context, id) }
            reconcile()
        }
        return result
    }

    fun applyImport(snapshot: CountdownImportSnapshot) {
        try {
            repository.importPayload(snapshot.claimPayload())
            runCatching { ArrivalNotificationState(context).clear() }
            runCatching { ArrivalNotifier.cancelAllEventNotifications(context) }
            reconcile()
        } finally {
            runCatching { snapshot.clear() }
        }
    }

    fun reconcile() {
        val time = CountdownTime.snapshot()
        ArrivalNotificationScheduler.invalidatePlan()
        runCatching { CountdownWidgetProvider.updateAllWidgets(context, time) }
        runCatching { WidgetUpdateScheduler.ensureScheduled(context, time) }
        runCatching { ArrivalNotificationScheduler.ensureScheduled(context, time) }
    }
}
