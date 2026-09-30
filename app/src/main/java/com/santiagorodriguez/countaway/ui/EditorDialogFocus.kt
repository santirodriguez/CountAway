package com.santiagorodriguez.countaway.ui

import android.app.Activity
import android.view.View
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityNodeInfo

/** A dismissed window must return focus only after the Activity window is ready. */
internal class EditorDialogFocus(private val activity: Activity) {
    private var listener: ViewTreeObserver.OnWindowFocusChangeListener? = null
    private var observer: ViewTreeObserver? = null
    private var accessibilityFocus = false

    fun capture(view: View) {
        clear()
        accessibilityFocus = view.isAccessibilityFocused
    }

    fun restore(view: View) {
        clear()
        fun restoreNow() {
            if (activity.isFinishing || activity.isDestroyed || !view.isAttachedToWindow) return
            view.requestFocusFromTouch()
            if (accessibilityFocus) view.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
        }
        val root = activity.window.decorView
        if (root.hasWindowFocus()) {
            restoreNow()
            return
        }
        observer = root.viewTreeObserver
        listener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (focused) { clear(); restoreNow() }
        }
        observer?.addOnWindowFocusChangeListener(listener)
        if (root.hasWindowFocus()) { clear(); restoreNow() }
    }

    fun clear() {
        listener?.let { if (observer?.isAlive == true) observer?.removeOnWindowFocusChangeListener(it) }
        observer = null
        listener = null
    }
}
