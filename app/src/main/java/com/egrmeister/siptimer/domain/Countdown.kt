package com.egrmeister.siptimer.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface CountdownState {
    data class Active(val dueAt: Instant, val minutesLeft: Long, val elapsedFraction: Float) : CountdownState
    data class Later(val dueAt: Instant) : CountdownState
    data object Off : CountdownState
    data object Blocked : CountdownState
    data object GoalReached : CountdownState
    data class Passed(val dueAt: Instant) : CountdownState
    data object Preparing : CountdownState
}

object CountdownPresenter {

    fun present(
        config: ReminderConfig?,
        state: ScheduleState,
        notificationsAllowed: Boolean,
        goalReachedToday: Boolean,
        now: Instant,
        zone: ZoneId,
    ): CountdownState {
        if (config == null || !config.enabled) return CountdownState.Off
        if (!notificationsAllowed) return CountdownState.Blocked
        val today = now.localDate(zone)
        if (goalReachedToday || state.pausedForDate == today) return CountdownState.GoalReached
        val due = state.dueAt ?: return CountdownState.Preparing
        if (!due.isAfter(now)) return CountdownState.Passed(due)
        val sameDay = due.localDate(zone) == today
        if (!sameDay || !config.window.contains(now, zone)) return CountdownState.Later(due)
        val seconds = Duration.between(now, due).seconds
        val minutesLeft = (seconds + 59) / 60 // ceil: never shows 0 while time remains
        val total = config.interval.seconds.toFloat()
        val elapsed = ((total - seconds) / total).coerceIn(0f, 1f)
        return CountdownState.Active(due, minutesLeft, elapsed)
    }

    fun formatRemaining(minutes: Long): String {
        val m = minutes.coerceAtLeast(0)
        return if (m < 60) "$m min" else "${m / 60} h ${"%02d".format(Locale.US, m % 60)} min"
    }

    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

    fun formatTime(instant: Instant, zone: ZoneId): String = timeFmt.format(instant.atZone(zone))

    /** "today at 10:00", "tomorrow at 10:00" or "Mon, Oct 12 at 10:00". */
    fun formatWhen(instant: Instant, zone: ZoneId, today: LocalDate): String {
        val date = instant.localDate(zone)
        val day = when (date) {
            today -> "today"
            today.plusDays(1) -> "tomorrow"
            else -> dateFmt.format(date)
        }
        return "$day at ${formatTime(instant, zone)}"
    }

    fun headline(state: CountdownState, zone: ZoneId, today: LocalDate): String = when (state) {
        is CountdownState.Active -> "Next sip in ${formatRemaining(state.minutesLeft)}."
        is CountdownState.Later -> "Next reminder ${formatWhen(state.dueAt, zone, today)}."
        CountdownState.Off -> "Reminders are off."
        CountdownState.Blocked -> "Notifications are not allowed."
        CountdownState.GoalReached -> "Your selected goal is reached. Reminders paused for today."
        is CountdownState.Passed -> "Reminder time reached. Android may deliver it later."
        CountdownState.Preparing -> "Preparing your next reminder…"
    }
}
