package com.santiagorodriguez.countaway.ui

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.data.CountdownIo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class EditorPolishLayoutInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun actualControlsFitNarrowWidthAndLargeTextInEveryLanguage() {
        val preferences = context.getSharedPreferences("countaway_ui", Context.MODE_PRIVATE)
        val hadLanguage = preferences.contains("language")
        val originalLanguage = preferences.getString("language", null)
        val originalLocales = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales
        } else null
        val originalScale = Settings.System.getString(context.contentResolver, Settings.System.FONT_SCALE)
        val effectiveScale = context.resources.configuration.fontScale
        var captures = 0
        try {
            for (scale in listOf(1f, 2f)) {
                shell("settings put system font_scale $scale")
                waitForFontScale(scale)
                for (language in listOf("en", "es", "ca")) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        preferences.edit().remove("language").commit()
                        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language)
                    } else {
                        preferences.edit().putString("language", language).commit()
                    }
                    ActivityScenario.launch<EditorActivity>(Intent(context, EditorActivity::class.java)).use { scenario ->
                        drain()
                        scenario.onActivity { activity ->
                            assertEquals(scale, activity.resources.configuration.fontScale, 0.01f)
                            assertEquals(language, activity.resources.configuration.locales[0].language)
                            for (up in listOf(false, true)) {
                                activity.findViewById<Button>(if (up) R.id.countUpModeButton else R.id.modeButton).performClick()
                                val root = activity.findViewById<ScrollView>(R.id.editorRoot)
                                measure(activity, root)
                                val grid = activity.findViewById<GridLayout>(R.id.typeGrid)
                                assertEquals(9, grid.childCount)
                                assertEquals(if (scale >= 1.3f) 2 else 3, grid.columnCount)
                                for (index in 0 until grid.childCount) {
                                    val card = grid.getChildAt(index) as TextView
                                    assertTextFits(card)
                                    assertTrue(card.width >= dp(activity, 48))
                                    assertTrue(card.height >= dp(activity, 48))
                                    val node = card.createAccessibilityNodeInfo()
                                    assertTrue(node.isCheckable)
                                    assertEquals(card.isSelected, node.isChecked)
                                }
                                for (id in listOf(R.id.modeButton, R.id.countUpModeButton)) {
                                    val button = activity.findViewById<Button>(id)
                                    assertTextFits(button)
                                    assertTrue(button.height >= dp(activity, 48))
                                }
                                assertTrue(activity.findViewById<View>(R.id.modeSelector).bottom < grid.top)
                                capture(root.getChildAt(0), "experiment-editor-$language-$scale-$up")
                                captures++
                            }
                        }
                    }
                    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                        drain()
                        scenario.onActivity { activity ->
                            val root = activity.findViewById<View>(R.id.mainRoot)
                            measure(activity, root)
                            val button = activity.findViewById<Button>(R.id.languageButton)
                            assertEquals("", button.text.toString())
                            assertEquals(1, button.compoundDrawablesRelative.count { it != null })
                            assertTrue(button.contentDescription.isNotBlank())
                            assertEquals(activity.getString(R.string.language_choose), button.tooltipText)
                            assertEquals(dp(activity, 48), button.width)
                            assertEquals(dp(activity, 48), button.height)
                            val flag = button.compoundDrawablesRelative[0]!!
                            assertTrue(flag.bounds.width() <= button.width - button.paddingLeft - button.paddingRight)
                            assertTrue(flag.bounds.height() <= button.height - button.paddingTop - button.paddingBottom)
                            assertTrue(button.right < activity.findViewById<View>(R.id.themeButton).left)
                            capture(root, "experiment-header-$language-$scale")
                            captures++
                        }
                    }
                }
            }
            assertEquals(18, captures)
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && originalLocales != null) {
                context.getSystemService(LocaleManager::class.java).applicationLocales = originalLocales
            }
            preferences.edit().apply {
                if (hadLanguage) putString("language", originalLanguage) else remove("language")
            }.commit()
            // The CI device is disposable, but restore its exact incoming configuration as well.
            if (originalScale == null) shell("settings delete system font_scale")
            else shell("settings put system font_scale $originalScale")
            waitForFontScale(effectiveScale)
        }
    }

    private fun assertTextFits(view: TextView) {
        val layout = checkNotNull(view.layout)
        assertTrue("Clipped text height: ${view.text}",
            layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom + 1)
        for (line in 0 until layout.lineCount) {
            assertEquals("Ellipsized text: ${view.text}", 0, layout.getEllipsisCount(line))
            assertTrue("Clipped text width: ${view.text}", layout.getLineWidth(line) <= layout.width + 1)
        }
    }

    private fun measure(context: Context, root: View) {
        repeat(3) {
            root.measure(View.MeasureSpec.makeMeasureSpec(dp(context, 320), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(context, 800), View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        }
    }

    private fun capture(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            File(context.filesDir, "layout-evidence/$name.png").apply {
                parentFile!!.mkdirs()
                outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally { bitmap.recycle() }
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use {
            it.readBytes()
        }
    }

    private fun waitForFontScale(expected: Float) {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (abs(context.resources.configuration.fontScale - expected) < 0.01f) return
            SystemClock.sleep(50)
        }
        assertEquals(expected, context.resources.configuration.fontScale, 0.01f)
    }

    private fun drain() {
        val done = CountDownLatch(1)
        CountdownIo.submit({ Unit }) { done.countDown() }
        assertTrue(done.await(15, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
