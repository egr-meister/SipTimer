package com.egrmeister.siptimer.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.egrmeister.siptimer.AppContainer
import com.egrmeister.siptimer.data.DaySummary
import com.egrmeister.siptimer.data.ReminderSkipEntity
import com.egrmeister.siptimer.data.WaterEntryEntity
import com.egrmeister.siptimer.domain.CountdownPresenter
import com.egrmeister.siptimer.domain.GoalMath
import com.egrmeister.siptimer.domain.localDate
import com.egrmeister.siptimer.ui.common.Fmt
import com.egrmeister.siptimer.ui.common.LocalUndo
import com.egrmeister.siptimer.ui.common.PastDatePickerDialog
import com.egrmeister.siptimer.ui.common.container
import com.egrmeister.siptimer.ui.today.EntryActions
import com.egrmeister.siptimer.ui.today.EntryRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HistoryViewModel(private val c: AppContainer) : ViewModel() {
    val days: StateFlow<List<DaySummary>?> =
        c.repository.daySummaries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun today(): LocalDate = c.time.now().localDate(c.time.zone())

    companion object {
        val Factory = viewModelFactory { initializer { HistoryViewModel(container()) } }
    }
}

data class DayDetailUi(
    val loaded: Boolean = false,
    val entries: List<WaterEntryEntity> = emptyList(),
    val skips: List<ReminderSkipEntity> = emptyList(),
    val goalMl: Int? = null,
)

class DayDetailViewModel(private val c: AppContainer) : ViewModel() {
    val zone: ZoneId get() = c.time.zone()
    val actions = EntryActions(c)

    fun today(): LocalDate = c.time.now().localDate(zone)

    fun observe(date: LocalDate) = combine(
        c.repository.observeEntries(date),
        c.repository.observeSkips(date),
        c.repository.goalHistory,
    ) { entries, skips, goals -> DayDetailUi(true, entries, skips, goals.goalFor(date)) }

    companion object {
        val Factory = viewModelFactory { initializer { DayDetailViewModel(container()) } }
    }
}

@Composable
fun HistoryScreen(
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val days by vm.days.collectAsStateWithLifecycle()
    val today = vm.today()
    var picking by rememberSaveable { mutableStateOf(false) }

    LazyColumn(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
            ) {
                Text("History", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.DateRange, contentDescription = null)
                    Text("  Pick a date")
                }
            }
        }
        val list = days
        if (list != null && list.isEmpty()) {
            item {
                Text(
                    "No records yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(20.dp),
                )
            }
        }
        items(list.orEmpty(), key = { it.date.toEpochDay() }) { day ->
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp)) {
                DaySummaryRow(day, today) { onOpenDay(day.date) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (picking) {
        PastDatePickerDialog(initial = today, today = today, onPick = onOpenDay, onDismiss = { picking = false })
    }
}

@Composable
private fun DaySummaryRow(day: DaySummary, today: LocalDate, onClick: () -> Unit) {
    val goal = day.goalMl?.let { " of ${Fmt.ml(it)}" } ?: ""
    val percent = GoalMath.percent(day.totalMl, day.goalMl)?.let { " · $it%" } ?: ""
    val entries = "${day.entryCount} ${if (day.entryCount == 1) "entry" else "entries"}"
    val skips = "${day.skipCount} skipped ${if (day.skipCount == 1) "reminder" else "reminders"}"
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClickLabel = "Open day", onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(Fmt.dayShort(day.date, today), style = MaterialTheme.typography.titleMedium)
        Text("${Fmt.ml(day.totalMl)}$goal$percent", style = MaterialTheme.typography.bodyLarge)
        Text("$entries · $skips", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DayDetailScreen(
    date: LocalDate,
    onBack: () -> Unit,
    onChangeDate: (LocalDate) -> Unit,
    onEditEntry: (Long) -> Unit,
    modifier: Modifier = Modifier,
    vm: DayDetailViewModel = viewModel(factory = DayDetailViewModel.Factory),
) {
    val flow = remember(date) { vm.observe(date) }
    val ui by flow.collectAsStateWithLifecycle(initialValue = DayDetailUi())
    val today = vm.today()
    val zone = vm.zone
    val undo = LocalUndo.current
    val scope = rememberCoroutineScope()
    var picking by rememberSaveable { mutableStateOf(false) }

    LazyColumn(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to History") }
                    Text(
                        Fmt.dayShort(date, today),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    IconButton(onClick = { picking = true }) { Icon(Icons.Filled.DateRange, contentDescription = "Pick a date") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { onChangeDate(date.minusDays(1)) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Earlier day")
                    }
                    Text(
                        Fmt.dayLong(date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onChangeDate(date.plusDays(1)) }, enabled = date.isBefore(today)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Later day")
                    }
                }
            }
        }
        if (ui.loaded) {
            val hasRecords = ui.entries.isNotEmpty() || ui.skips.isNotEmpty()
            item {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp)) {
                    if (!hasRecords) {
                        Text("No records", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 16.dp))
                        Text(
                            ui.goalMl?.let { "Saved goal: ${Fmt.ml(it)}" } ?: "No saved goal for this date.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val total = ui.entries.sumOf { it.amountMl.toLong() }
                        val goalText = ui.goalMl?.let { " of ${Fmt.ml(it)}" } ?: ""
                        val pct = GoalMath.percent(total, ui.goalMl)?.let { " · $it%" } ?: ""
                        Text(
                            "${Fmt.ml(total)}$goalText$pct",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    if (hasRecords) {
                        Spacer(Modifier.height(8.dp))
                        Text("Water entries", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        if (ui.entries.isEmpty()) {
                            Text("No water entries.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
            items(ui.entries, key = { "e" + it.id }) { entry ->
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp)) {
                    EntryRow(
                        entry = entry,
                        onEdit = { onEditEntry(entry.id) },
                        onDelete = {
                            scope.launch {
                                vm.actions.delete(entry)
                                undo.show("Deleted ${Fmt.ml(entry.amountMl)}") { vm.actions.restore(entry) }
                            }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            if (hasRecords) {
                item {
                    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp)) {
                        Spacer(Modifier.height(16.dp))
                        Text("Skipped reminders", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        if (ui.skips.isEmpty()) {
                            Text("None.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
                items(ui.skips, key = { "s" + it.id }) { skip ->
                    val skippedAt = CountdownPresenter.formatTime(Instant.ofEpochMilli(skip.skippedDueAt), zone)
                    val actionAt = CountdownPresenter.formatTime(Instant.ofEpochMilli(skip.actionTimestamp), zone)
                    val replacement = CountdownPresenter.formatWhen(Instant.ofEpochMilli(skip.replacementDueAt), zone, date)
                    Column(
                        Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .semantics(mergeDescendants = true) {
                                contentDescription = "Reminder for $skippedAt skipped at $actionAt; moved to $replacement"
                            },
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("Reminder $skippedAt skipped", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Skipped at $actionAt · next was $replacement",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (picking) {
        PastDatePickerDialog(initial = date, today = today, onPick = onChangeDate, onDismiss = { picking = false })
    }
}
