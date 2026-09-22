package com.santiagorodriguez.countaway.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetInstanceValidatorInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val expected = ComponentName(context, CountdownWidgetProvider::class.java)

    @Test
    fun invalidAndForeignWidgetIdsAreRejectedByProviderIdentityPolicy() {
        assertFalse(
            WidgetInstanceValidator.matchesProvider(
                AppWidgetManager.INVALID_APPWIDGET_ID,
                expected,
                expected,
            ),
        )
        assertFalse(
            WidgetInstanceValidator.matchesProvider(
                42,
                ComponentName(context.packageName, "other.Provider"),
                expected,
            ),
        )
        assertTrue(WidgetInstanceValidator.matchesProvider(42, expected, expected))
    }
}
