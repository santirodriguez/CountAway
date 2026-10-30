package com.santiagorodriguez.countaway.share

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.EventTransport
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.EventIcon
import com.santiagorodriguez.countaway.model.RepeatRule
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Shares the same localized card or text fallback from a saved Home event or an editor draft.
 */
internal object EventShareLauncher {
    fun share(
        activity: Activity,
        title: String,
        date: LocalDate,
        icon: EventIcon,
        repeatRule: RepeatRule,
        countMode: CountMode,
        eventTime: LocalTime? = null,
        eventZone: ZoneId? = null,
        onFinished: () -> Unit = {},
    ) {
        val now = CountdownTime.snapshot().now
        val today = now.toLocalDate()
        val text = ShareCardContentFactory.text(activity, title, date, repeatRule, today,
            countMode, eventTime, eventZone, now)
        if (title.length > 16_384 || text.length > 16_384) {
            // The complete text is a read-only private attachment, not a Binder extra.
            val appContext = activity.applicationContext
            CountdownIo.submit(
                task = { ShareTextStore.write(appContext, text) },
                onComplete = { result ->
                    if (activity.isFinishing || activity.isDestroyed) return@submit
                    onFinished()
                    val uri = result.getOrNull()
                    if (uri == null || !launch(activity, largeTextIntent(activity, text, uri))) {
                        Toast.makeText(activity, R.string.share_unavailable, Toast.LENGTH_LONG).show()
                    }
                },
            )
            return
        }
        val renderContext = ShareCardRenderer.captureContext(activity)
        val content = ShareCardContentFactory.create(
            context = renderContext, title = title, date = date, icon = icon,
            repeatRule = repeatRule, today = today, countMode = countMode,
            eventTime = eventTime, eventZone = eventZone, now = now,
        )
        val dark = ShareCardRenderer.resolveDark(activity)
        val appContext = activity.applicationContext

        CountdownIo.submit(
            task = {
                val bitmap = ShareCardRenderer.render(renderContext, content, dark)
                try { ShareImageStore.write(appContext, bitmap) } finally { bitmap.recycle() }
            },
            onComplete = { result ->
                if (activity.isFinishing || activity.isDestroyed) return@submit
                onFinished()
                val uri = result.getOrNull()
                if (uri != null && launch(activity, imageIntent(activity, title, text, uri))) return@submit
                if (!launch(activity, Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, text))) {
                    Toast.makeText(activity, R.string.share_unavailable, Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    private fun largeTextIntent(activity: Activity, text: String, uri: Uri) =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, EventTransport.preview(text))
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(activity.contentResolver, "CountAway", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun imageIntent(activity: Activity, title: String, text: String, uri: Uri) =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, title)
            clipData = ClipData.newUri(activity.contentResolver, title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun launch(activity: Activity, intent: Intent): Boolean = runCatching {
        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.action_share)))
        true
    }.getOrDefault(false)
}
