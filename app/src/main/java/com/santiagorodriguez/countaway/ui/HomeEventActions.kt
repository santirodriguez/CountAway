package com.santiagorodriguez.countaway.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.data.CountdownMutationResult
import com.santiagorodriguez.countaway.data.CountdownMutations
import com.santiagorodriguez.countaway.data.EventRevision
import com.santiagorodriguez.countaway.data.RetainedOperation
import com.santiagorodriguez.countaway.model.CountdownEvent

internal class HomeEventActions(
    private val activity: Activity,
    val session: Session,
    private val edit: (CountdownEvent) -> Unit,
    private val addWidget: (CountdownEvent) -> Unit,
    private val refresh: () -> Unit,
) {
    class Session {
        val deletion = RetainedOperation<CountdownMutationResult>()
    }

    private var popup: PopupMenu? = null
    private var confirmation: AlertDialog? = null
    private var resumed = false
    private val focus = EditorDialogFocus(activity)

    init { session.deletion.onChanged = ::consumeResult }

    fun show(event: CountdownEvent, anchor: View) {
        if (!resumed || session.deletion.running || popup != null || confirmation != null) return
        focus.capture(anchor)
        val menu = PopupMenu(activity, anchor, Gravity.END)
        popup = menu
        menu.inflate(R.menu.event_actions)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.home_event_edit -> edit(event)
                R.id.home_event_duplicate -> activity.startActivity(
                    Intent(activity, EditorActivity::class.java)
                        .putExtra(EditorActivity.EXTRA_DUPLICATE_EVENT_ID, event.id)
                        .putExtra(EditorActivity.EXTRA_SOURCE_REVISION, EventRevision.of(event)),
                )
                R.id.home_event_widget -> addWidget(event)
                R.id.home_event_delete -> confirmDelete(event, anchor)
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        menu.setOnDismissListener {
            if (popup === menu) popup = null
            if (confirmation == null) restoreFocus(anchor, event.id)
        }
        menu.show()
    }

    private fun confirmDelete(event: CountdownEvent, anchor: View) {
        if (session.deletion.running || confirmation != null) return
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.delete_title)
            .setMessage(activity.getString(R.string.delete_message, event.title))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                if (!session.deletion.running) {
                    val mutations = CountdownMutations(activity.applicationContext)
                    val id = event.id
                    val revision = EventRevision.of(event)
                    session.deletion.start { mutations.delete(id, revision) }
                }
            }
            .create()
        confirmation = dialog
        dialog.setOnDismissListener {
            if (confirmation === dialog) confirmation = null
            restoreFocus(anchor, event.id)
        }
        dialog.show()
    }

    private fun restoreFocus(anchor: View, eventId: String) {
        if (resumed && anchor.isAttachedToWindow && anchor.tag == eventId) focus.restore(anchor)
    }

    fun resume() {
        resumed = true
        consumeResult()
    }

    fun pause() {
        resumed = false
        popup?.dismiss()
        confirmation?.dismiss()
        focus.clear()
    }

    fun close() {
        pause()
        session.deletion.onChanged = null
    }

    private fun consumeResult() {
        if (!resumed || activity.isFinishing || activity.isDestroyed) return
        val result = session.deletion.takeResult() ?: return
        result.onSuccess {
            if (it == CountdownMutationResult.CONFLICT) {
                Toast.makeText(activity, R.string.event_action_stale, Toast.LENGTH_LONG).show()
            }
        }.onFailure {
            Toast.makeText(activity, R.string.data_delete_failed, Toast.LENGTH_LONG).show()
        }
        refresh()
    }
}
