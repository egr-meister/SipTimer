package com.egrmeister.siptimer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.egrmeister.siptimer.domain.Limits
import com.egrmeister.siptimer.domain.ReminderConfig
import com.egrmeister.siptimer.domain.ScheduleState
import com.egrmeister.siptimer.domain.ScheduleStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

private val Context.siptimerStore: DataStore<Preferences> by preferencesDataStore(name = "siptimer_settings")

/**
 * DataStore for reminder configuration, schedule state, quick portions and UI preferences.
 * Lives in app-private storage and is excluded from backup / device transfer.
 */
class SettingsStore(context: Context) : ScheduleStore {
    private val store = context.applicationContext.siptimerStore

    private object Keys {
        val SETUP_DONE = booleanPreferencesKey("setup_done")
        val INTERVAL = intPreferencesKey("interval_minutes")
        val START = intPreferencesKey("start_minute")
        val END = intPreferencesKey("end_minute")
        val ENABLED = booleanPreferencesKey("reminders_enabled")
        val PERMISSION_ASKED = booleanPreferencesKey("notification_permission_asked")
        val DUE_AT = longPreferencesKey("next_due_at")
        val GENERATION = longPreferencesKey("schedule_generation")
        val PAUSED_DATE = stringPreferencesKey("paused_for_date")
        val PRESETS = stringPreferencesKey("quick_presets")
    }

    val setupDone: Flow<Boolean> = store.data.map { it[Keys.SETUP_DONE] ?: false }

    val configFlow: Flow<ReminderConfig?> = store.data.map { it.toConfig() }

    val stateFlow: Flow<ScheduleState> = store.data.map { it.toState() }

    val presetsFlow: Flow<List<Int>> = store.data.map { prefs -> parsePresets(prefs[Keys.PRESETS]) }

    val permissionAsked: Flow<Boolean> = store.data.map { it[Keys.PERMISSION_ASKED] ?: false }

    override suspend fun config(): ReminderConfig? = store.data.first().toConfig()

    override suspend fun state(): ScheduleState = store.data.first().toState()

    override suspend fun saveState(state: ScheduleState) {
        store.edit { p ->
            val due = state.dueAt
            if (due == null) {
                p.remove(Keys.DUE_AT)
            } else {
                p[Keys.DUE_AT] = due.toEpochMilli()
            }
            p[Keys.GENERATION] = state.generation
            val paused = state.pausedForDate
            if (paused == null) {
                p.remove(Keys.PAUSED_DATE)
            } else {
                p[Keys.PAUSED_DATE] = paused.toString()
            }
        }
    }

    suspend fun saveConfig(config: ReminderConfig, markSetupDone: Boolean = false) {
        store.edit { p ->
            p[Keys.INTERVAL] = config.intervalMinutes
            p[Keys.START] = config.startMinute
            p[Keys.END] = config.endMinute
            p[Keys.ENABLED] = config.enabled
            if (markSetupDone) p[Keys.SETUP_DONE] = true
        }
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        store.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun markPermissionAsked() {
        store.edit { it[Keys.PERMISSION_ASKED] = true }
    }

    suspend fun savePresets(presets: List<Int>) {
        store.edit { it[Keys.PRESETS] = presets.joinToString(",") }
    }

    suspend fun clearAll() {
        store.edit { it.clear() }
    }

    private fun Preferences.toConfig(): ReminderConfig? {
        val interval = this[Keys.INTERVAL] ?: return null
        val start = this[Keys.START] ?: return null
        val end = this[Keys.END] ?: return null
        return ReminderConfig(interval, start, end, this[Keys.ENABLED] ?: false)
    }

    private fun Preferences.toState(): ScheduleState = ScheduleState(
        dueAt = this[Keys.DUE_AT]?.let { Instant.ofEpochMilli(it) },
        generation = this[Keys.GENERATION] ?: 0L,
        pausedForDate = this[Keys.PAUSED_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    )

    private fun parsePresets(raw: String?): List<Int> {
        val parsed = raw?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it in Limits.MIN_AMOUNT_ML..Limits.MAX_AMOUNT_ML }
        return if (parsed.isNullOrEmpty()) Limits.DEFAULT_PRESETS else parsed
    }
}
