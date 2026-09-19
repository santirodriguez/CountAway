package com.santiagorodriguez.countaway.widget

import android.content.Context
import android.widget.RemoteViews
import com.santiagorodriguez.countaway.R

internal object WidgetPreviewFactory {
    fun remoteViews(context: Context): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_countdown_preview)
}
