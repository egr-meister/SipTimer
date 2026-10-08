package com.egrmeister.siptimer.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.egrmeister.siptimer.SipTimerApp
import kotlinx.coroutines.launch

/** Receives only our own explicit, immutable alarm PendingIntent (android:exported="false"). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER) return
        val generation = intent.getLongExtra(EXTRA_GENERATION, -1L)
        val container = (context.applicationContext as SipTimerApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                val result = container.engine.onAlarm(generation)
                Log.i(SipTimerApp.TAG, "Reminder callback gen=$generation -> $result")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMINDER = "com.egrmeister.siptimer.action.REMINDER"
        const val EXTRA_GENERATION = "generation"
    }
}

/** Reboot, app update and manual clock / time-zone changes. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as SipTimerApp).container
        val action = intent.action ?: return
        val pending = goAsync()
        container.appScope.launch {
            try {
                when (action) {
                    Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> container.engine.onClockChanged()
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> container.engine.recover()
                    else -> Unit
                }
                Log.i(SipTimerApp.TAG, "System event handled: $action")
            } finally {
                pending.finish()
            }
        }
    }
}
