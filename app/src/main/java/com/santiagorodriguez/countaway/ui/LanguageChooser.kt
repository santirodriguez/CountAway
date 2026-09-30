package com.santiagorodriguez.countaway.ui

import android.app.Activity
import android.app.AlertDialog
import android.view.View
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
        button.setOnLongClickListener {
            show()
            true
        }
        button.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(AccessibilityNodeInfo.AccessibilityAction(
                    AccessibilityNodeInfo.ACTION_LONG_CLICK,
                    activity.getString(R.string.language_choose),
                ))
            }
        }
    }

    fun render() {
        val current = LanguageManager.currentLanguageTag(activity)
        val flagRes = when (current) {
            LanguageManager.SPANISH -> R.drawable.flag_ar
            LanguageManager.CATALAN -> R.drawable.flag_catalonia
            else -> R.drawable.flag_us
        }
        val flag = activity.getDrawable(flagRes)?.apply { setBounds(0, 0, dp(24), dp(16)) }
        button.setCompoundDrawablesRelative(flag, null, null, null)
        button.compoundDrawablePadding = dp(8)
        button.setText(R.string.language_label)
        val stateRes = if (LanguageManager.isFollowingSystem(activity)) {
            R.string.language_state_system
        } else {
            R.string.language_state_explicit
        }
        button.contentDescription = activity.getString(
            R.string.language_control_description,
            endonym(current),
            activity.getString(stateRes),
        )
    }

    private fun show() {
        if (dialog?.isShowing == true || activity.isFinishing || activity.isDestroyed) return
        val current = LanguageManager.currentLanguageTag(activity)
        val tags = listOf(current) + listOf(
            LanguageManager.ENGLISH, LanguageManager.SPANISH, LanguageManager.CATALAN,
        ).filter { it != current }
        val labels = tags.map { tag ->
            if (tag == current) activity.getString(R.string.language_in_use, endonym(tag)) else endonym(tag)
        } + activity.getString(R.string.language_system_choice,
            endonym(LanguageManager.systemLanguageTag(activity)))
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
            if (!activity.isFinishing && !activity.isDestroyed) {
                button.requestFocus()
                if (restoreAccessibilityFocus) {
                    button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
                }
            }
        }
        dialog = picker
        picker.show()
    }

    fun dismiss() {
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
    }

    private fun endonym(tag: String): String = when (tag) {
        LanguageManager.SPANISH -> "Español"
        LanguageManager.CATALAN -> "Català"
        else -> "English"
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
