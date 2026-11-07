package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.content.Intent
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
