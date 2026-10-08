package com.egrmeister.siptimer.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A same-day active window [start, end): the start is included, the end is excluded.
 *
 * Daylight-saving rule (deterministic, documented in README):
 * window boundaries are resolved with [ZonedDateTime.of], i.e. a boundary that falls into a
 * spring-forward gap moves forward by the gap length, and a boundary inside a fall-back overlap
 * uses the earlier offset. Reminder intervals are elapsed real time (Instant + Duration).
 */
data class ActiveWindow(val startMinute: Int, val endMinute: Int) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY) { "start out of range" }
        require(endMinute in 1..MINUTES_PER_DAY) { "end out of range" }
    }

    val durationMinutes: Int get() = endMinute - startMinute

    fun startOn(date: LocalDate, zone: ZoneId): Instant = instantAt(date, startMinute, zone)

    fun endOn(date: LocalDate, zone: ZoneId): Instant = instantAt(date, endMinute, zone)

    /** True when [instant] lies inside the window of its own local date. */
    fun contains(instant: Instant, zone: ZoneId): Boolean {
        val date = instant.localDate(zone)
        return !instant.isBefore(startOn(date, zone)) && instant.isBefore(endOn(date, zone))
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        fun instantAt(date: LocalDate, minuteOfDay: Int, zone: ZoneId): Instant =
            if (minuteOfDay >= MINUTES_PER_DAY) {
                ZonedDateTime.of(date.plusDays(1), LocalTime.MIDNIGHT, zone).toInstant()
            } else {
                ZonedDateTime.of(date, LocalTime.of(minuteOfDay / 60, minuteOfDay % 60), zone).toInstant()
            }
    }
}

data class ReminderConfig(
    val intervalMinutes: Int,
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean,
) {
    val window: ActiveWindow get() = ActiveWindow(startMinute, endMinute)
    val interval: Duration get() = Duration.ofMinutes(intervalMinutes.toLong())

    companion object {
        /** Editable examples shown during setup — not recommendations. */
        val EXAMPLE = ReminderConfig(intervalMinutes = 120, startMinute = 8 * 60, endMinute = 22 * 60, enabled = false)
        const val EXAMPLE_GOAL_ML = 2_000
    }
}
