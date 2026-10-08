package com.egrmeister.siptimer.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure scheduling arithmetic. Every function returns an instant that lies inside an active
 * window (start inclusive, end exclusive) — "the first eligible reminder".
 */
object ReminderPlanner {

    /** First reminder of [date]: one interval after that day's window start. */
    fun firstOfDay(date: LocalDate, config: ReminderConfig, zone: ZoneId): Instant =
        config.window.startOn(date, zone).plus(config.interval)

    /**
     * Applies the active-window boundary rule to a calculated candidate:
     * - before the window of its own date -> first reminder of that date;
     * - at or after the end -> first reminder of the next day's window;
     * - otherwise unchanged.
     */
    fun normalize(candidate: Instant, config: ReminderConfig, zone: ZoneId): Instant {
        val date = candidate.localDate(zone)
        val start = config.window.startOn(date, zone)
        val end = config.window.endOn(date, zone)
        return when {
            candidate.isBefore(start) -> firstOfDay(date, config, zone)
            !candidate.isBefore(end) -> firstOfDay(date.plusDays(1), config, zone)
            else -> candidate
        }
    }

    /**
     * A fresh interval measured from [now]:
     * inside the window -> now + interval; outside -> next window start + interval.
     * The boundary rule is applied to both.
     */
    fun freshFrom(now: Instant, config: ReminderConfig, zone: ZoneId): Instant {
        val date = now.localDate(zone)
        val start = config.window.startOn(date, zone)
        val end = config.window.endOn(date, zone)
        val candidate = when {
            now.isBefore(start) -> start.plus(config.interval)
            !now.isBefore(end) -> firstOfDay(date.plusDays(1), config, zone)
            else -> now.plus(config.interval)
        }
        return normalize(candidate, config, zone)
    }

    /** Skip: advance the scheduled occurrence by one interval, moving to the next day if needed. */
    fun skip(currentDue: Instant, config: ReminderConfig, zone: ZoneId): Instant =
        normalize(currentDue.plus(config.interval), config, zone)
}
