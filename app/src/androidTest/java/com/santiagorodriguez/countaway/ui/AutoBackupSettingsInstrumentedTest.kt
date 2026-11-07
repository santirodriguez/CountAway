package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.ScrollView
import java.io.File
import java.util.Locale
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoBackupSettingsInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun settingsRemainScrollableWithLargeTextInAllThreeLanguagesAndThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val scales = listOf(1.3f, 2f)
            for (language in listOf("en", "es", "ca")) for (dark in listOf(false, true)) {
                for (scale in scales) for (widthDp in listOf(320, 390)) {
                    val config = Configuration(context.resources.configuration).apply {
                        setLocale(Locale.forLanguageTag(language))
                        fontScale = scale
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                            if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    }
                    val themed = ContextThemeWrapper(
                        context.createConfigurationContext(config), context.applicationInfo.theme)
                    val root = LayoutInflater.from(themed)
                        .inflate(R.layout.activity_auto_backup_settings, null) as ScrollView
                    val density = themed.resources.displayMetrics.density
                    val w = (widthDp * density).toInt()
                    val h = (520 * density).toInt()
                    root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, w, h)
                    val child = root.getChildAt(0)
                    assertTrue("Settings content wider than viewport", child.measuredWidth <= w)
                    for (id in listOf(R.id.autoBackupDefault, R.id.autoBackupChoose)) {
                        val button = root.findViewById<Button>(id)
                        assertTrue("Target too short for touch", button.height >= (48 * density).toInt())
                        assertTrue("Target exceeds viewport width", button.width <= w - (40 * density).toInt())
                    }
                    assertTrue("Privacy notes must be scrollable", child.height > root.height)
                    root.scrollTo(0, child.height - root.height)
                    val image = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    try {
                        root.draw(Canvas(image))
                        val name = "auto-backup-$language-$dark-$scale-$widthDp.png"
                        val file = File(context.filesDir, "layout-evidence/$name")
                        file.parentFile!!.mkdirs()
                        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } finally { image.recycle() }
                }
            }
        }
    }

    @Test fun settingsExposeBothDestinationsButStayOffUntilUserOptsIn() {
        val prefs = context.getSharedPreferences("countaway_auto_backups", Context.MODE_PRIVATE)
        val original = prefs.getString("configuration_v1", null)
        prefs.edit().remove("configuration_v1").commit()
        try {
            ActivityScenario.launch<AutoBackupSettingsActivity>(
                Intent(context, AutoBackupSettingsActivity::class.java)
            ).use { scenario ->
                scenario.onActivity { screen ->
                    assertEquals(screen.getString(R.string.auto_backup_off),
                        screen.findViewById<TextView>(R.id.autoBackupStatus).text.toString())
                    assertTrue(screen.findViewById<Button>(R.id.autoBackupDefault).isEnabled)
                    assertTrue(screen.findViewById<Button>(R.id.autoBackupChoose).isEnabled)
                    assertEquals(View.GONE, screen.findViewById<View>(R.id.autoBackupNow).visibility)
                    assertEquals(View.GONE, screen.findViewById<View>(R.id.autoBackupDisable).visibility)
                    assertNotNull(screen.findViewById<View>(R.id.autoBackupBack).contentDescription)
                }
            }
        } finally {
            val edit = prefs.edit()
            if (original == null) edit.remove("configuration_v1")
            else edit.putString("configuration_v1", original)
            edit.commit()
        }
    }
}
