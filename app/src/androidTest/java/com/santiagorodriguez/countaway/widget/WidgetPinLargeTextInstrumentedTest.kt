package com.santiagorodriguez.countaway.widget

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class WidgetPinLargeTextInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun allPinChoicesFitNarrowAndLandscapeLayoutsAtLargeFontScales() {
        val preferences = context.getSharedPreferences("countaway_ui", Context.MODE_PRIVATE)
        val originalLanguage = preferences.getString("language", null)
        val originalTheme = preferences.getString("theme", null)
        val originalLocales = if (Build.VERSION.SDK_INT >= 33)
            context.getSystemService(LocaleManager::class.java).applicationLocales else null
        val originalScaleSetting = Settings.System.getString(context.contentResolver, Settings.System.FONT_SCALE)
        val originalEffectiveScale = context.resources.configuration.fontScale
        val repository = CountdownRepository(context)
        val originalEvents = (repository.loadResult() as CountdownLoadResult.Success).events
        val event = CountdownEvent("pin-measure-" + UUID.randomUUID(), "Widget pin measurement",
            LocalDate.of(2027, 5, 1), EventType.EVENT, createdAt = Instant.EPOCH)
        val failures = mutableListOf<String>()
        repository.save(originalEvents + event)
        try {
            for (scale in listOf(1f, 1.3f, 2f)) {
                shell("settings put system font_scale $scale")
                waitForFontScale(scale)
                for (language in listOf("en", "es", "ca")) for (theme in listOf("LIGHT", "DARK", "SYSTEM")) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        preferences.edit().remove("language").commit()
                        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language)
                    } else preferences.edit().putString("language", language).commit()
                    preferences.edit().putString("theme", theme).commit()
                    ActivityScenario.launch<WidgetPinSetupActivity>(Intent(context, WidgetPinSetupActivity::class.java)
                        .putExtra(WidgetPinSetupActivity.EXTRA_EVENT_ID, event.id)).use { scenario ->
                        drain()
                        scenario.onActivity { activity ->
                            for ((widthDp, heightDp) in listOf(320 to 640, 640 to 360)) {
                                val root = activity.findViewById<ScrollView>(R.id.widgetPinSetupRoot)
                                val density = activity.resources.displayMetrics.density
                                root.measure(View.MeasureSpec.makeMeasureSpec((widthDp * density).toInt(), View.MeasureSpec.EXACTLY),
                                    View.MeasureSpec.makeMeasureSpec((heightDp * density).toInt(), View.MeasureSpec.EXACTLY))
                                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                                val case = "pin-$language-$scale-$theme-${widthDp}x$heightDp"
                                val rows = activity.findViewById<LinearLayout>(R.id.widgetPinSetupBackgroundRows)
                                for (i in 0 until rows.childCount) {
                                    val row = rows.getChildAt(i) as LinearLayout
                                    for (j in 0 until row.childCount) {
                                        val cell = row.getChildAt(j) as? LinearLayout ?: continue
                                        if (cell.tag !is WidgetBackground) continue
                                        val label = cell.getChildAt(1) as TextView
                                        if (!textFits(label) || !cell.isFocusable || cell.contentDescription.isNullOrBlank())
                                            failures += "$case background=${cell.tag} lines=${label.layout?.lineCount} height=${label.height}"
                                    }
                                }
                                val appearances = activity.findViewById<RadioGroup>(R.id.widgetPinSetupAppearanceGroup)
                                for (i in 0 until appearances.childCount) {
                                    val button = appearances.getChildAt(i) as TextView
                                    if (!textFits(button) || button.height < (48 * density).toInt())
                                        failures += "$case appearance=$i lines=${button.layout?.lineCount} height=${button.height}"
                                }
                                root.scrollTo(0, root.getChildAt(0).height)
                                if (root.scrollY + root.height < root.getChildAt(0).height - root.paddingBottom)
                                    failures += "$case actions not reachable by scrolling"
                                if (widthDp == 320 && scale == 2f) {
                                    val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                                    root.draw(Canvas(bitmap))
                                    val file = File(context.filesDir, "layout-evidence/$case.png")
                                    file.parentFile!!.mkdirs()
                                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                    bitmap.recycle()
                                }
                            }
                        }
                    }
                }
            }
            assertTrue(failures.joinToString("\n"), failures.isEmpty())
        } finally {
            repository.save(originalEvents)
            if (Build.VERSION.SDK_INT >= 33 && originalLocales != null)
                context.getSystemService(LocaleManager::class.java).applicationLocales = originalLocales
            preferences.edit().apply {
                if (originalLanguage != null) putString("language", originalLanguage) else remove("language")
                if (originalTheme != null) putString("theme", originalTheme) else remove("theme")
            }.commit()
            if (originalScaleSetting == null) shell("settings delete system font_scale")
            else shell("settings put system font_scale $originalScaleSetting")
            waitForFontScale(originalEffectiveScale)
        }
    }

    private fun textFits(view: TextView): Boolean {
        val layout = view.layout ?: return false
        if (layout.lineCount <= 0 || layout.height > view.height - view.paddingTop - view.paddingBottom + 1) return false
        return layout.getLineEnd(layout.lineCount - 1) >= view.text.length &&
            (0 until layout.lineCount).all { layout.getEllipsisCount(it) == 0 }
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }

    private fun waitForFontScale(scale: Float) {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (abs(context.resources.configuration.fontScale - scale) < 0.01f) return
            SystemClock.sleep(50)
        }
        assertEquals(scale, context.resources.configuration.fontScale, 0.01f)
    }

    private fun drain() {
        val done = CountDownLatch(1)
        CountdownIo.submit({ Unit }) { done.countDown() }
        assertTrue(done.await(15, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }
}
