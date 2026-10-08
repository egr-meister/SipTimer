package com.egrmeister.siptimer.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderEngineTest {
    private fun t(s: String) = LocalDateTime.parse(s)

    @Test fun enablingInsideWindowSchedulesNowPlusInterval() = runTest {
        val r = Rig(t("2026-10-08T09:10"))
        r.engine.onConfigChanged()
        assertEquals(t("2026-10-08T11:10"), r.local(r.store.st.dueAt))
        assertEquals(r.store.st.dueAt, r.alarms.scheduledAt)
        assertEquals(r.store.st.generation, r.alarms.scheduledGeneration)
    }

    @Test fun enablingOutsideWindowUsesNextWindowStart() = runTest {
        val r = Rig(t("2026-10-08T23:00"))
        r.engine.onConfigChanged()
        assertEquals(t("2026-10-09T10:00"), r.local(r.store.st.dueAt))
    }

    @Test fun currentTimeLoggingRestartsInterval() = runTest {
        val r = Rig(t("2026-10-08T09:00"))
        r.engine.onConfigChanged()
        r.time.set(t("2026-10-08T10:30"))
        r.progress.add(r.today, 250)
        r.engine.onWaterLoggedNow()
        assertEquals(t("2026-10-08T12:30"), r.local(r.store.st.dueAt))
        assertTrue(r.notifications.dismissed > 0)
    }

    @Test fun historicalEntriesAndEditsDoNotRestartTimer() = runTest {
        val r = Rig(t("2026-10-08T09:00"))
        r.engine.onConfigChanged()
        val before = r.store.st
        r.time.set(t("2026-10-08T10:30"))
        r.progress.add(r.today.minusDays(1), 500) // backdated entry
        r.engine.onDataChanged()
        r.progress.add(r.today, 300) // edit/backdated today, still below goal
        r.engine.onDataChanged()
        assertEquals(before, r.store.st)
    }

    @Test fun skipAdvancesByOneIntervalAndRecordsEvent() = runTest {
        val r = Rig(t("2026-10-08T09:00"))
        r.engine.onConfigChanged()
        val st = r.store.st
        val result = r.engine.skip(st.dueAt!!, st.generation)
        assertTrue(result is SkipResult.Skipped)
        assertEquals(t("2026-10-08T13:00"), r.local(r.store.st.dueAt))
        assertEquals(listOf(st.dueAt), r.progress.skips)
        assertEquals(0L, r.progress.totals[r.today] ?: 0L) // no water added
    }

    @Test fun duplicateSkipActivationIsProcessedOnce() = runTest {
        val r = Rig(t("2026-10-08T09:00"))
        r.engine.onConfigChanged()
        val st = r.store.st
        r.engine.skip(st.dueAt!!, st.generation)
        val second = r.engine.skip(st.dueAt!!, st.generation)
        assertTrue(second is SkipResult.Ignored)
        assertEquals(1, r.progress.skips.size)
        // A deliberate second skip of the new occurrence is allowed.
        val now = r.store.st
        assertTrue(r.engine.skip(now.dueAt!!, now.generation) is SkipResult.Skipped)
        assertEquals(t("2026-10-08T15:00"), r.local(r.store.st.dueAt))
    }

    @Test fun skipMovesToNextDayWhenNeeded() = runTest {
        val r = Rig(t("2026-10-08T19:00"))
        r.engine.onConfigChanged() // 21:00
        val st = r.store.st
        r.engine.skip(st.dueAt!!, st.generation)
        assertEquals(t("2026-10-09T10:00"), r.local(r.store.st.dueAt))
    }

    @Test fun skipDisabledWhenOffBlockedOrPaused() = runTest {
        val r = Rig(t("2026-10-08T09:00"))
        r.engine.onConfigChanged()
        r.notifications.allowed = false
        val st = r.store.st
        assertTrue(r.engine.skip(st.dueAt!!, st.generation) is SkipResult.Ignored)
        r.notifications.allowed = true
        r.store.cfg = r.store.cfg!!.copy(enabled = false)
        assertTrue(r.engine.skip(st.dueAt!!, st.generation) is SkipResult.Ignored)
    }

    @Test fun goalReachedPausesAndResumesNextDay() = runTest {
        val r = Rig(t("2026-10-08T09:00"))
        r.engine.onConfigChanged()
        r.time.set(t("2026-10-08T15:00"))
        r.progress.add(r.today, 2_000)
        r.engine.onWaterLoggedNow()
        assertEquals(r.today, r.store.st.pausedForDate)
        assertEquals(t("2026-10-09T10:00"), r.local(r.store.st.dueAt))
        assertTrue(r.notifications.dismissed > 0)
        val st = r.store.st
        assertTrue(r.engine.skip(st.dueAt!!, st.generation) is SkipResult.Ignored)
    }

    @Test fun droppingBelowGoalSchedulesFreshIntervalFromNow() = runTest {
        val r = Rig(t("2026-10-08T09:00"))
        r.engine.onConfigChanged()
        r.time.set(t("2026-10-08T15:00"))
        r.progress.add(r.today, 2_100)
        r.engine.onWaterLoggedNow()
        r.time.set(t("2026-10-08T16:20"))
        r.progress.add(r.today, -500) // deletion / Undo
        r.engine.onDataChanged()
        assertNull(r.store.st.pausedForDate)
        assertEquals(t("2026-10-08T18:20"), r.local(r.store.st.dueAt))
    }

    @Test fun staleAndObsoleteCallbacksAreRejected() = runTest {
        val r = Rig(t("2026-10-08T09:00"))
        r.engine.onConfigChanged()
        val oldGen = r.store.st.generation
        r.time.set(t("2026-10-08T10:00"))
        r.engine.onWaterLoggedNow() // new generation
        r.time.set(t("2026-10-08T11:05"))
        assertEquals(DeliveryResult.Obsolete, r.engine.onAlarm(oldGen))
        assertEquals(0, r.notifications.posted)
    }

    @Test fun duplicateDeliveryPostsOnce() = runTest {
        val r = Rig(t("2026-10-08T09:00"))
        r.engine.onConfigChanged()
        val gen = r.store.st.generation
        r.time.set(t("2026-10-08T11:03"))
        assertEquals(DeliveryResult.Posted, r.engine.onAlarm(gen))
        assertEquals(DeliveryResult.Obsolete, r.engine.onAlarm(gen))
        assertEquals(1, r.notifications.posted)
        // Next one interval after processing time; no replay of missed intervals.
        assertEquals(t("2026-10-08T13:03"), r.local(r.store.st.dueAt))
    }

    @Test fun previousDayDeliveryIsRejected() = runTest {
        val r = Rig(t("2026-10-08T19:00"))
        r.engine.onConfigChanged() // due 21:00
        val gen = r.store.st.generation
        r.time.set(t("2026-10-09T08:30")) // device was asleep all night
        val result = r.engine.onAlarm(gen)
        assertTrue(result is DeliveryResult.Rejected)
        assertEquals(0, r.notifications.posted)
        // Processed at 08:30 (inside today's window): next one interval after processing time.
        assertEquals(t("2026-10-09T10:30"), r.local(r.store.st.dueAt))
    }

    @Test fun outsideWindowDeliveryIsRejected() = runTest {
        val r = Rig(t("2026-10-08T19:30"))
        r.engine.onConfigChanged() // 21:30
        val gen = r.store.st.generation
        r.time.set(t("2026-10-08T22:10"))
        assertTrue(r.engine.onAlarm(gen) is DeliveryResult.Rejected)
        assertEquals(0, r.notifications.posted)
    }

    @Test fun deliveryWhenGoalReachedPauses() = runTest {
        val r = Rig(t("2026-10-08T09:00"))
        r.engine.onConfigChanged()
        val gen = r.store.st.generation
        r.progress.add(r.today, 2_500)
        r.time.set(t("2026-10-08T11:00"))
        assertEquals(DeliveryResult.PausedForGoal, r.engine.onAlarm(gen))
        assertEquals(0, r.notifications.posted)
    }

    @Test fun permissionDenialKeepsScheduleButRegistersNoAlarm() = runTest {
        val r = Rig(t("2026-10-08T09:00"))
        r.notifications.allowed = false
        r.engine.onConfigChanged()
        assertTrue(r.store.st.dueAt != null)
        assertNull(r.alarms.scheduledAt)
        val countdown = CountdownPresenter.present(r.store.cfg, r.store.st, false, false, r.time.now(), r.time.zone())
        assertEquals(CountdownState.Blocked, countdown)
        // Regaining permission re-registers the saved schedule without restarting it.
        val due = r.store.st.dueAt
        r.notifications.allowed = true
        r.engine.recover()
        assertEquals(due, r.alarms.scheduledAt)
    }

    @Test fun recoveryRestoresWithoutRestarting() = runTest {
        val r = Rig(t("2026-10-08T09:00"))
        r.engine.onConfigChanged()
        val saved = r.store.st
        r.alarms.cancel() // process death between save and alarm registration
        r.time.set(t("2026-10-08T10:00"))
        r.engine.recover()
        assertEquals(saved, r.store.st)
        assertEquals(saved.dueAt, r.alarms.scheduledAt)
        val c = CountdownPresenter.present(r.store.cfg, r.store.st, true, false, r.time.now(), r.time.zone())
        assertEquals(60L, (c as CountdownState.Active).minutesLeft)
    }

    @Test fun recoveryWithMissingStateComputesFreshSchedule() = runTest {
        val r = Rig(t("2026-10-08T09:00"))
        r.engine.recover()
        assertEquals(t("2026-10-08T11:00"), r.local(r.store.st.dueAt))
    }

    @Test fun recoveryAfterEarlierDayDueRecalculates() = runTest {
        val r = Rig(t("2026-10-08T19:00"))
        r.engine.onConfigChanged() // 21:00
        r.time.set(t("2026-10-09T12:00"))
        r.engine.recover()
        assertEquals(t("2026-10-09T14:00"), r.local(r.store.st.dueAt))
    }

    @Test fun midnightAfterGoalPauseShowsNewDay() = runTest {
        val r = Rig(t("2026-10-08T20:00"))
        r.engine.onConfigChanged()
        r.progress.add(r.today, 2_000)
        r.engine.onDataChanged()
        val paused = r.store.st
        assertEquals(r.today, paused.pausedForDate)
        r.time.set(t("2026-10-09T00:01"))
        val c = CountdownPresenter.present(r.store.cfg, r.store.st, true, false, r.time.now(), r.time.zone())
        assertTrue(c is CountdownState.Later)
        r.time.set(t("2026-10-09T09:00"))
        val c2 = CountdownPresenter.present(r.store.cfg, r.store.st, true, false, r.time.now(), r.time.zone())
        assertEquals(60L, (c2 as CountdownState.Active).minutesLeft)
    }

    @Test fun clockChangeRecalculatesAndInvalidatesOldCallback() = runTest {
        val r = Rig(t("2026-10-08T09:00"))
        r.engine.onConfigChanged()
        val oldGen = r.store.st.generation
        r.time.set(t("2026-10-08T15:40")) // user moved the clock forward
        r.engine.onClockChanged()
        assertEquals(t("2026-10-08T17:40"), r.local(r.store.st.dueAt))
        assertNotEquals(oldGen, r.store.st.generation)
        assertEquals(DeliveryResult.Obsolete, r.engine.onAlarm(oldGen))
    }

    @Test fun timeZoneChangeUsesNewZoneForWindows() = runTest {
        val r = Rig(t("2026-10-08T09:00"))
        r.engine.onConfigChanged()
        // Travel: same instant, but local time in Tokyo is 15:00.
        r.time.zoneId = ZoneId.of("Asia/Tokyo")
        r.engine.onClockChanged()
        assertEquals(t("2026-10-08T17:00"), r.local(r.store.st.dueAt))
    }

    @Test fun springForwardGapIsDeterministic() = runTest {
        // Europe/Berlin 2026-03-29 02:00 -> 03:00. Window 02:30–20:00 starts inside the gap -> 03:30.
        val zone = ZoneId.of("Europe/Berlin")
        val cfg = ReminderConfig(60, 2 * 60 + 30, 20 * 60, true)
        val r = Rig(t("2026-03-29T01:00"), cfg, zone)
        r.engine.onConfigChanged()
        assertEquals(t("2026-03-29T04:30"), r.local(r.store.st.dueAt))
    }

    @Test fun fallBackIntervalIsElapsedTime() = runTest {
        // Europe/Berlin 2026-10-25 03:00 -> 02:00. 01:30 + 120 elapsed minutes = 02:30 (second occurrence).
        val zone = ZoneId.of("Europe/Berlin")
        val cfg = ReminderConfig(120, 60, 20 * 60, true)
        val r = Rig(t("2026-10-25T01:30"), cfg, zone)
        r.engine.onConfigChanged()
        val due = r.store.st.dueAt!!
        assertEquals(120L, java.time.Duration.between(r.time.now(), due).toMinutes())
        assertEquals("02:30", due.atZone(zone).toLocalTime().toString())
    }

    @Test fun disablingCancelsEverything() = runTest {
        val r = Rig(t("2026-10-08T09:00"))
        r.engine.onConfigChanged()
        r.store.cfg = r.store.cfg!!.copy(enabled = false)
        r.engine.onConfigChanged()
        assertNull(r.store.st.dueAt)
        assertNull(r.alarms.scheduledAt)
        assertEquals(CountdownState.Off, CountdownPresenter.present(r.store.cfg, r.store.st, true, false, r.time.now(), r.time.zone()))
    }

    @Test fun settingsChangeRecalculatesFromNow() = runTest {
        val r = Rig(t("2026-10-08T09:00"))
        r.engine.onConfigChanged()
        r.time.set(t("2026-10-08T09:40"))
        r.store.cfg = r.store.cfg!!.copy(intervalMinutes = 45)
        r.engine.onConfigChanged()
        assertEquals(t("2026-10-08T10:25"), r.local(r.store.st.dueAt))
    }
}
