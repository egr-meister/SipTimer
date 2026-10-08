package com.egrmeister.siptimer.data

import androidx.room.withTransaction
import com.egrmeister.siptimer.domain.GoalChange
import com.egrmeister.siptimer.domain.GoalHistory
import com.egrmeister.siptimer.domain.GoalMath
import com.egrmeister.siptimer.domain.GoalProgressSource
import com.egrmeister.siptimer.domain.SkipRecorder
import com.egrmeister.siptimer.domain.TimeSource
import com.egrmeister.siptimer.domain.localDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DaySummary(
    val date: LocalDate,
    val totalMl: Long,
    val goalMl: Int?,
    val entryCount: Int,
    val skipCount: Int,
)

/** All Room access goes through here, on [io] — never on the main thread. */
class WaterRepository(
    private val db: AppDatabase,
    private val time: TimeSource,
    private val io: CoroutineDispatcher,
) : GoalProgressSource, SkipRecorder {

    private val entries = db.waterEntryDao()
    private val goals = db.goalChangeDao()
    private val skips = db.reminderSkipDao()

    fun observeEntries(date: LocalDate): Flow<List<WaterEntryEntity>> = entries.observeForDate(date.toString())

    fun observeSkips(date: LocalDate): Flow<List<ReminderSkipEntity>> = skips.observeForDate(date.toString())

    val goalHistory: Flow<GoalHistory> = goals.observeAll().map { list ->
        GoalHistory(list.map { GoalChange(LocalDate.parse(it.effectiveLocalDate), it.goalMl) })
    }

    val daySummaries: Flow<List<DaySummary>> =
        combine(entries.observeDayVolumes(), skips.observeSkipCounts(), goalHistory) { volumes, skipCounts, history ->
            val byDate = HashMap<String, DaySummary>()
            for (v in volumes) {
                val d = LocalDate.parse(v.loggedLocalDate)
                byDate[v.loggedLocalDate] = DaySummary(d, v.totalMl, history.goalFor(d), v.entryCount, 0)
            }
            for (s in skipCounts) {
                val existing = byDate[s.loggedLocalDate]
                val d = LocalDate.parse(s.loggedLocalDate)
                byDate[s.loggedLocalDate] = existing?.copy(skipCount = s.skipCount)
                    ?: DaySummary(d, 0L, history.goalFor(d), 0, s.skipCount)
            }
            byDate.values.sortedByDescending { it.date }
        }

    suspend fun getEntry(id: Long): WaterEntryEntity? = withContext(io) { entries.getById(id) }

    /** Inserts an entry at [instant]; local values are recorded in [zone]. Returns the new id. */
    suspend fun addEntry(amountMl: Int, instant: Instant, zone: ZoneId): Long = withContext(io) {
        val local = instant.atZone(zone)
        val nowMs = time.now().toEpochMilli()
        entries.insert(
            WaterEntryEntity(
                amountMl = amountMl,
                timestamp = instant.toEpochMilli(),
                loggedLocalDate = local.toLocalDate().toString(),
                loggedLocalTime = TIME.format(local.toLocalTime()),
                zoneId = zone.id,
                createdAt = nowMs,
                updatedAt = nowMs,
            ),
        )
    }

    suspend fun updateEntry(original: WaterEntryEntity, amountMl: Int, instant: Instant, zone: ZoneId) = withContext(io) {
        val local = instant.atZone(zone)
        entries.update(
            original.copy(
                amountMl = amountMl,
                timestamp = instant.toEpochMilli(),
                loggedLocalDate = local.toLocalDate().toString(),
                loggedLocalTime = TIME.format(local.toLocalTime()),
                zoneId = zone.id,
                updatedAt = time.now().toEpochMilli(),
            ),
        )
    }

    suspend fun deleteEntry(id: Long) = withContext(io) { entries.deleteById(id) }

    /** Restores a deleted entry exactly (same id and timestamps) for Undo. */
    suspend fun restoreEntry(entry: WaterEntryEntity) = withContext(io) { entries.insert(entry) }

    /** Undo of an edit: writes back the previous values of the same row. */
    suspend fun restoreOver(previous: WaterEntryEntity) = withContext(io) { entries.update(previous) }

    suspend fun totalFor(date: LocalDate): Long = withContext(io) { entries.totalForDate(date.toString()) }

    suspend fun goalFor(date: LocalDate): Int? = withContext(io) {
        GoalHistory(goals.getAll().map { GoalChange(LocalDate.parse(it.effectiveLocalDate), it.goalMl) }).goalFor(date)
    }

    /** Saves a goal effective from [effectiveDate]; later scheduled changes are superseded. */
    suspend fun setGoal(goalMl: Int, effectiveDate: LocalDate) = withContext(io) {
        db.withTransaction {
            goals.deleteAfter(effectiveDate.toString())
            goals.upsert(GoalChangeEntity(effectiveDate.toString(), goalMl, time.now().toEpochMilli()))
        }
    }

    override suspend fun goalReachedOn(date: LocalDate): Boolean = GoalMath.reached(totalFor(date), goalFor(date))

    override suspend fun recordSkip(skippedDueAt: Instant, actionAt: Instant, date: LocalDate, replacementDueAt: Instant) {
        withContext(io) {
            skips.insert(
                ReminderSkipEntity(
                    skippedDueAt = skippedDueAt.toEpochMilli(),
                    actionTimestamp = actionAt.toEpochMilli(),
                    loggedLocalDate = date.toString(),
                    replacementDueAt = replacementDueAt.toEpochMilli(),
                ),
            )
        }
    }

    suspend fun clearAll() = withContext(io) { db.clearAllTables() }

    fun todayDate(): LocalDate = time.now().localDate(time.zone())

    companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

        fun parseTime(text: String): LocalTime = LocalTime.parse(text, TIME)
    }
}
