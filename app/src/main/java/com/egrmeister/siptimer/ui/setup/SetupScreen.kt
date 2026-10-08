package com.egrmeister.siptimer.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.egrmeister.siptimer.domain.ParseResult
import com.egrmeister.siptimer.domain.ReminderConfig
import com.egrmeister.siptimer.domain.Validation
import com.egrmeister.siptimer.ui.common.ScheduleFields
import com.egrmeister.siptimer.ui.common.SwitchRow
import com.egrmeister.siptimer.ui.common.container
import com.egrmeister.siptimer.ui.common.rememberReminderEnabler
import kotlinx.coroutines.launch

class SetupViewModel(private val c: AppContainer) : ViewModel() {
    val permissionAsked = c.settings.permissionAsked

    fun markAsked() = viewModelScope.launch { c.settings.markPermissionAsked() }

    fun save(goalMl: Int, config: ReminderConfig, onDone: () -> Unit) {
        viewModelScope.launch {
            c.repository.setGoal(goalMl, c.repository.todayDate())
            c.settings.saveConfig(config, markSetupDone = true)
            c.refreshNotificationPermission()
            c.engine.onConfigChanged()
            onDone()
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { SetupViewModel(container()) } }
    }
}

@Composable
fun SetupScreen(onFinished: () -> Unit, vm: SetupViewModel = viewModel(factory = SetupViewModel.Factory)) {
    val example = ReminderConfig.EXAMPLE
    var goalText by rememberSaveable { mutableStateOf(ReminderConfig.EXAMPLE_GOAL_ML.toString()) }
    var interval by rememberSaveable { mutableIntStateOf(example.intervalMinutes) }
    var start by rememberSaveable { mutableIntStateOf(example.startMinute) }
    var end by rememberSaveable { mutableIntStateOf(example.endMinute) }
    var reminders by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    val asked by vm.permissionAsked.collectAsState(initial = false)

    val goalResult = Validation.parseGoal(goalText)
    val scheduleError = Validation.scheduleError(interval, start, end)
    val valid = goalResult is ParseResult.Ok && scheduleError == null

    val doSave = {
        val goal = (goalResult as ParseResult.Ok).value
        vm.save(goal, ReminderConfig(interval, start, end, reminders), onFinished)
    }
    val enableThenSave = rememberReminderEnabler(
        permissionAlreadyAsked = asked,
        onAsked = { vm.markAsked() },
        onDone = { doSave() },
    )

    Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Set up SipTimer", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Choose your own goal and timing. The values below are editable examples, not recommendations.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = goalText,
                onValueChange = { goalText = it.filter { ch -> ch.isDigit() }.take(5) },
                label = { Text("Daily goal (mL)") },
                singleLine = true,
                isError = goalResult is ParseResult.Error,
                supportingText = {
                    Text((goalResult as? ParseResult.Error)?.message ?: "100–10,000 mL")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
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
            HorizontalDivider()
            SwitchRow(
                title = "Enable reminders",
                subtitle = "Optional. You can log water without reminders.",
                checked = reminders,
                onChange = { reminders = it },
            )
            Button(
                onClick = {
                    if (!saving && valid) {
                        saving = true
                        if (reminders) enableThenSave() else doSave()
                    }
                },
                enabled = valid && !saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Save and start") }
        }
    }
}
