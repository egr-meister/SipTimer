package com.egrmeister.siptimer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A water entry. [timestamp] is the absolute instant (epoch millis); [loggedLocalDate] /
 * [loggedLocalTime] / [zoneId] are the local values at logging time and drive history grouping,
 * so travelling or DST never moves an entry to another day.
 */
@Entity(tableName = "water_entries", indices = [Index(value = ["loggedLocalDate"])])
data class WaterEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMl: Int,
    val timestamp: Long,
    /** ISO-8601 local date, e.g. 2026-10-08. */
    val loggedLocalDate: String,
    /** Local time HH:mm. */
    val loggedLocalTime: String,
    val zoneId: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Effective-dated goal. One row per effective date; earlier dates are never rewritten. */
@Entity(tableName = "goal_changes")
data class GoalChangeEntity(
    @PrimaryKey val effectiveLocalDate: String,
    val goalMl: Int,
    val createdAt: Long,
)

/** A skipped reminder occurrence. Never counted as water. */
@Entity(tableName = "reminder_skips", indices = [Index(value = ["loggedLocalDate"])])
data class ReminderSkipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val skippedDueAt: Long,
    val actionTimestamp: Long,
    val loggedLocalDate: String,
    val replacementDueAt: Long,
)

data class DayVolume(val loggedLocalDate: String, val totalMl: Long, val entryCount: Int)

data class DaySkipCount(val loggedLocalDate: String, val skipCount: Int)
