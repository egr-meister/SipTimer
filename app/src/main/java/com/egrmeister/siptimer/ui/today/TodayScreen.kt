package com.egrmeister.siptimer.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.egrmeister.siptimer.SipTimerApp
import com.egrmeister.siptimer.data.WaterEntryEntity
import com.egrmeister.siptimer.domain.CountdownPresenter
import com.egrmeister.siptimer.domain.CountdownState
import com.egrmeister.siptimer.ui.common.Fmt
import com.egrmeister.siptimer.ui.common.LocalUndo
import com.egrmeister.siptimer.ui.common.openNotificationSettings
import com.egrmeister.siptimer.ui.theme.SipColors
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(
    onAddWater: () -> Unit,
    onEditEntry: (Long) -> Unit,
    modifier: Modifier = Modifier,
    vm: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val undo = LocalUndo.current
    val context = LocalContext.current
    val container = (context.applicationContext as SipTimerApp).container
    val actions = remember(container) { EntryActions(container) }
    val scope = rememberCoroutineScope()

    if (!ui.loaded) {
        Box(modifier.fillMaxSize())
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(12.dp))
                CountdownConsole(
                    ui = ui,
                    onAddWater = onAddWater,
                    onSkip = {
                        vm.skip { replacement ->
                            undo.show(
                                "Reminder skipped. Next: ${CountdownPresenter.formatWhen(replacement, ui.zone, ui.today)}",
                                null,
                            )
                        }
                    },
                    onOpenSettings = { openNotificationSettings(context) },
                )
                Spacer(Modifier.height(20.dp))
                VolumeSection(ui)
                Spacer(Modifier.height(12.dp))
                QuickPortions(ui.presets) { amount ->
                    vm.quickAdd(amount) { id ->
                        undo.show("Added ${Fmt.ml(amount)}") { actions.undoAdd(id) }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Today’s entries",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(4.dp))
                if (ui.entries.isEmpty()) {
                    Text(
                        "No records yet today.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
        items(ui.entries, key = { it.id }) { entry ->
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp)) {
                EntryRow(
                    entry = entry,
                    onEdit = { onEditEntry(entry.id) },
                    onDelete = {
                        scope.launch {
                            actions.delete(entry)
                            undo.show("Deleted ${Fmt.ml(entry.amountMl)}") { actions.restore(entry) }
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CountdownConsole(
    ui: TodayUi,
    onAddWater: () -> Unit,
    onSkip: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val fraction = (ui.countdown as? CountdownState.Active)?.elapsedFraction
            TimeArc(fraction = fraction, modifier = Modifier.fillMaxWidth().height(36.dp))
            Spacer(Modifier.height(8.dp))
            // Not a live region: TalkBack reads it on focus instead of announcing every minute.
            Text(
                ui.headline,
                style = if (ui.countdown is CountdownState.Active) {
                    MaterialTheme.typography.displaySmall
                } else {
                    MaterialTheme.typography.headlineSmall
                },
                color = SipColors.Navy,
                modifier = Modifier.semantics { heading() },
            )
            ui.scheduledLine?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ui.statusLine.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(ui.statusLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ui.showSettingsLink) {
                TextButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Open notification settings (optional)")
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAddWater,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  Add Water", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSkip,
                enabled = ui.canSkip,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Skip Next Reminder") }
        }
    }
}

/** Restrained time-progress arc: pale blue track, teal elapsed part. Purely decorative. */
@Composable
private fun TimeArc(fraction: Float?, modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val stroke = 5.dp.toPx()
        val width = size.width * 0.8f
        val left = (size.width - width) / 2f
        val arcHeight = size.height * 2f
        val topLeft = Offset(left, stroke)
        val arcSize = Size(width, arcHeight)
        drawArc(
            color = SipColors.PaleBlue,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (fraction != null && fraction > 0f) {
            drawArc(
                color = SipColors.Teal,
                startAngle = 200f,
                sweepAngle = 140f * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun VolumeSection(ui: TodayUi) {
    val goalText = ui.goalMl?.let { "${Fmt.ml(ui.totalMl)} of ${Fmt.ml(it)}" } ?: Fmt.ml(ui.totalMl)
    val percent = ui.percent?.let { " · $it%" } ?: ""
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "Recorded $goalText$percent" }) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(goalText, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (percent.isNotEmpty()) {
                Text(percent.removePrefix(" · "), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(SipColors.PaleBlue, RoundedCornerShape(2.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(ui.progress)
                    .height(4.dp)
                    .background(SipColors.Teal, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickPortions(presets: List<Int>, onAdd: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        presets.forEach { amount ->
            FilledTonalButton(
                onClick = { onAdd(amount) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Quick add ${Fmt.ml(amount)}" },
            ) { Text("+${Fmt.ml(amount)}") }
        }
    }
}

@Composable
fun EntryRow(entry: WaterEntryEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = "Edit entry", onClick = onEdit),
    ) {
        Text(entry.loggedLocalTime, style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(min = 72.dp))
        Text(Fmt.ml(entry.amountMl), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete ${Fmt.ml(entry.amountMl)} at ${entry.loggedLocalTime}")
        }
    }
}
