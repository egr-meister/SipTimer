package com.egrmeister.siptimer.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.egrmeister.siptimer.AppContainer
import com.egrmeister.siptimer.data.WaterEntryEntity
import com.egrmeister.siptimer.domain.ActivationGuard
import com.egrmeister.siptimer.domain.CountdownPresenter
import com.egrmeister.siptimer.domain.CountdownState
import com.egrmeister.siptimer.domain.GoalHistory
import com.egrmeister.siptimer.domain.GoalMath
import com.egrmeister.siptimer.domain.ReminderConfig
import com.egrmeister.siptimer.domain.ScheduleState
import com.egrmeister.siptimer.domain.SkipResult
import com.egrmeister.siptimer.domain.localDate
import com.egrmeister.siptimer.ui.common.Fmt
import com.egrmeister.siptimer.ui.common.container
import com.egrmeister.siptimer.ui.common.minuteTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class TodayUi(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
    val zone: ZoneId = ZoneId.systemDefault(),
    val countdown: CountdownState = CountdownState.Preparing,
    val headline: String = "",
    val scheduledLine: String? = null,
    val statusLine: String = "",
    val canSkip: Boolean = false,
    val showSettingsLink: Boolean = false,
    val dueAt: Instant? = null,
    val generation: Long = 0,
    val totalMl: Long = 0,
    val goalMl: Int? = null,
    val percent: Int? = null,
    val progress: Float = 0f,
    val presets: List<Int> = emptyList(),
    val entries: List<WaterEntryEntity> = emptyList(),
)

private data class ScheduleInputs(
    val now: Instant,
    val config: ReminderConfig?,
    val state: ScheduleState,
    val allowed: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val c: AppContainer) : ViewModel() {
    private val guard = ActivationGuard()

    private val ticks = minuteTicker(c.time).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val todayFlow = ticks.map { it.localDate(c.time.zone()) }.distinctUntilChanged()

    private val entriesFlow = todayFlow.flatMapLatest { c.repository.observeEntries(it) }

    private val scheduleInputs = combine(ticks, c.settings.configFlow, c.settings.stateFlow, c.notificationsAllowed) { now, config, state, allowed ->
        ScheduleInputs(now, config, state, allowed)
    }

    val ui: StateFlow<TodayUi> = combine(
        scheduleInputs,
        entriesFlow,
        c.repository.goalHistory,
        c.settings.presetsFlow,
    ) { inputs, entries, goals, presets -> build(inputs, entries, goals, presets) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUi())

    private fun build(inputs: ScheduleInputs, entries: List<WaterEntryEntity>, goals: GoalHistory, presets: List<Int>): TodayUi {
        val zone = c.time.zone()
        val today = inputs.now.localDate(zone)
        val total = entries.sumOf { it.amountMl.toLong() }
        val goal = goals.goalFor(today)
        val reached = GoalMath.reached(total, goal)
        val countdown = CountdownPresenter.present(inputs.config, inputs.state, inputs.allowed, reached, inputs.now, zone)
        val due = inputs.state.dueAt
        val scheduledLine = when (countdown) {
            is CountdownState.Active -> "Scheduled for ${CountdownPresenter.formatTime(countdown.dueAt, zone)}"
            is CountdownState.Passed -> "Scheduled for ${CountdownPresenter.formatTime(countdown.dueAt, zone)}"
            CountdownState.GoalReached -> due?.let { "Resumes ${CountdownPresenter.formatWhen(it, zone, today)}" }
            else -> null
        }
        val cfg = inputs.config
        val statusLine = when {
            cfg == null -> ""
            !cfg.enabled -> "Logging works without reminders. Turn them on in Settings."
            else -> "Reminders on · every ${cfg.intervalMinutes} min · ${Fmt.minuteOfDay(cfg.startMinute)}–${Fmt.minuteOfDay(cfg.endMinute)}"
        }
        val canSkip = (countdown is CountdownState.Active || countdown is CountdownState.Later) &&
            due != null && due.isAfter(inputs.now)
        return TodayUi(
            loaded = true,
            today = today,
            zone = zone,
            countdown = countdown,
            headline = CountdownPresenter.headline(countdown, zone, today),
            scheduledLine = scheduledLine,
            statusLine = statusLine,
            canSkip = canSkip,
            showSettingsLink = countdown == CountdownState.Blocked,
            dueAt = due,
            generation = inputs.state.generation,
            totalMl = total,
            goalMl = goal,
            percent = GoalMath.percent(total, goal),
            progress = GoalMath.progressFraction(total, goal),
            presets = presets,
            entries = entries,
        )
    }

    /** Quick Add: records the current date and time and restarts the interval. */
    fun quickAdd(amountMl: Int, onAdded: (Long) -> Unit) {
        val key = "quick-$amountMl"
        if (!guard.tryBegin(key, System.currentTimeMillis())) return
        viewModelScope.launch {
            try {
                val id = c.repository.addEntry(amountMl, c.time.now(), c.time.zone())
                c.engine.onWaterLoggedNow()
                onAdded(id)
            } finally {
                guard.end(key)
            }
        }
    }

    fun skip(onSkipped: (Instant) -> Unit) {
        val state = ui.value
        val due = state.dueAt ?: return
        if (!state.canSkip) return
        if (!guard.tryBegin("skip", System.currentTimeMillis())) return
        viewModelScope.launch {
            try {
                val result = c.engine.skip(due, state.generation)
                if (result is SkipResult.Skipped) onSkipped(result.replacement)
            } finally {
                guard.end("skip")
            }
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { TodayViewModel(container()) } }
    }
}

/** Shared entry mutations with Undo, used by Today and day details. */
class EntryActions(private val c: AppContainer) {
    suspend fun undoAdd(id: Long) {
        c.repository.deleteEntry(id)
        c.engine.onDataChanged()
    }

    suspend fun delete(entry: WaterEntryEntity) {
        c.repository.deleteEntry(entry.id)
        c.engine.onDataChanged()
    }

    suspend fun restore(entry: WaterEntryEntity) {
        c.repository.restoreEntry(entry)
        c.engine.onDataChanged()
    }
}
