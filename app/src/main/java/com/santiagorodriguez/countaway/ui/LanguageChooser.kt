package com.santiagorodriguez.countaway.ui

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.Toast
import com.santiagorodriguez.countaway.R

internal class LanguageChooser(
    private val activity: Activity,
    private val button: Button,
) {
    private var dialog: AlertDialog? = null

    init {
        button.setOnClickListener { show() }
        button.setOnLongClickListener { show(); true }
        button.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(
                    AccessibilityNodeInfo.AccessibilityAction(
                        AccessibilityNodeInfo.ACTION_LONG_CLICK,
                        activity.getString(R.string.language_choose),
                    ),
                )
            }
        }
    }

    fun render() {
        val catalog = LanguageManager.supportedLanguages(activity)
        val current = LanguageManager.currentLanguageTag(activity)
        val language = catalog.language(current) ?: catalog.language(catalog.defaultTag)
        val flag = activity.getDrawable(language?.iconRes ?: R.drawable.ic_language)
            ?.apply { setBounds(0, 0, dp(24), dp(16)) }
        button.setCompoundDrawablesRelative(flag, null, null, null)
        val horizontalSpace = (button.layoutParams.width - dp(24)).coerceAtLeast(0)
        button.setPaddingRelative(horizontalSpace / 2, 0, horizontalSpace - horizontalSpace / 2, 0)
        button.compoundDrawablePadding = 0
        button.text = ""
        button.tooltipText = activity.getString(R.string.language_choose)
        val stateRes = if (LanguageManager.isFollowingSystem(activity)) {
            R.string.language_state_system
        } else {
            R.string.language_state_explicit
        }
        button.contentDescription = activity.getString(
            R.string.language_control_description,
            endonym(catalog, current),
            activity.getString(stateRes),
        )
    }

    private fun show() {
        if (dialog?.isShowing == true || activity.isFinishing || activity.isDestroyed) return
        val catalog = LanguageManager.supportedLanguages(activity)
        val current = LanguageManager.currentLanguageTag(activity)
        val tags = listOf(current) + catalog.tags.filter { it != current }
        val labels = tags.map { tag ->
            if (tag == current) activity.getString(R.string.language_in_use, endonym(catalog, tag))
            else endonym(catalog, tag)
        } + activity.getString(
            R.string.language_system_choice,
            endonym(catalog, LanguageManager.systemLanguageTag(activity)),
        )
        val followingSystem = LanguageManager.isFollowingSystem(activity)
        val restoreAccessibilityFocus = button.isAccessibilityFocused
        val picker = AlertDialog.Builder(activity)
            .setTitle(R.string.language_choose)
            .setSingleChoiceItems(labels.toTypedArray(), if (followingSystem) tags.size else 0) { selectedDialog, which ->
                selectedDialog.dismiss()
                runCatching {
                    if (which == tags.size) LanguageManager.useSystemLanguage(activity)
                    else LanguageManager.setLanguage(activity, tags[which])
                }.onFailure {
                    Toast.makeText(activity, R.string.language_change_failed, Toast.LENGTH_LONG).show()
                }
                render()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        picker.setOnDismissListener {
            dialog = null
            restoreButtonFocus(restoreAccessibilityFocus)
        }
        dialog = picker
        picker.show()
    }

    private fun restoreButtonFocus(restoreAccessibilityFocus: Boolean) {
        if (activity.isFinishing || activity.isDestroyed) return
        var restored = false
        fun restoreOnce() {
            if (restored || activity.isFinishing || activity.isDestroyed) return
            restored = true
            button.requestFocusFromTouch()
            if (restoreAccessibilityFocus) {
                button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
            }
        }
        if (button.hasWindowFocus()) {
            button.post { restoreOnce() }
            return
        }
        val observer = button.viewTreeObserver
        val listener = object : ViewTreeObserver.OnWindowFocusChangeListener {
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                if (!hasFocus) return
                val currentObserver = button.viewTreeObserver
                if (currentObserver.isAlive) currentObserver.removeOnWindowFocusChangeListener(this)
                button.post { restoreOnce() }
            }
        }
        observer.addOnWindowFocusChangeListener(listener)
        button.post {
            if (button.hasWindowFocus()) {
                val currentObserver = button.viewTreeObserver
                if (currentObserver.isAlive) currentObserver.removeOnWindowFocusChangeListener(listener)
                restoreOnce()
            }
        }
    }

    fun dismiss() {
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
    }

    private fun endonym(catalog: SupportedLanguageCatalog, tag: String): String =
        catalog.language(tag)?.endonym ?: catalog.language(catalog.defaultTag)?.endonym ?: tag

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
