package com.egrmeister.siptimer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class CountdownAndInputTest {
    private val zone = ZoneId.of("Europe/Minsk")
    private val cfg = ReminderConfig(120, 8 * 60, 22 * 60, true)
    private fun at(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant()

    @Test fun neverShowsNegativeTime() {
        val st = ScheduleState(at("2026-10-08T10:00"), 3, null)
        val c = CountdownPresenter.present(cfg, st, true, false, at("2026-10-08T10:07"), zone)
        assertTrue(c is CountdownState.Passed)
        assertEquals(
            "Reminder time reached. Android may deliver it later.",
            CountdownPresenter.headline(c, zone, LocalDate.of(2026, 10, 8)),
        )
    }

    @Test fun activeHeadlineUsesMinutes() {
        val st = ScheduleState(at("2026-10-08T10:42"), 3, null)
        val c = CountdownPresenter.present(cfg, st, true, false, at("2026-10-08T10:00"), zone)
        assertEquals("Next sip in 42 min.", CountdownPresenter.headline(c, zone, LocalDate.of(2026, 10, 8)))
    }

    @Test fun outsideHoursHeadlineNamesDayAndTime() {
        val st = ScheduleState(at("2026-10-09T10:00"), 3, null)
        val c = CountdownPresenter.present(cfg, st, true, false, at("2026-10-08T23:00"), zone)
        assertEquals("Next reminder tomorrow at 10:00.", CountdownPresenter.headline(c, zone, LocalDate.of(2026, 10, 8)))
    }

    @Test fun stateMessages() {
        val st = ScheduleState(at("2026-10-08T12:00"), 1, null)
        val now = at("2026-10-08T10:00")
        val d = LocalDate.of(2026, 10, 8)
        assertEquals("Reminders are off.", CountdownPresenter.headline(CountdownPresenter.present(cfg.copy(enabled = false), st, true, false, now, zone), zone, d))
        assertEquals("Notifications are not allowed.", CountdownPresenter.headline(CountdownPresenter.present(cfg, st, false, false, now, zone), zone, d))
        assertEquals(
            "Your selected goal is reached. Reminders paused for today.",
            CountdownPresenter.headline(CountdownPresenter.present(cfg, st, true, true, now, zone), zone, d),
        )
    }

    @Test fun amountValidation() {
        assertTrue(Validation.parseAmount("250") is ParseResult.Ok)
        assertTrue(Validation.parseAmount("0") is ParseResult.Error)
        assertTrue(Validation.parseAmount("5001") is ParseResult.Error)
        assertTrue(Validation.parseAmount("12.5") is ParseResult.Error)
        assertTrue(Validation.parseAmount("") is ParseResult.Error)
        assertTrue(Validation.parseGoal("99") is ParseResult.Error)
        assertTrue(Validation.parseGoal("10000") is ParseResult.Ok)
    }

    @Test fun noFutureTimestamps() {
        val now = at("2026-10-08T10:00")
        assertTrue(Validation.entryTimestamp(LocalDate.of(2026, 10, 9), LocalTime.of(8, 0), zone, now) is ParseResult.Error)
        assertTrue(Validation.entryTimestamp(LocalDate.of(2026, 10, 8), LocalTime.of(10, 30), zone, now) is ParseResult.Error)
        assertTrue(Validation.entryTimestamp(LocalDate.of(2026, 10, 8), LocalTime.of(9, 30), zone, now) is ParseResult.Ok)
        assertTrue(Validation.entryTimestamp(LocalDate.of(2026, 9, 1), LocalTime.of(23, 0), zone, now) is ParseResult.Ok)
    }

    @Test fun effectiveDatedGoalsDoNotRewriteEarlierDays() {
        val h = GoalHistory(
            listOf(
                GoalChange(LocalDate.of(2026, 10, 1), 2_000),
                GoalChange(LocalDate.of(2026, 10, 9), 2_500),
            ),
        )
        assertNull(h.goalFor(LocalDate.of(2026, 9, 30)))
        assertEquals(2_000, h.goalFor(LocalDate.of(2026, 10, 8)))
        assertEquals(2_500, h.goalFor(LocalDate.of(2026, 10, 9)))
        assertFalse(GoalMath.reached(1_999, 2_000))
        assertTrue(GoalMath.reached(2_000, 2_000))
        assertEquals(125, GoalMath.percent(2_500, 2_000))
    }

    @Test fun activationGuardBlocksDoubleDispatchOnly() {
        val g = ActivationGuard(windowMs = 600)
        assertTrue(g.tryBegin("quick-250", 1_000))
        assertFalse(g.tryBegin("quick-250", 1_100)) // still in flight
        g.end("quick-250")
        assertFalse(g.tryBegin("quick-250", 1_200)) // same activation re-dispatched
        assertTrue(g.tryBegin("quick-250", 2_000)) // deliberate separate entry
        g.end("quick-250")
        assertTrue(g.tryBegin("quick-500", 2_010))
    }
}
