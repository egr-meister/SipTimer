package com.egrmeister.siptimer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Bundled privacy information — shown offline, no remote content. */
@Composable
fun PrivacyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Settings") }
                Text("Privacy and data", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            }
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrivacyItem(
                    "Stays on this device",
                    "Water entries, goals, skipped-reminder records and settings are stored only in this app's private storage. " +
                        "SipTimer has no account, no server, no analytics, no ads and no internet permission.",
                )
                PrivacyItem(
                    "No backup or transfer",
                    "Cloud backup and device-to-device transfer are disabled for this app, so your records are not copied elsewhere.",
                )
                PrivacyItem(
                    "Permissions",
                    "Notifications (optional, only if you turn on reminders) and running after restart (to restore a scheduled reminder). Nothing else.",
                )
                PrivacyItem(
                    "Reminders are approximate",
                    "Android may deliver a reminder later than its scheduled time to save battery. If you force-stop the app, " +
                        "Android removes its scheduled reminders until you open SipTimer again.",
                )
                PrivacyItem(
                    "Not medical advice",
                    "Goals and intervals are preferences you choose. SipTimer does not ask for personal or medical information.",
                )
                PrivacyItem(
                    "Deleting data",
                    "Settings → Clear All Data deletes everything and cancels reminders. Uninstalling the app also removes all data.",
                )
            }
        }
    }
}

@Composable
private fun PrivacyItem(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
