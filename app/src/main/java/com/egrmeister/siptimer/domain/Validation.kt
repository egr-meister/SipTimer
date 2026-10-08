package com.egrmeister.siptimer.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object Limits {
    const val MIN_AMOUNT_ML = 1
    const val MAX_AMOUNT_ML = 5_000
    const val MIN_GOAL_ML = 100
    const val MAX_GOAL_ML = 10_000
    const val MIN_INTERVAL = 30
    const val MAX_INTERVAL = 240
    const val INTERVAL_STEP = 15
    const val MIN_WINDOW_MINUTES = 2 * 60
    const val MAX_WINDOW_MINUTES = 18 * 60
    val DEFAULT_PRESETS = listOf(150, 250, 350, 500)
}

sealed interface ParseResult<out T> {
    data class Ok<T>(val value: T) : ParseResult<T>
    data class Error(val message: String) : ParseResult<Nothing>
}

object Validation {

    fun parseAmount(text: String): ParseResult<Int> = parseWhole(text, Limits.MIN_AMOUNT_ML, Limits.MAX_AMOUNT_ML, "Amount")

    fun parseGoal(text: String): ParseResult<Int> = parseWhole(text, Limits.MIN_GOAL_ML, Limits.MAX_GOAL_ML, "Goal")

    private fun parseWhole(text: String, min: Int, max: Int, label: String): ParseResult<Int> {
        val t = text.trim().replace(",", "").replace(" ", "")
        if (t.isEmpty()) return ParseResult.Error("Enter a whole number of mL.")
        if (!t.all { it.isDigit() }) return ParseResult.Error("Use whole milliliters only.")
        val value = t.toLongOrNull() ?: return ParseResult.Error("$label must be ${"%,d".format(min)}–${"%,d".format(max)} mL.")
        if (value < min || value > max) return ParseResult.Error("$label must be ${"%,d".format(min)}–${"%,d".format(max)} mL.")
        return ParseResult.Ok(value.toInt())
    }

    /** Validates schedule settings; returns null when valid, otherwise a calm explanation. */
    fun scheduleError(intervalMinutes: Int, startMinute: Int, endMinute: Int): String? {
        if (endMinute <= startMinute) return "End must be later than start (same day)."
        val duration = endMinute - startMinute
        if (duration < Limits.MIN_WINDOW_MINUTES || duration > Limits.MAX_WINDOW_MINUTES) {
            return "Active hours must span 2–18 hours."
        }
        if (intervalMinutes < Limits.MIN_INTERVAL || intervalMinutes > Limits.MAX_INTERVAL) {
            return "Interval must be 30–240 minutes."
        }
        if (intervalMinutes % Limits.INTERVAL_STEP != 0) return "Interval uses 15-minute steps."
        // The window end is excluded, so an interval equal to the window would never fit a reminder.
        if (intervalMinutes >= duration) return "Interval must be shorter than the active hours."
        return null
    }

    /** Timestamp for a logged local date/time; rejects future dates and future times. */
    fun entryTimestamp(date: LocalDate, time: LocalTime, zone: ZoneId, now: Instant): ParseResult<Instant> {
        val today = now.localDate(zone)
        if (date.isAfter(today)) return ParseResult.Error("Choose today or a past date.")
        val instant = ZonedDateTime.of(date, time, zone).toInstant()
        if (instant.isAfter(now)) return ParseResult.Error("This time is in the future.")
        return ParseResult.Ok(instant)
    }

    fun parsePresets(texts: List<String>): ParseResult<List<Int>> {
        val out = ArrayList<Int>(texts.size)
        for (t in texts) {
            when (val r = parseAmount(t)) {
                is ParseResult.Ok -> out += r.value
                is ParseResult.Error -> return r
            }
        }
        return ParseResult.Ok(out)
    }
}
