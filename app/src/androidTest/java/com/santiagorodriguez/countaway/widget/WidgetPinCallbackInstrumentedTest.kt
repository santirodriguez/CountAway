package com.santiagorodriguez.countaway.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WidgetPinCallbackInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun callbackIntentWidgetIdIsParsedDeterministically() {
        val callbackIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 41_002)
        assertEquals(41_002, WidgetPinResultReceiver.appWidgetIdFrom(callbackIntent))
        assertEquals(AppWidgetManager.INVALID_APPWIDGET_ID, WidgetPinResultReceiver.appWidgetIdFrom(Intent()))
    }

    @Test
    fun productionCallbackAcceptsFillInIdWithoutActivityAndKeepsSnapshotIdentity() = withFixture { _, preferences, event, id, snapshot ->
        val pending = WidgetPinning.callbackPendingIntent(context, snapshot)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) assertFalse(pending.isImmutable)
            send(pending, Intent().setData(Uri.parse("countaway://wrong/snapshot"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            val expected = WidgetConfiguration(event.id, snapshot.style.appearance,
                snapshot.style.background, WidgetEventSelection.FIXED)
            assertEquals(expected, preferences.get(id))
            send(pending, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            assertEquals(expected, preferences.get(id))
            preferences.save(id, "conflicting-event", WidgetAppearance.LIGHT,
                WidgetBackground.CLASSIC, WidgetEventSelection.FIXED)
            send(pending, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            assertEquals("conflicting-event", preferences.get(id)?.eventId)
        } finally {
            pending.cancel()
        }
    }

    @Test
    fun lateProductionCallbackAfterFallbackKeepsTheSameConfiguration() = withFixture { _, preferences, _, id, snapshot ->
        assertEquals(WidgetPinFinalizeResult.APPLIED, WidgetPinFinalizer.finalize(context, id, snapshot))
        val expected = preferences.get(id)
        val pending = WidgetPinning.callbackPendingIntent(context, snapshot)
        try {
            send(pending, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            assertEquals(expected, preferences.get(id))
        } finally {
            pending.cancel()
        }
    }

    @Test
    fun missingInvalidDeletedAndMalformedCallbacksDoNotConfigureAWidget() = withFixture { repository, preferences, event, id, snapshot ->
        val pending = WidgetPinning.callbackPendingIntent(context, snapshot)
        val malformed = WidgetPinning.callbackPendingIntent(context,
            snapshot.copy(requestToken = UUID.randomUUID().toString(), eventId = ""))
        try {
            send(pending, Intent())
            send(pending, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
            assertNull(preferences.get(id))
            send(malformed, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            assertNull(preferences.get(id))
            val current = (repository.loadResult() as CountdownLoadResult.Success).events
            repository.save(current.filterNot { it.id == event.id })
            send(pending, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            assertNull(preferences.get(id))
        } finally {
            pending.cancel()
            malformed.cancel()
        }
    }

    private fun send(pending: PendingIntent, fillIn: Intent) {
        val finished = CountDownLatch(1)
        pending.send(context, 0, fillIn, { _, _, _, _, _ -> finished.countDown() }, Handler(Looper.getMainLooper()))
        assertTrue("Callback broadcast did not finish", finished.await(10, TimeUnit.SECONDS))
        drainIo()
    }

    private fun drainIo() {
        val drained = CountDownLatch(1)
        CountdownIo.execute { drained.countDown() }
        assertTrue("Callback persistence did not finish", drained.await(10, TimeUnit.SECONDS))
    }

    private fun withFixture(block: (CountdownRepository, WidgetPreferences, CountdownEvent, Int, WidgetPinRequestSnapshot) -> Unit) {
        drainIo()
        val files = listOf("countaways.json", "countaways.json.bak", "countaways.json.new")
            .associate { name -> File(context.filesDir, name).let { it to if (it.isFile) it.readBytes() else null } }
        val repository = CountdownRepository(context)
        val original = (repository.loadResult() as CountdownLoadResult.Success).events
        val preferences = WidgetPreferences(context)
        val id = (1_900_000_000..1_900_000_100).first { preferences.get(it) == null }
        val defaults = context.getSharedPreferences("countaway_widget_defaults", Context.MODE_PRIVATE)
        val defaultValues = listOf("appearance", "background").associateWith { defaults.getString(it, null) }
        val event = CountdownEvent(UUID.randomUUID().toString(), "Callback event", LocalDate.of(2027, 1, 1),
            EventType.EVENT, createdAt = Instant.EPOCH)
        val snapshot = WidgetPinRequestSnapshot.create(event.id,
            WidgetStyleSelection(WidgetAppearance.DARK, WidgetBackground.MONOGRAM))
        try {
            repository.save(original + event)
            block(repository, preferences, event, id, snapshot)
        } finally {
            drainIo()
            preferences.remove(id)
            defaults.edit().apply {
                defaultValues.forEach { (key, value) -> if (value == null) remove(key) else putString(key, value) }
            }.commit()
            files.forEach { (file, bytes) -> if (bytes == null) file.delete() else file.writeBytes(bytes) }
        }
    }
}
