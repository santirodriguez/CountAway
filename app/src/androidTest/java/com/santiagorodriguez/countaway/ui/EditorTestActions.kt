package com.santiagorodriguez.countaway.ui

import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.R
import org.hamcrest.Matchers.anything

internal object EditorTestActions {
    fun chooseMode(scenario: ActivityScenario<EditorActivity>, up: Boolean) {
        scenario.onActivity { it.findViewById<Button>(R.id.modeButton).performClick() }
        onData(anything()).inRoot(isDialog()).atPosition(if (up) 1 else 0).perform(click())
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}
