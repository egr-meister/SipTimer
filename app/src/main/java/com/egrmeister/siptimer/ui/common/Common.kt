package com.egrmeister.siptimer.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.egrmeister.siptimer.AppContainer
import com.egrmeister.siptimer.SipTimerApp
import com.egrmeister.siptimer.domain.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Emits now, then once at every minute boundary — only while collected (screen visible). */
fun minuteTicker(time: TimeSource): Flow<Instant> = flow {
    while (true) {
        val now = time.now()
        emit(now)
        val msIntoMinute = now.toEpochMilli() % 60_000L
        delay(60_000L - msIntoMinute + 50L)
    }
}

fun CreationExtras.container(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SipTimerApp).container

object Fmt {
    private val dayLong = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)
    private val dayShort = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    fun ml(value: Long): String = String.format(Locale.US, "%,d mL", value)
    fun ml(value: Int): String = ml(value.toLong())
    fun dayLong(d: LocalDate): String = dayLong.format(d)
    fun dayShort(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> dayShort.format(d)
    }
    fun time(t: LocalTime): String = time.format(t)
    fun minuteOfDay(m: Int): String = if (m >= 24 * 60) "24:00" else String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
}

/** Shows a confirmation snackbar with an Undo action; owned by the root so it survives navigation. */
fun interface UndoMessenger {
    fun show(message: String, onUndo: (suspend () -> Unit)?)
}

val LocalUndo = staticCompositionLocalOf<UndoMessenger> { UndoMessenger { _, _ -> } }

fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
