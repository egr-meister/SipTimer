package com.egrmeister.siptimer.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.unit.dp
import com.egrmeister.siptimer.domain.Limits
import java.time.LocalTime

/** Interval stepper + active-hours pickers, shared by Setup and Settings. */
@Composable
fun ScheduleFields(
    intervalMinutes: Int,
    startMinute: Int,
    endMinute: Int,
    onInterval: (Int) -> Unit,
    onStart: (Int) -> Unit,
    onEnd: (Int) -> Unit,
    error: String?,
) {
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Reminder interval", style = MaterialTheme.typography.titleMedium)
        IntervalStepper(
            minutes = intervalMinutes,
            onChange = onInterval,
            min = Limits.MIN_INTERVAL,
            max = Limits.MAX_INTERVAL,
            step = Limits.INTERVAL_STEP,
        )
        Text("Active hours", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ValueButton("Start", Fmt.minuteOfDay(startMinute), { picking = "start" }, Modifier.weight(1f))
            ValueButton("End", Fmt.minuteOfDay(endMinute), { picking = "end" }, Modifier.weight(1f))
        }
        Text(
            "Reminders are only delivered between start (included) and end (excluded), on the same day.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
    when (picking) {
        "start" -> TimePickerDialog(
            title = "Active hours start",
            initial = LocalTime.of(startMinute / 60, startMinute % 60),
            onPick = { onStart(it.hour * 60 + it.minute) },
            onDismiss = { picking = null },
        )
        "end" -> TimePickerDialog(
            title = "Active hours end",
            initial = if (endMinute >= 24 * 60) LocalTime.of(23, 59) else LocalTime.of(endMinute / 60, endMinute % 60),
            onPick = { onEnd(it.hour * 60 + it.minute) },
            onDismiss = { picking = null },
        )
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .semantics { role = Role.Switch }
            .padding(vertical = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
