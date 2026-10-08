package com.egrmeister.siptimer.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.egrmeister.siptimer.domain.AlarmGateway
import java.time.Instant

/**
 * Inexact one-shot AlarmManager scheduling. No SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM:
 * Android may deliver later than the scheduled time, which the UI states explicitly.
 */
class AndroidAlarmScheduler(private val context: Context) : AlarmGateway {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(at: Instant, generation: Long) {
        val pi = pendingIntent(generation)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
    }

    override fun cancel() {
        alarmManager.cancel(pendingIntent(0L))
    }

    private fun pendingIntent(generation: Long): PendingIntent {
        // Explicit intent to our own non-exported receiver; same request code, so only one alarm exists.
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_REMINDER)
            .putExtra(ReminderReceiver.EXTRA_GENERATION, generation)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val REQUEST_CODE = 4101
    }
}
