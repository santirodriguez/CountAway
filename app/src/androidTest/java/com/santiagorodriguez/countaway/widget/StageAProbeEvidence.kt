package com.santiagorodriguez.countaway.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.roundToInt

/** Shared test-only Android view measurements and real raster captures. */
internal object StageAProbeEvidence {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext

    fun context(fontScale: Float): Context = app.createConfigurationContext(
        Configuration(app.resources.configuration).apply { this.fontScale = fontScale })

    fun layout(root: View, context: Context, widthDp: Int, heightDp: Int) {
        val d = context.resources.displayMetrics.density
        val w = (widthDp * d).roundToInt().coerceAtLeast(1)
        val h = (heightDp * d).roundToInt().coerceAtLeast(1)
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, w, h)
    }

    fun capture(view: View, fileName: String) {
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1),
            view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            val file = File(app.filesDir, "layout-evidence/stage-a/" + fileName)
            file.parentFile?.mkdirs()
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }

    fun report(line: String) {
        val file = File(app.filesDir, "layout-evidence/stage-a/measurements.txt")
        file.parentFile?.mkdirs()
        file.appendText(line)
    }
}
