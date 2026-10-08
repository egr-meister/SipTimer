package com.egrmeister.siptimer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterEntryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: WaterEntryEntity): Long

    @Update
    suspend fun update(entry: WaterEntryEntity)

    @Query("DELETE FROM water_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM water_entries WHERE id = :id")
    suspend fun getById(id: Long): WaterEntryEntity?

    @Query("SELECT * FROM water_entries WHERE loggedLocalDate = :date ORDER BY timestamp ASC, id ASC")
    fun observeForDate(date: String): Flow<List<WaterEntryEntity>>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_entries WHERE loggedLocalDate = :date")
    suspend fun totalForDate(date: String): Long

    @Query(
        "SELECT loggedLocalDate AS loggedLocalDate, COALESCE(SUM(amountMl), 0) AS totalMl, COUNT(*) AS entryCount " +
            "FROM water_entries GROUP BY loggedLocalDate ORDER BY loggedLocalDate DESC",
    )
    fun observeDayVolumes(): Flow<List<DayVolume>>

    @Query("DELETE FROM water_entries")
    suspend fun deleteAll()
}

@Dao
interface GoalChangeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(change: GoalChangeEntity)

    @Query("DELETE FROM goal_changes WHERE effectiveLocalDate > :date")
    suspend fun deleteAfter(date: String)

    @Query("SELECT * FROM goal_changes ORDER BY effectiveLocalDate ASC")
    fun observeAll(): Flow<List<GoalChangeEntity>>

    @Query("SELECT * FROM goal_changes ORDER BY effectiveLocalDate ASC")
    suspend fun getAll(): List<GoalChangeEntity>

    @Query("DELETE FROM goal_changes")
    suspend fun deleteAll()
}

@Dao
interface ReminderSkipDao {
    @Insert
    suspend fun insert(skip: ReminderSkipEntity): Long

    @Query("SELECT * FROM reminder_skips WHERE loggedLocalDate = :date ORDER BY actionTimestamp ASC, id ASC")
    fun observeForDate(date: String): Flow<List<ReminderSkipEntity>>

    @Query(
        "SELECT loggedLocalDate AS loggedLocalDate, COUNT(*) AS skipCount FROM reminder_skips " +
            "GROUP BY loggedLocalDate ORDER BY loggedLocalDate DESC",
    )
    fun observeSkipCounts(): Flow<List<DaySkipCount>>

    @Query("DELETE FROM reminder_skips")
    suspend fun deleteAll()
}
