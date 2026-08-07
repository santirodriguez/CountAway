package com.santiagorodriguez.countaway.ui

import android.os.Build
import android.view.View
import kotlin.math.max

object InsetUtils {
    fun applySystemBarPadding(view: View) {
        val baseLeft = view.paddingLeft
        val baseTop = view.paddingTop
        val baseRight = view.paddingRight
        val baseBottom = view.paddingBottom

        view.setOnApplyWindowInsetsListener { target, insets ->
            var left = insets.systemWindowInsetLeft
            var top = insets.systemWindowInsetTop
            var right = insets.systemWindowInsetRight
            var bottom = insets.systemWindowInsetBottom

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                insets.displayCutout?.let { cutout ->
                    left = max(left, cutout.safeInsetLeft)
                    top = max(top, cutout.safeInsetTop)
                    right = max(right, cutout.safeInsetRight)
                    bottom = max(bottom, cutout.safeInsetBottom)
                }
            }

            target.setPadding(
                baseLeft + left,
                baseTop + top,
                baseRight + right,
                baseBottom + bottom,
            )
            insets
        }
        view.requestApplyInsets()
    }
}
