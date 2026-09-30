package com.santiagorodriguez.countaway.ui

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
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.withId
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
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class EditorPolishLayoutInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun actualControlsFitNarrowWidthAndLargeTextInEveryLanguage() {
        val preferences = context.getSharedPreferences("countaway_ui", Context.MODE_PRIVATE)
        val original = listOf("language", "theme").associateWith {
            preferences.contains(it) to preferences.getString(it, null)
        }
        val originalLocales = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales
        } else null
        val originalScale = Settings.System.getString(context.contentResolver, Settings.System.FONT_SCALE)
        val effectiveScale = context.resources.configuration.fontScale
        var captures = 0
        try {
            for (scale in listOf(1f, 1.3f, 2f)) {
                shell("settings put system font_scale $scale")
                waitForFontScale(scale)
                for (language in listOf("en", "es", "ca")) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        preferences.edit().remove("language").commit()
                        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language)
                    } else {
                        preferences.edit().putString("language", language).commit()
                    }
                    for (theme in listOf("LIGHT", "DARK", "SYSTEM")) {
                        preferences.edit().putString("theme", theme).commit()
                        ActivityScenario.launch<EditorActivity>(Intent(context, EditorActivity::class.java)).use { scenario ->
                            drain()
                            for (up in listOf(false, true)) {
                                if (up) EditorTestActions.chooseMode(scenario, true)
                                scenario.onActivity { activity ->
                                    assertEquals(scale, activity.resources.configuration.fontScale, 0.01f)
                                    assertEquals(language, activity.resources.configuration.locales[0].language)
                                    if (theme != "SYSTEM") assertEquals(
                                        if (theme == "DARK") Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO,
                                        activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                                    if (up) {
                                        val grid = activity.findViewById<GridLayout>(R.id.typeGrid)
                                        (0 until grid.childCount).map(grid::getChildAt)
                                            .single { it.tag == "up:TRAINING" }.performClick()
                                    }
                                    val root = activity.findViewById<ScrollView>(R.id.editorRoot)
                                    for (width in listOf(320, 360, 393, 411)) {
                                        measure(activity, root, width)
                                        verifyEditor(activity, width, scale, up)
                                        if (width == 393 || (width == 320 && theme != "SYSTEM")) {
                                            capture(root.getChildAt(0), "editor-$language-$scale-$theme-$width-$up")
                                            captures++
                                        }
                                    }
                                    // Exercise a narrow/wide reflow without replacing the focused view.
                                    val grid = activity.findViewById<GridLayout>(R.id.typeGrid)
                                    val focused = grid.getChildAt(0)
                                    focused.requestFocusFromTouch()
                                    measure(activity, root, 230)
                                    measure(activity, root, 411)
                                    assertSame(focused, grid.getChildAt(0))
                                    assertTrue(focused.hasFocus())
                                    // A shorter viewport models keyboard/landscape constraints: actions remain scrollable.
                                    measure(activity, root, 640, 360)
                                    root.scrollTo(0, root.getChildAt(0).height)
                                    assertTrue(root.scrollY + root.height >= root.getChildAt(0).height - root.paddingBottom)
                                }
                            }
                        }
                        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                            drain()
                            scenario.onActivity { activity ->
                                val root = activity.findViewById<View>(R.id.mainRoot)
                                measure(activity, root, 320)
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
                                val empty = activity.findViewById<ScrollView>(R.id.emptyState)
                                if (empty.visibility == View.VISIBLE) {
                                    assertTextFits(activity.findViewById(R.id.emptyDescription))
                                    val content = empty.getChildAt(0)
                                    empty.scrollTo(0, content.height)
                                    assertTrue(empty.scrollY + empty.height >= content.height - content.paddingBottom)
                                    empty.scrollTo(0, 0)
                                }
                                capture(root, "editor-header-$language-$scale-$theme")
                                captures++
                            }
                        }
                    }
                }
            }
            assertEquals(117, captures)
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && originalLocales != null) {
                context.getSystemService(LocaleManager::class.java).applicationLocales = originalLocales
            }
            preferences.edit().apply {
                original.forEach { (key, state) -> if (state.first) putString(key, state.second) else remove(key) }
            }.commit()
            if (originalScale == null) shell("settings delete system font_scale")
            else shell("settings put system font_scale $originalScale")
            waitForFontScale(effectiveScale)
        }
    }

    @Test fun modePickerCancellationRestoresItsLauncherWithoutChangingTheDraft() {
        ActivityScenario.launch<EditorActivity>(Intent(context, EditorActivity::class.java)).use { scenario ->
            drain()
            var before = ""
            scenario.onActivity {
                val button = it.findViewById<Button>(R.id.modeButton)
                before = button.text.toString()
                button.requestFocusFromTouch()
                button.performClick()
            }
            pressBack()
            onView(withId(R.id.modeButton)).check(matches(hasFocus()))
            scenario.onActivity { assertEquals(before, it.findViewById<Button>(R.id.modeButton).text.toString()) }
        }
    }

    private fun verifyEditor(activity: EditorActivity, width: Int, scale: Float, up: Boolean) {
        val grid = activity.findViewById<GridLayout>(R.id.typeGrid)
        assertEquals(9, grid.childCount)
        assertTrue(grid.columnCount == 3 || grid.columnCount == 1)
        if (scale == 1f && width >= 360) assertEquals(3, grid.columnCount)
        if (scale == 2f && width == 320) assertEquals(1, grid.columnCount)
        val cards = (0 until grid.childCount).map { grid.getChildAt(it) as TextView }
        for (card in cards) {
            assertTextFits(card)
            assertWholeWordsFit(card)
            assertTrue(card.width >= dp(activity, 48))
            assertTrue(card.height >= dp(activity, 48))
            val node = card.createAccessibilityNodeInfo()
            assertTrue(node.isCheckable)
            assertEquals(card.isSelected, node.isChecked)
        }
        assertTrue(cards.maxOf { it.width } - cards.minOf { it.width } <= 1)
        if (grid.columnCount == 3) {
            assertEquals(3, cards.map { it.top }.distinct().size)
            cards.chunked(3).forEach { row ->
                assertEquals(1, row.map { it.top }.distinct().size)
                assertEquals(1, row.map { it.bottom }.distinct().size)
            }
            assertTrue(grid.height <= dp(activity, if (scale == 1f) 290 else 350))
        }
        val title = activity.findViewById<View>(R.id.titleInput)
        val mode = activity.findViewById<Button>(R.id.modeButton)
        val date = activity.findViewById<Button>(R.id.dateButton)
        assertTrue(grid.bottom < title.top)
        assertTrue(title.bottom < mode.top)
        assertTrue(mode.bottom < date.top)
        for (view in listOf(mode, date)) {
            assertTextFits(view)
            assertWholeWordsFit(view)
            assertTrue(view.height >= dp(activity, 48))
            assertEquals(2, view.compoundDrawablesRelative.count { it != null })
        }
        if (up) {
            assertEquals(View.GONE, activity.findViewById<View>(R.id.repeatSection).visibility)
            assertEquals(View.GONE, activity.findViewById<View>(R.id.reminderSection).visibility)
            assertTextFits(activity.findViewById(R.id.editorScheduleSummary))
            assertTextFits(activity.findViewById(R.id.iconButton))
        } else {
            for (id in listOf(R.id.repeatSpinner, R.id.reminderSpinner)) {
                val spinner = activity.findViewById<Spinner>(id)
                val selected = spinner.selectedView as TextView
                assertTextFits(selected)
                assertWholeWordsFit(selected)
                assertTrue(spinner.height >= dp(activity, 48))
                assertEquals(2, selected.compoundDrawablesRelative.count { it != null })
            }
        }
    }

    private fun assertTextFits(view: TextView) {
        val layout = checkNotNull(view.layout) { "Unmeasured text: ${view.text}" }
        assertTrue("Clipped text height: ${view.text}",
            layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom + 1)
        for (line in 0 until layout.lineCount) {
            assertEquals("Ellipsized text: ${view.text}", 0, layout.getEllipsisCount(line))
            assertTrue("Clipped text width: ${view.text}", layout.getLineWidth(line) <= layout.width + 1)
        }
    }

    private fun assertWholeWordsFit(view: TextView) {
        val layout = checkNotNull(view.layout)
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            if (end > 0 && end < view.text.length) assertFalse("Word split inside ${view.text}",
                view.text[end - 1].isLetter() && view.text[end].isLetter())
        }
    }

    private fun measure(context: Context, root: View, width: Int, height: Int = 800) {
        repeat(3) {
            forceLayoutTree(root)
            root.measure(View.MeasureSpec.makeMeasureSpec(dp(context, width), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(context, height), View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        }
    }

    private fun forceLayoutTree(view: View) {
        view.forceLayout()
        if (view is ViewGroup) for (index in 0 until view.childCount) forceLayoutTree(view.getChildAt(index))
    }

    private fun capture(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(view.context.getColor(R.color.background))
            view.draw(canvas)
            File(context.filesDir, "layout-evidence/$name.png").apply {
                parentFile!!.mkdirs()
                outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally { bitmap.recycle() }
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
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

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
}
