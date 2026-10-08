package com.egrmeister.siptimer.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate

/**
 * Persisted schedule state. The countdown is always derived from [dueAt]; nothing decrements.
 * [generation] increases on every transition; alarm callbacks carry the generation they were
 * registered with and exit when it is obsolete.
 */
data class ScheduleState(
    val dueAt: Instant?,
    val generation: Long,
    val pausedForDate: LocalDate?,
) {
    companion object {
        val EMPTY = ScheduleState(dueAt = null, generation = 0L, pausedForDate = null)
    }
}

interface ScheduleStore {
    suspend fun config(): ReminderConfig?
    suspend fun state(): ScheduleState
    suspend fun saveState(state: ScheduleState)
}

interface AlarmGateway {
    /** Registers ONE inexact one-shot alarm (replacing any previous one). */
    fun schedule(at: Instant, generation: Long)
    fun cancel()
}

interface NotificationGateway {
    /** App-level notifications enabled, POST_NOTIFICATIONS granted (API 33+), channel not blocked. */
    fun canPost(): Boolean
    fun post()
    fun dismiss()
}

interface GoalProgressSource {
    suspend fun goalReachedOn(date: LocalDate): Boolean
}

interface SkipRecorder {
    suspend fun recordSkip(skippedDueAt: Instant, actionAt: Instant, date: LocalDate, replacementDueAt: Instant)
}

sealed interface DeliveryResult {
    data object Posted : DeliveryResult
    data object Obsolete : DeliveryResult
    data object NotEarlyYet : DeliveryResult
    data class Rejected(val reason: String) : DeliveryResult
    data object PausedForGoal : DeliveryResult
}

sealed interface SkipResult {
    data class Skipped(val replacement: Instant) : SkipResult
    data class Ignored(val reason: String) : SkipResult
}

/**
 * Reminder scheduling state machine. Every transition runs under one [Mutex] so transitions are
 * serialized; state is persisted BEFORE the alarm is registered, and [recover] re-registers the
 * persisted alarm (e.g. after process death between the two steps, reboot or app update).
 */
