package com.egrmeister.siptimer.ui.settings

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.egrmeister.siptimer.AppContainer
import com.egrmeister.siptimer.domain.GoalHistory
import com.egrmeister.siptimer.domain.ParseResult
import com.egrmeister.siptimer.domain.ReminderConfig
import com.egrmeister.siptimer.domain.Validation
import com.egrmeister.siptimer.domain.localDate
import com.egrmeister.siptimer.ui.common.Fmt
import com.egrmeister.siptimer.ui.common.LocalUndo
import com.egrmeister.siptimer.ui.common.ScheduleFields
import com.egrmeister.siptimer.ui.common.SwitchRow
import com.egrmeister.siptimer.ui.common.container
import com.egrmeister.siptimer.ui.common.openNotificationSettings
import com.egrmeister.siptimer.ui.common.rememberReminderEnabler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SettingsUi(
    val loaded: Boolean = false,
    val config: ReminderConfig? = null,
    val presets: List<Int> = emptyList(),
    val goals: GoalHistory = GoalHistory(emptyList()),
    val allowed: Boolean = true,
    val permissionAsked: Boolean = false,
)

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val ui: StateFlow<SettingsUi> = combine(
        c.settings.configFlow,
        c.settings.presetsFlow,
        c.repository.goalHistory,
        c.notificationsAllowed,
        c.settings.permissionAsked,
    ) { config, presets, goals, allowed, asked -> SettingsUi(true, config, presets, goals, allowed, asked) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi())

    fun today(): LocalDate = c.time.now().localDate(c.time.zone())

    fun markAsked() = viewModelScope.launch { c.settings.markPermissionAsked() }

    fun setReminders(enabled: Boolean) = viewModelScope.launch {
        c.settings.setRemindersEnabled(enabled)
        c.refreshNotificationPermission()
        c.engine.onConfigChanged()
    }

    fun saveSchedule(interval: Int, start: Int, end: Int) = viewModelScope.launch {
        val current = c.settings.config() ?: return@launch
        c.settings.saveConfig(current.copy(intervalMinutes = interval, startMinute = start, endMinute = end))
        c.engine.onConfigChanged()
    }

    fun saveGoal(goalMl: Int, applyToday: Boolean) = viewModelScope.launch {
        val today = today()
        c.repository.setGoal(goalMl, if (applyToday) today else today.plusDays(1))
        c.engine.onDataChanged()
    }

    fun savePresets(presets: List<Int>) = viewModelScope.launch { c.settings.savePresets(presets) }

    fun clearAll(onDone: () -> Unit) = viewModelScope.launch {
        c.engine.cancelEverything()
        c.repository.clearAll()
        c.settings.clearAll()
        onDone()
    }

    companion object {
        val Factory = viewModelFactory { initializer { SettingsViewModel(container()) } }
    }
}

