package com.egrmeister.siptimer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ActiveWindowTest {
    private val zone = ZoneId.of("Europe/Minsk")
    private val cfg = ReminderConfig(120, 8 * 60, 22 * 60, true)
    private fun at(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant()
    private fun local(i: java.time.Instant) = i.atZone(zone).toLocalDateTime().toString()

    @Test fun startIsIncludedEndIsExcluded() {
        assertTrue(cfg.window.contains(at("2026-10-08T08:00"), zone))
        assertTrue(cfg.window.contains(at("2026-10-08T21:59:59"), zone))
        assertFalse(cfg.window.contains(at("2026-10-08T22:00"), zone))
        assertFalse(cfg.window.contains(at("2026-10-08T07:59:59"), zone))
    }

    @Test fun firstReminderIsOneIntervalAfterWindowStart() {
        assertEquals("2026-10-08T10:00", local(ReminderPlanner.freshFrom(at("2026-10-08T06:30"), cfg, zone)))
    }

    @Test fun insideWindowIsNowPlusInterval() {
        assertEquals("2026-10-08T12:15", local(ReminderPlanner.freshFrom(at("2026-10-08T10:15"), cfg, zone)))
    }

    @Test fun reminderAtEndMovesToNextDayFirstReminder() {
        // 20:00 + 120 min = 22:00, which is the excluded end.
        assertEquals("2026-10-09T10:00", local(ReminderPlanner.freshFrom(at("2026-10-08T20:00"), cfg, zone)))
        assertEquals("2026-10-09T10:00", local(ReminderPlanner.freshFrom(at("2026-10-08T21:30"), cfg, zone)))
    }

    @Test fun afterEndSchedulesNextDay() {
        assertEquals("2026-10-09T10:00", local(ReminderPlanner.freshFrom(at("2026-10-08T23:10"), cfg, zone)))
    }

    @Test fun lastEligibleMinuteStaysToday() {
        assertEquals("2026-10-08T21:59", local(ReminderPlanner.freshFrom(at("2026-10-08T19:59"), cfg, zone)))
    }

    @Test fun validationRules() {
        assertEquals(null, Validation.scheduleError(120, 8 * 60, 22 * 60))
        assertTrue(Validation.scheduleError(120, 22 * 60, 8 * 60) != null) // end before start
        assertTrue(Validation.scheduleError(60, 8 * 60, 9 * 60 + 30) != null) // < 2 h
        assertTrue(Validation.scheduleError(60, 5 * 60, 23 * 60 + 30) != null) // > 18 h
        assertTrue(Validation.scheduleError(20, 8 * 60, 22 * 60) != null) // < 30
        assertTrue(Validation.scheduleError(250, 8 * 60, 22 * 60) != null) // > 240
        assertTrue(Validation.scheduleError(100, 8 * 60, 22 * 60) != null) // not 15-min step
        assertTrue(Validation.scheduleError(180, 8 * 60, 10 * 60) != null) // exceeds window
        assertEquals(null, Validation.scheduleError(105, 8 * 60, 10 * 60))
    }
}
