package com.santiagorodriguez.countaway.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.santiagorodriguez.countaway.countdown.CountdownTemporalInvalidationPolicy
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.countdown.CountdownTimeSnapshot

internal class TemporalInvalidationController(
    private val context: Context,
    private val onInvalidated: (CountdownTimeSnapshot) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private val midnightRefresh = Runnable { invalidateNow() }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            invalidateNow()
        }
    }

    fun start(snapshot: CountdownTimeSnapshot = CountdownTime.snapshot()) {
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            registered = true
        }
        scheduleNextBoundary(snapshot)
    }

    fun stop() {
        handler.removeCallbacks(midnightRefresh)
        if (registered) {
            runCatching { context.unregisterReceiver(receiver) }
            registered = false
        }
    }

    private fun invalidateNow() {
        val snapshot = CountdownTime.snapshot()
        onInvalidated(snapshot)
        scheduleNextBoundary(snapshot)
    }

    private fun scheduleNextBoundary(snapshot: CountdownTimeSnapshot) {
        handler.removeCallbacks(midnightRefresh)
        handler.postDelayed(
            midnightRefresh,
            CountdownTemporalInvalidationPolicy.delayUntilNextDateBoundaryMillis(snapshot),
        )
    }
}