class ReminderEngine(
    private val store: ScheduleStore,
    private val progress: GoalProgressSource,
    private val skips: SkipRecorder,
    private val alarms: AlarmGateway,
    private val notifications: NotificationGateway,
    private val time: TimeSource,
    private val log: (String) -> Unit = {},
) {
    private val mutex = Mutex()

    private suspend fun commit(config: ReminderConfig, next: ScheduleState) {
        store.saveState(next)
        val due = next.dueAt
        if (config.enabled && due != null && notifications.canPost()) {
            alarms.schedule(due, next.generation)
            log("scheduled gen=${next.generation} due=$due paused=${next.pausedForDate}")
        } else {
            alarms.cancel()
            log("no alarm gen=${next.generation} enabled=${config.enabled} due=$due")
        }
    }

    /** Fresh interval from now (or the goal-reached pause), always with a new generation. */
    private suspend fun freshState(config: ReminderConfig, previous: ScheduleState): ScheduleState {
        val now = time.now()
        val zone = time.zone()
        val today = now.localDate(zone)
        val generation = previous.generation + 1
        if (!config.enabled) return ScheduleState(null, generation, null)
        if (progress.goalReachedOn(today)) {
            notifications.dismiss()
            return ScheduleState(ReminderPlanner.firstOfDay(today.plusDays(1), config, zone), generation, today)
        }
        return ScheduleState(ReminderPlanner.freshFrom(now, config, zone), generation, null)
    }

    /** Reminders enabled/disabled, interval or active hours changed. */
    suspend fun onConfigChanged() = mutex.withLock {
        val config = store.config() ?: return@withLock
        if (!config.enabled) notifications.dismiss()
        commit(config, freshState(config, store.state()))
    }

    /** A successful current-time water entry: cancel the pending reminder, start a fresh interval. */
    suspend fun onWaterLoggedNow() = mutex.withLock {
        val config = store.config() ?: return@withLock
        notifications.dismiss()
        commit(config, freshState(config, store.state()))
    }

    /** Manual time or time-zone change: discard the old schedule and compute a fresh one. */
    suspend fun onClockChanged() = mutex.withLock {
        val config = store.config() ?: return@withLock
        commit(config, freshState(config, store.state()))
    }

    /**
     * Backdated entries, edits, deletions, Undo or goal changes. These never restart the
     * countdown, except: reaching the goal pauses today; dropping below it after a pause starts a
     * fresh interval from now (an obsolete due time is never restored).
     */
    suspend fun onDataChanged() = mutex.withLock {
        val config = store.config() ?: return@withLock
        if (!config.enabled) return@withLock
        val previous = store.state()
        val today = time.now().localDate(time.zone())
        val reached = progress.goalReachedOn(today)
        val pausedToday = previous.pausedForDate == today
        when {
            reached && !pausedToday -> commit(config, freshState(config, previous))
            !reached && pausedToday -> commit(config, freshState(config, previous))
            previous.dueAt == null -> commit(config, freshState(config, previous))
            else -> Unit
        }
    }

    /** Process start, reboot, app update, permission regained: restore without restarting. */
    suspend fun recover() = mutex.withLock {
        val config = store.config() ?: return@withLock
        val previous = store.state()
        if (!config.enabled) {
            alarms.cancel()
            return@withLock
        }
        val due = previous.dueAt
        if (due == null) {
            commit(config, freshState(config, previous))
            return@withLock
        }
        val now = time.now()
        val zone = time.zone()
        val today = now.localDate(zone)
        // A stored due time from an earlier day can never be delivered (stale) — recalculate.
        if (due.isBefore(now) && due.localDate(zone).isBefore(today)) {
            commit(config, freshState(config, previous))
            return@withLock
        }
        // Midnight passed while paused and today's goal is not reached: the stored first reminder
        // of the day is still valid; just clear nothing and re-register.
        commit(config, previous)
    }

    /** Alarm callback. Rejects obsolete generations; never posts outside the window or stale days. */
    suspend fun onAlarm(generation: Long): DeliveryResult = mutex.withLock {
        val config = store.config() ?: return@withLock DeliveryResult.Obsolete
        val previous = store.state()
        if (generation != previous.generation) {
            log("obsolete callback gen=$generation current=${previous.generation}")
            return@withLock DeliveryResult.Obsolete
        }
        val due = previous.dueAt ?: return@withLock DeliveryResult.Obsolete
        if (!config.enabled) {
            alarms.cancel()
            return@withLock DeliveryResult.Rejected("reminders off")
        }
        val now = time.now()
        val zone = time.zone()
        val today = now.localDate(zone)
        if (now.isBefore(due)) {
            // Fired early (e.g. clock moved): keep the saved schedule.
            commit(config, previous)
            return@withLock DeliveryResult.NotEarlyYet
        }
        if (progress.goalReachedOn(today)) {
            commit(config, freshState(config, previous))
            return@withLock DeliveryResult.PausedForGoal
        }
        val staleDay = due.localDate(zone) != today
        val insideWindow = config.window.contains(now, zone)
        val allowed = notifications.canPost()
        val result = when {
            staleDay -> DeliveryResult.Rejected("stale previous-day callback")
            !insideWindow -> DeliveryResult.Rejected("outside active hours")
            !allowed -> DeliveryResult.Rejected("notifications not allowed")
            else -> {
                notifications.post()
                DeliveryResult.Posted
            }
        }
        // Next eligible reminder one interval after processing time; missed intervals are not replayed.
        commit(config, ScheduleState(ReminderPlanner.freshFrom(now, config, zone), previous.generation + 1, null))
        log("alarm processed gen=$generation result=$result")
        result
    }

    /**
     * Skip Next Reminder. [expectedDueAt]/[expectedGeneration] identify the occurrence the user saw;
     * a duplicate activation for an already-skipped occurrence is ignored.
     */
    suspend fun skip(expectedDueAt: Instant, expectedGeneration: Long): SkipResult = mutex.withLock {
        val config = store.config() ?: return@withLock SkipResult.Ignored("no config")
        val previous = store.state()
        val now = time.now()
        val zone = time.zone()
        val today = now.localDate(zone)
        val due = previous.dueAt
        when {
            !config.enabled -> return@withLock SkipResult.Ignored("reminders off")
            !notifications.canPost() -> return@withLock SkipResult.Ignored("notifications blocked")
            previous.pausedForDate == today -> return@withLock SkipResult.Ignored("paused for goal")
            due == null || due != expectedDueAt || previous.generation != expectedGeneration ->
                return@withLock SkipResult.Ignored("already processed")
            !due.isAfter(now) -> return@withLock SkipResult.Ignored("no future reminder")
        }
        val skipped = due!!
        val replacement = ReminderPlanner.skip(skipped, config, zone)
        skips.recordSkip(skipped, now, today, replacement)
        commit(config, ScheduleState(replacement, previous.generation + 1, null))
        SkipResult.Skipped(replacement)
    }

    /** Clear All Data: cancel alarms and notifications first. */
    suspend fun cancelEverything() = mutex.withLock {
        alarms.cancel()
        notifications.dismiss()
        store.saveState(ScheduleState(null, store.state().generation + 1, null))
    }
}
