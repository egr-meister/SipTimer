package com.egrmeister.siptimer.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class FakeTime(var instant: Instant, var zoneId: ZoneId = ZoneId.of("Europe/Minsk")) : TimeSource {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId
    fun set(local: LocalDateTime) {
        instant = local.atZone(zoneId).toInstant()
    }
    fun advance(minutes: Long) {
        instant = instant.plus(Duration.ofMinutes(minutes))
    }
}

class FakeStore(var cfg: ReminderConfig?, var st: ScheduleState = ScheduleState.EMPTY) : ScheduleStore {
    override suspend fun config(): ReminderConfig? = cfg
    override suspend fun state(): ScheduleState = st
    override suspend fun saveState(state: ScheduleState) {
        st = state
    }
}

class FakeAlarms : AlarmGateway {
    var scheduledAt: Instant? = null
    var scheduledGeneration: Long? = null
    var cancels = 0
    override fun schedule(at: Instant, generation: Long) {
        scheduledAt = at
        scheduledGeneration = generation
    }
    override fun cancel() {
        scheduledAt = null
        scheduledGeneration = null
        cancels++
    }
}

class FakeNotifications(var allowed: Boolean = true) : NotificationGateway {
    var posted = 0
    var dismissed = 0
    override fun canPost(): Boolean = allowed
    override fun post() {
        posted++
    }
    override fun dismiss() {
        dismissed++
    }
}

/** In-memory "water log": entries per date and an effective-dated goal history. */
class FakeProgress(private val time: TimeSource) : GoalProgressSource, SkipRecorder {
    val totals = HashMap<LocalDate, Long>()
    var goals = GoalHistory(listOf(GoalChange(LocalDate.of(2020, 1, 1), 2_000)))
    val skips = ArrayList<Instant>()

    fun add(date: LocalDate, ml: Long) {
        totals[date] = (totals[date] ?: 0L) + ml
    }

    override suspend fun goalReachedOn(date: LocalDate): Boolean = GoalMath.reached(totals[date] ?: 0L, goals.goalFor(date))

    override suspend fun recordSkip(skippedDueAt: Instant, actionAt: Instant, date: LocalDate, replacementDueAt: Instant) {
        skips += skippedDueAt
    }
}

class Rig(
    start: LocalDateTime,
    config: ReminderConfig = ReminderConfig(intervalMinutes = 120, startMinute = 8 * 60, endMinute = 22 * 60, enabled = true),
    zone: ZoneId = ZoneId.of("Europe/Minsk"),
) {
    val time = FakeTime(start.atZone(zone).toInstant(), zone)
    val store = FakeStore(config)
    val alarms = FakeAlarms()
    val notifications = FakeNotifications()
    val progress = FakeProgress(time)
    val engine = ReminderEngine(store, progress, progress, alarms, notifications, time)

    val today: LocalDate get() = time.now().atZone(time.zone()).toLocalDate()

    fun at(local: LocalDateTime): Instant = local.atZone(time.zone()).toInstant()

    fun local(instant: Instant?): LocalDateTime? = instant?.atZone(time.zone())?.toLocalDateTime()
}
