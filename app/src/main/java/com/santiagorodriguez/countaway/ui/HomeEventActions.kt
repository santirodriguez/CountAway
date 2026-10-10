package com.santiagorodriguez.countaway.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.data.CountdownMutationResult
import com.santiagorodriguez.countaway.data.CountdownMutations
import com.santiagorodriguez.countaway.data.EventRevision
import com.santiagorodriguez.countaway.data.RetainedOperation
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.share.EventShareLauncher

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

    private var popup: PopupWindow? = null
    private var confirmation: AlertDialog? = null
    private var resumed = false
    private var sharing = false
    private val focus = EditorDialogFocus(activity)

    init { session.deletion.onChanged = ::consumeResult }

    fun show(event: CountdownEvent, anchor: View) {
        if (!resumed || sharing || session.deletion.running || popup != null || confirmation != null) return
        focus.capture(anchor)
        val popupParent = anchor.rootView as? ViewGroup ?: return
        val content = LayoutInflater.from(activity).inflate(R.layout.event_actions_popup, popupParent, false)
        content.contentDescription = activity.getString(R.string.event_actions_description, event.title)
        content.findViewById<ImageView>(R.id.eventActionsPopupIcon)
            .setImageResource(EventIconPresentation.drawableRes(event.icon))
        content.findViewById<TextView>(R.id.eventActionsPopupTitle).text = event.title

        val menu = PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            isClippingEnabled = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            elevation = dp(14).toFloat()
        }
        popup = menu
        var restoreAfterDismiss = true

        fun action(viewId: Int, block: () -> Unit) {
            content.findViewById<View>(viewId).setOnClickListener {
                if (!resumed || session.deletion.running) return@setOnClickListener
                restoreAfterDismiss = false
                menu.dismiss()
                block()
            }
        }
        action(R.id.home_event_widget) { addWidget(event) }
        action(R.id.home_event_edit) { edit(event) }
        action(R.id.home_event_duplicate) {
            activity.startActivity(
                Intent(activity, EditorActivity::class.java)
                    .putExtra(EditorActivity.EXTRA_DUPLICATE_EVENT_ID, event.id)
                    .putExtra(EditorActivity.EXTRA_SOURCE_REVISION, EventRevision.of(event)),
            )
        }
        action(R.id.home_event_share) {
            sharing = true
            EventShareLauncher.share(
                activity = activity, title = event.title, date = event.date, icon = event.icon,
                repeatRule = event.repeatRule, countMode = event.countMode,
                onFinished = { sharing = false },
            )
        }
        action(R.id.home_event_delete) { confirmDelete(event, anchor) }

        menu.setOnDismissListener {
            if (popup === menu) popup = null
            if (restoreAfterDismiss && confirmation == null) restoreFocus(anchor, event.id)
        }

        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val location = IntArray(2)
        anchor.getLocationInWindow(location)
        val root = anchor.rootView
        val margin = dp(12)
        val desiredX = if (anchor.layoutDirection == View.LAYOUT_DIRECTION_RTL) location[0]
            else location[0] + anchor.width - content.measuredWidth
        val maxX = (root.width - content.measuredWidth - margin).coerceAtLeast(margin)
        val x = desiredX.coerceIn(margin, maxX)
        val desiredY = location[1] + anchor.height / 2 - content.measuredHeight / 2
        val maxY = (root.height - content.measuredHeight - margin).coerceAtLeast(margin)
        val y = desiredY.coerceIn(margin, maxY)
        menu.showAtLocation(anchor, Gravity.TOP or Gravity.START, x, y)
        val firstAction = content.findViewById<View>(R.id.home_event_widget)
        // PopupWindow attachment timing differs across Android releases.
        // Request initial keyboard focus after the popup content is attached.
        firstAction.post {
            if (popup === menu && menu.isShowing) firstAction.requestFocus()
        }
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
        DialogPresentation.polish(dialog, DialogPresentation.PositiveTone.DANGER)
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

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

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
