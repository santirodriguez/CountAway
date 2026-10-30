package com.santiagorodriguez.countaway.ui

import android.app.Instrumentation
import android.graphics.Bitmap
import java.io.File

/** Store diagnostic emulator screenshots in CI render evidence, never Play listing assets. */
internal object DialogEvidenceCapture {
    fun save(instrumentation: Instrumentation, label: String) {
        instrumentation.waitForIdleSync()
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) {
            "Could not capture the $label dialog"
        }
        try {
            val directory = File(instrumentation.targetContext.filesDir, "layout-evidence").apply {
                check(isDirectory || mkdirs()) { "Could not create dialog evidence directory" }
            }
            val output = File(directory, "dialog-$label.png")
            output.outputStream().use {
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                    "Could not write $label dialog screenshot"
                }
            }
        } finally {
            screenshot.recycle()
        }
    }
}
