package com.santiagorodriguez.countaway.widget

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WidgetPinFinalizerInstrumentedTest {
    @Test
    fun validSnapshotPersistsBeforeProviderVisibilityIsAvailable() {
        withIsolatedContext { context ->
            val appWidgetId = 841_001
            val repository = CountdownRepository(context)
            val widgetPreferences = WidgetPreferences(context)
            val defaultsPreferences = WidgetDefaultsPreferences(context)
            val originalDefaults = defaultsPreferences.get()
            val event = event("event-a")
            val style = WidgetStyleSelection(
                appearance = WidgetAppearance.DARK,
                background = WidgetBackground.PULSE,
            )
            repository.save(listOf(event))
            widgetPreferences.remove(appWidgetId)

            try {
                assertEquals(
                    WidgetPinFinalizeResult.APPLIED,
                    WidgetPinFinalizer.finalize(
                        context = context,
                        appWidgetId = appWidgetId,
                        snapshot = WidgetPinRequestSnapshot.create(
                            eventId = event.id,
                            style = style,
                            requestToken = "not-yet-visible-widget",
                        ),
                    ),
                )

                assertEquals(
                    WidgetConfiguration(
                        eventId = event.id,
                        appearance = style.appearance,
                        background = style.background,
                        eventSelection = WidgetEventSelection.FIXED,
                    ),
                    widgetPreferences.get(appWidgetId),
                )
                assertEquals(style, defaultsPreferences.get())
            } finally {
                widgetPreferences.remove(appWidgetId)
                defaultsPreferences.save(originalDefaults)
                WidgetUpdateScheduler.cancel(context)
            }
        }
    }

    @Test
    fun deletedEventDoesNotCreateWidgetConfigurationOrChangeLastUsedStyle() {
        withIsolatedContext { context ->
            val appWidgetId = 841_002
            val widgetPreferences = WidgetPreferences(context)
            val defaultsPreferences = WidgetDefaultsPreferences(context)
            val originalDefaults = defaultsPreferences.get()
            widgetPreferences.remove(appWidgetId)

            try {
                assertEquals(
                    WidgetPinFinalizeResult.EVENT_MISSING,
                    WidgetPinFinalizer.finalize(
                        context = context,
                        appWidgetId = appWidgetId,
                        snapshot = WidgetPinRequestSnapshot.create(
                            eventId = "missing-event",
                            style = WidgetStyleSelection(
                                appearance = WidgetAppearance.LIGHT,
                                background = WidgetBackground.FOREST,
                            ),
                            requestToken = "deleted-event",
                        ),
                    ),
                )

                assertNull(widgetPreferences.get(appWidgetId))
                assertEquals(originalDefaults, defaultsPreferences.get())
            } finally {
                widgetPreferences.remove(appWidgetId)
                defaultsPreferences.save(originalDefaults)
                WidgetUpdateScheduler.cancel(context)
            }
        }
    }

    private fun withIsolatedContext(block: (Context) -> Unit) {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val isolatedFilesDir = File(
            targetContext.cacheDir,
            "widget-pin-finalizer-" + UUID.randomUUID(),
        )
        val context = object : ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = isolatedFilesDir
        }

        try {
            block(context)
        } finally {
            isolatedFilesDir.deleteRecursively()
        }
    }

    private fun event(id: String): CountdownEvent = CountdownEvent(
        id = id,
        title = "Pinned countdown",
        date = LocalDate.of(2026, 12, 24),
        type = EventType.EVENT,
        createdAt = Instant.parse("2026-09-23T00:00:00Z"),
    )
}
