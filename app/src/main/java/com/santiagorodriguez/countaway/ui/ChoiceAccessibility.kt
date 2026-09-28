package com.santiagorodriguez.countaway.ui

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

internal object ChoiceAccessibility {
    fun apply(view: View) {
        view.isFocusable = true
        view.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.RadioButton::class.java.name
                info.isCheckable = true
                info.isChecked = host.isSelected
            }
        }
    }
}