@Composable
fun SettingsScreen(
    onOpenPrivacy: () -> Unit,
    onCleared: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val undo = LocalUndo.current
    if (!ui.loaded || ui.config == null) {
        Box(modifier.fillMaxSize())
        return
    }
    val config = ui.config!!
    val today = vm.today()

    // Schedule draft — saved explicitly; asks before discarding unsaved edits.
    var interval by rememberSaveable { mutableIntStateOf(config.intervalMinutes) }
    var start by rememberSaveable { mutableIntStateOf(config.startMinute) }
    var end by rememberSaveable { mutableIntStateOf(config.endMinute) }
    var goalText by rememberSaveable { mutableStateOf("") }
    var presetTexts by rememberSaveable { mutableStateOf(ui.presets.joinToString(",")) }
    var presetError by rememberSaveable { mutableStateOf<String?>(null) }
    var goalDialogFor by rememberSaveable { mutableIntStateOf(0) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var pendingLeave by rememberSaveable { mutableStateOf<String?>(null) }

    var syncedConfig by rememberSaveable { mutableStateOf("${config.intervalMinutes},${config.startMinute},${config.endMinute}") }
    LaunchedEffect(config) {
        // Adopt saved settings when they change, but keep an in-progress draft across rotation.
        val saved = "${config.intervalMinutes},${config.startMinute},${config.endMinute}"
        if (saved != syncedConfig) {
            interval = config.intervalMinutes
            start = config.startMinute
            end = config.endMinute
            syncedConfig = saved
        }
    }

    val scheduleDirty = interval != config.intervalMinutes || start != config.startMinute || end != config.endMinute
    val presetsDirty = presetTexts != ui.presets.joinToString(",")
    val goalDirty = goalText.isNotEmpty()
    val dirty = scheduleDirty || presetsDirty || goalDirty
    val scheduleError = Validation.scheduleError(interval, start, end)
    val currentGoal = ui.goals.goalFor(today)
    val tomorrowGoal = ui.goals.goalFor(today.plusDays(1))

    val enableReminders = rememberReminderEnabler(
        permissionAlreadyAsked = ui.permissionAsked,
        onAsked = { vm.markAsked() },
        onDone = { vm.setReminders(true) },
    )

    BackHandler(enabled = dirty) {
        pendingLeave = "back"
        confirmDiscard = true
    }

    Box(modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })

            SwitchRow(
                title = "Reminders",
                subtitle = if (config.enabled && !ui.allowed) "Notifications are not allowed." else "Optional local notifications.",
                checked = config.enabled,
                onChange = { on -> if (on) enableReminders() else vm.setReminders(false) },
            )
            if (config.enabled && !ui.allowed) {
                TextButton(onClick = { openNotificationSettings(context) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Open notification settings (optional)")
                }
            }
            HorizontalDivider()

            ScheduleFields(
                intervalMinutes = interval,
                startMinute = start,
                endMinute = end,
                onInterval = { interval = it },
                onStart = { start = it },
                onEnd = { end = it },
                error = scheduleError,
            )
            if (scheduleDirty) {
                Button(
                    onClick = {
                        vm.saveSchedule(interval, start, end)
                        undo.show("Schedule saved. Next reminder recalculated.", null)
                    },
                    enabled = scheduleError == null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text("Save schedule") }
            }
            HorizontalDivider()

            Text("Daily goal", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(
                buildString {
                    append("Today: ").append(currentGoal?.let { Fmt.ml(it) } ?: "not set")
                    if (tomorrowGoal != currentGoal && tomorrowGoal != null) append(" · From tomorrow: ").append(Fmt.ml(tomorrowGoal))
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            val goalResult = if (goalText.isEmpty()) null else Validation.parseGoal(goalText)
            OutlinedTextField(
                value = goalText,
                onValueChange = { goalText = it.filter { ch -> ch.isDigit() }.take(5) },
                label = { Text("New goal (mL)") },
                singleLine = true,
                isError = goalResult is ParseResult.Error,
                supportingText = { Text((goalResult as? ParseResult.Error)?.message ?: "100–10,000 mL. Earlier days keep their saved goal.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { goalDialogFor = (goalResult as ParseResult.Ok).value },
                enabled = goalResult is ParseResult.Ok,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Change goal") }
            HorizontalDivider()

            Text("Quick portions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            val parts = presetTexts.split(",")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                parts.forEachIndexed { index, value ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { v ->
                            val copy = parts.toMutableList()
                            copy[index] = v.filter { it.isDigit() }.take(4)
                            presetTexts = copy.joinToString(",")
                            presetError = null
                        },
                        label = { Text("mL") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            presetError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (presetsDirty) {
                Button(
                    onClick = {
                        when (val r = Validation.parsePresets(parts)) {
                            is ParseResult.Ok -> {
                                vm.savePresets(r.value)
                                undo.show("Quick portions saved", null)
                            }
                            is ParseResult.Error -> {
                                presetError = r.message
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text("Save quick portions") }
            }
            HorizontalDivider()

            OutlinedButton(onClick = {
                if (dirty) {
                    pendingLeave = "privacy"
                    confirmDiscard = true
                } else {
                    onOpenPrivacy()
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Privacy and data") }
            OutlinedButton(
                onClick = { confirmClear = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Clear All Data") }
            Text(
                "SipTimer works fully offline. Goals and intervals are your own preferences, not medical recommendations.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (goalDialogFor > 0) {
        AlertDialog(
            onDismissRequest = { goalDialogFor = 0 },
            title = { Text("Apply ${Fmt.ml(goalDialogFor)}") },
            text = { Text("Choose when the new goal takes effect. Earlier dates are not changed.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveGoal(goalDialogFor, applyToday = true)
                    goalDialogFor = 0
                    goalText = ""
                }) { Text("Apply today") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.saveGoal(goalDialogFor, applyToday = false)
                    goalDialogFor = 0
                    goalText = ""
                }) { Text("Apply tomorrow") }
            },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false; pendingLeave = null },
            title = { Text("Discard unsaved changes?") },
            text = { Text("Schedule, goal or quick-portion edits have not been saved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    interval = config.intervalMinutes
                    start = config.startMinute
                    end = config.endMinute
                    goalText = ""
                    presetTexts = ui.presets.joinToString(",")
                    when (pendingLeave) {
                        "privacy" -> onOpenPrivacy()
                        "back" -> onBack()
                    }
                    pendingLeave = null
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false; pendingLeave = null }) { Text("Keep editing") }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all data?") },
            text = { Text("All entries, goals, skip records and settings are deleted from this device, reminders are cancelled, and setup starts again. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearAll(onCleared)
                }) { Text("Clear all data") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
