package com.santiagorodriguez.countaway.widget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.santiagorodriguez.countaway.model.CountdownEvent
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

internal data class WidgetPinRequestSnapshot(
    val requestToken: String,
    val eventId: String,
    val style: WidgetStyleSelection,
) {
    fun callbackData(): String = buildString {
        append("countaway://widget/pin/")
        append(encode(requestToken))
        append("?event=")
        append(encode(eventId))
        append("&appearance=")
        append(encode(style.appearance.storageKey))
        append("&background=")
        append(encode(style.background.storageKey))
    }

    companion object {
        fun create(
            eventId: String,
            style: WidgetStyleSelection,
            requestToken: String = UUID.randomUUID().toString(),
        ): WidgetPinRequestSnapshot = WidgetPinRequestSnapshot(
            requestToken = requestToken,
            eventId = eventId,
            style = style,
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
    fun request(activity: Activity): Boolean =
        requestInternal(activity = activity, event = null, snapshot = null)

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
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun requestInternal(
        activity: Activity,
        event: CountdownEvent?,
        snapshot: WidgetPinRequestSnapshot?,
    ): Boolean {
        val manager = AppWidgetManager.getInstance(activity)
        if (!manager.isRequestPinAppWidgetSupported) return false

        val provider = ComponentName(activity, CountdownWidgetProvider::class.java)
        val preview = if (event != null && snapshot != null) {
            WidgetPreviewFactory.remoteViews(
                context = activity,
                event = event,
                style = snapshot.style,
            )
        } else {
            WidgetPreviewFactory.remoteViews(activity)
        }
        val extras = Bundle().apply {
            putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, preview)
        }
        val callback = snapshot?.let { callbackPendingIntent(activity, it) }

        val requested = runCatching {
            manager.requestPinAppWidget(provider, extras, callback)
        }.getOrDefault(false)
        if (!requested) callback?.cancel()
        return requested
    }
}
