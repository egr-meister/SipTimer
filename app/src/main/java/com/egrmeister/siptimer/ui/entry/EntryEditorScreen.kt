package com.egrmeister.siptimer.ui.entry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.egrmeister.siptimer.AppContainer
import com.egrmeister.siptimer.data.WaterEntryEntity
import com.egrmeister.siptimer.data.WaterRepository
import com.egrmeister.siptimer.domain.ActivationGuard
import com.egrmeister.siptimer.domain.ParseResult
import com.egrmeister.siptimer.domain.Validation
import com.egrmeister.siptimer.domain.localDate
import com.egrmeister.siptimer.ui.common.Fmt
import com.egrmeister.siptimer.ui.common.LocalUndo
import com.egrmeister.siptimer.ui.common.PastDatePickerDialog
import com.egrmeister.siptimer.ui.common.TimePickerDialog
import com.egrmeister.siptimer.ui.common.ValueButton
import com.egrmeister.siptimer.ui.common.container
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

sealed interface SaveOutcome {
    data class Added(val id: Long, val amountMl: Int) : SaveOutcome
    data class Updated(val previous: WaterEntryEntity) : SaveOutcome
    data class Invalid(val message: String) : SaveOutcome
}

class EntryEditorViewModel(private val c: AppContainer) : ViewModel() {
    private val guard = ActivationGuard()

    val zone: ZoneId get() = c.time.zone()
    fun today(): LocalDate = c.time.now().localDate(zone)
    fun nowTime(): LocalTime = c.time.now().atZone(zone).toLocalTime().withSecond(0).withNano(0)

    suspend fun load(id: Long): WaterEntryEntity? = c.repository.getEntry(id)

    /**
     * Saves the draft. A new entry whose date/time the user did not change is a current-time entry
     * (restarts the interval); a deliberately chosen date/time only changes totals.
     */
    fun save(
        existingId: Long?,
        amountText: String,
        date: LocalDate,
        time: LocalTime,
        timeEdited: Boolean,
        onResult: (SaveOutcome) -> Unit,
    ) {
        if (!guard.tryBegin("save", System.currentTimeMillis())) return
        viewModelScope.launch {
            try {
                val amount = when (val r = Validation.parseAmount(amountText)) {
                    is ParseResult.Ok -> r.value
                    is ParseResult.Error -> {
                        onResult(SaveOutcome.Invalid(r.message)); return@launch
                    }
                }
                val now = c.time.now()
                if (existingId == null && !timeEdited) {
                    val id = c.repository.addEntry(amount, now, zone)
                    c.engine.onWaterLoggedNow()
                    onResult(SaveOutcome.Added(id, amount))
                    return@launch
                }
                val instant = when (val r = Validation.entryTimestamp(date, time, zone, now)) {
                    is ParseResult.Ok -> r.value
                    is ParseResult.Error -> {
                        onResult(SaveOutcome.Invalid(r.message)); return@launch
                    }
                }
                if (existingId == null) {
                    val id = c.repository.addEntry(amount, instant, zone)
                    c.engine.onDataChanged()
                    onResult(SaveOutcome.Added(id, amount))
                } else {
                    val original = c.repository.getEntry(existingId)
                    if (original == null) {
                        onResult(SaveOutcome.Invalid("This entry no longer exists."))
                        return@launch
                    }
                    c.repository.updateEntry(original, amount, instant, zone)
                    c.engine.onDataChanged()
                    onResult(SaveOutcome.Updated(original))
                }
            } finally {
                guard.end("save")
            }
        }
    }

    suspend fun undoAdd(id: Long) {
        c.repository.deleteEntry(id)
        c.engine.onDataChanged()
    }

    suspend fun undoUpdate(previous: WaterEntryEntity) {
        c.repository.restoreOver(previous)
        c.engine.onDataChanged()
    }

    companion object {
        val Factory = viewModelFactory { initializer { EntryEditorViewModel(container()) } }
    }
}

@Composable
fun EntryEditorScreen(
    entryId: Long?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    vm: EntryEditorViewModel = viewModel(factory = EntryEditorViewModel.Factory),
) {
    val undo = LocalUndo.current
    val today = vm.today()

    // Draft state survives rotation and process recreation.
    var loaded by rememberSaveable { mutableStateOf(entryId == null) }
    var amount by rememberSaveable { mutableStateOf("") }
    var dateIso by rememberSaveable { mutableStateOf(today.toString()) }
    var timeText by rememberSaveable { mutableStateOf(Fmt.time(vm.nowTime())) }
    var timeEdited by rememberSaveable { mutableStateOf(false) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var missing by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(entryId) {
        if (entryId != null && !loaded) {
            val e = vm.load(entryId)
            if (e == null) {
                missing = true
            } else {
                amount = e.amountMl.toString()
                dateIso = e.loggedLocalDate
                timeText = e.loggedLocalTime
                timeEdited = true
            }
            loaded = true
        }
    }

    val date = LocalDate.parse(dateIso)
    val time = WaterRepository.parseTime(timeText)

    BackHandler(enabled = dirty) { confirmDiscard = true }

    Box(modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (entryId == null) "Add water" else "Edit entry", style = MaterialTheme.typography.headlineSmall)
            if (missing) {
                Text("This entry no longer exists.", style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
                return@Column
            }
            if (!loaded) return@Column
            OutlinedTextField(
                value = amount,
                onValueChange = {
                    amount = it.filter { ch -> ch.isDigit() }.take(4)
                    dirty = true
                    error = null
                },
                label = { Text("Amount (mL)") },
                singleLine = true,
                supportingText = { Text("Whole milliliters, 1–5,000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ValueButton("Date", Fmt.dayShort(date, today), { picker = "date" }, Modifier.weight(1f))
                ValueButton("Time", timeText, { picker = "time" }, Modifier.weight(1f))
            }
            if (entryId == null) {
                Text(
                    if (timeEdited) {
                        "A chosen date or time updates totals only; the reminder countdown is unchanged."
                    } else {
                        "Saving now starts a fresh reminder interval."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { if (dirty) confirmDiscard = true else onClose() },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Text("Cancel") }
                Button(
                    onClick = {
                        vm.save(entryId, amount, date, time, timeEdited) { outcome ->
                            when (outcome) {
                                is SaveOutcome.Invalid -> {
                                    error = outcome.message
                                }
                                is SaveOutcome.Added -> {
                                    dirty = false
                                    undo.show("Added ${Fmt.ml(outcome.amountMl)}") { vm.undoAdd(outcome.id) }
                                    onClose()
                                }
                                is SaveOutcome.Updated -> {
                                    dirty = false
                                    undo.show("Entry updated") { vm.undoUpdate(outcome.previous) }
                                    onClose()
                                }
                            }
                        }
                    },
                    enabled = amount.isNotEmpty(),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Text("Save") }
            }
        }
    }

    when (picker) {
        "date" -> PastDatePickerDialog(
            initial = date,
            today = today,
            onPick = {
                dateIso = it.toString()
                timeEdited = true
                dirty = true
                error = null
            },
            onDismiss = { picker = null },
        )
        "time" -> TimePickerDialog(
            title = "Time",
            initial = time,
            onPick = {
                timeText = Fmt.time(it)
                timeEdited = true
                dirty = true
                error = null
            },
            onDismiss = { picker = null },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your unsaved changes will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    dirty = false
                    onClose()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}
