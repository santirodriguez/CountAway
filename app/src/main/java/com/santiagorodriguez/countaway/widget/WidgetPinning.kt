package com.santiagorodriguez.countaway.widget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.santiagorodriguez.countaway.data.EventTransport
import com.santiagorodriguez.countaway.model.CountdownEvent
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

internal enum class WidgetFamily(val key: String, val providerClass: Class<*>, val configClass: Class<*>) {
    DAYS("days", CountdownWidgetProvider::class.java, WidgetConfigActivity::class.java),
    TIME("time", TimeWidgetProvider::class.java, TimeWidgetConfigActivity::class.java);

    fun provider(context: Context) = ComponentName(context, providerClass)

    fun accepts(event: CountdownEvent): Boolean =
        this == DAYS || (event.eventTime != null && event.eventZone != null)

    companion object {
        fun fromKey(key: String?): WidgetFamily? = if (key == null) DAYS else entries.firstOrNull { it.key == key }
    }
}

internal data class WidgetPinRequestSnapshot(
    val requestToken: String,
    val eventId: String,
    val style: WidgetStyleSelection,
    val family: WidgetFamily = WidgetFamily.DAYS,
    val onlyHours: Boolean = false,
) {
    fun callbackData(): String = buildString {
        append("countaway://widget/pin/")
        append(encode(requestToken))
        append("?event=")
        append(encode(EventTransport.wireId(eventId)))
        append("&appearance=")
        append(encode(style.appearance.storageKey))
        append("&background=")
        append(encode(style.background.storageKey))
        append("&family=")
        append(family.key)
        if (onlyHours) append("&hours=1")
    }

    companion object {
        fun create(
            eventId: String,
            style: WidgetStyleSelection,
            requestToken: String = UUID.randomUUID().toString(),
            family: WidgetFamily = WidgetFamily.DAYS,
            onlyHours: Boolean = false,
        ): WidgetPinRequestSnapshot = WidgetPinRequestSnapshot(
            requestToken = requestToken,
            eventId = EventTransport.wireId(eventId),
            style = style,
            family = family,
            onlyHours = onlyHours,
        )

        fun fromCallbackData(raw: String?): WidgetPinRequestSnapshot? {
            if (raw.isNullOrBlank()) return null
            val uri = runCatching { URI(raw) }.getOrNull() ?: return null
            if (uri.scheme != "countaway" || uri.host != "widget") return null

            val path = uri.rawPath.orEmpty()
                .split('/')
                .filter(String::isNotEmpty)
            if (path.size != 2 || path[0] != "pin") return null

            val requestToken = decode(path[1]).takeIf(String::isNotBlank) ?: return null
            val values = uri.rawQuery.orEmpty()
                .split('&')
                .mapNotNull { pair ->
                    val separator = pair.indexOf('=')
                    if (separator <= 0) return@mapNotNull null
                    decode(pair.substring(0, separator)) to decode(pair.substring(separator + 1))
                }
                .toMap()
            val eventId = values["event"]?.takeIf(String::isNotBlank) ?: return null
            val appearance = WidgetAppearance.entries.firstOrNull {
                it.storageKey == values["appearance"]
            } ?: return null
            val background = WidgetBackground.entries.firstOrNull {
                it.storageKey == values["background"]
            } ?: return null

            return WidgetPinRequestSnapshot(
                requestToken = requestToken,
                onlyHours = values["hours"] == "1",
                family = WidgetFamily.fromKey(values["family"]) ?: return null,
                eventId = eventId,
                style = WidgetStyleSelection(
                    appearance = appearance,
                    background = background,
                ),
            )
        }

        private fun encode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name())

        private fun decode(value: String): String =
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }
}

object WidgetPinning {
    fun request(
        activity: Activity,
        event: CountdownEvent,
        style: WidgetStyleSelection,
    ): Boolean = request(
        activity = activity,
        event = event,
        snapshot = WidgetPinRequestSnapshot.create(event.id, style),
    )

    internal fun request(
        activity: Activity,
        event: CountdownEvent,
        snapshot: WidgetPinRequestSnapshot,
    ): Boolean = requestInternal(activity, event, snapshot)

    internal fun callbackPendingIntent(
        context: Context,
        snapshot: WidgetPinRequestSnapshot,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        snapshot.requestToken.hashCode(),
        Intent(context, WidgetPinResultReceiver::class.java)
            .setData(Uri.parse(snapshot.callbackData())),
        // The launcher supplies EXTRA_APPWIDGET_ID through the success callback.
        PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
    )

    internal fun timePreview(
        context: Context,
        event: CountdownEvent,
        style: WidgetStyleSelection,
        onlyHours: Boolean = false,
    ): android.widget.RemoteViews = TimeWidgetRenderer.render(
        context, context, AppWidgetManager.INVALID_APPWIDGET_ID,
        WidgetConfiguration(event.id, style.appearance, style.background, onlyHours = onlyHours, dayClock = !onlyHours), event, null,
        com.santiagorodriguez.countaway.countdown.CountdownTime.snapshot(),
        150, 50, mutableMapOf(), interactive = false,
    )

    private fun requestInternal(
        activity: Activity,
        event: CountdownEvent,
        snapshot: WidgetPinRequestSnapshot,
    ): Boolean {
        val manager = AppWidgetManager.getInstance(activity)
        if (!snapshot.family.accepts(event) || !manager.isRequestPinAppWidgetSupported) return false

        val provider = snapshot.family.provider(activity)
        val preview = if (snapshot.family == WidgetFamily.TIME) timePreview(activity, event, snapshot.style, snapshot.onlyHours)
        else WidgetPreviewFactory.remoteViews(
            context = activity,
            event = event.copy(title = EventTransport.preview(event.title)),
            style = snapshot.style,
        )
        val extras = Bundle().apply {
            putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, preview)
        }
        val callback = callbackPendingIntent(activity, snapshot)

        val requested = runCatching {
            manager.requestPinAppWidget(provider, extras, callback)
        }.getOrDefault(false)
        if (!requested) callback.cancel()
        return requested
    }
}
