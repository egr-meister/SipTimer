package com.egrmeister.siptimer.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Returns a function that turns reminders on. On Android 13+ it asks for POST_NOTIFICATIONS only
 * at this moment, and only once: after a denial the app never prompts again and instead shows the
 * blocked state with an optional link to system settings. [onDone] always runs, so tracking and
 * scheduling continue whatever the answer.
 */
@Composable
fun rememberReminderEnabler(
    permissionAlreadyAsked: Boolean,
    onAsked: () -> Unit,
    onDone: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentOnDone = rememberUpdatedState(onDone)
    val currentOnAsked = rememberUpdatedState(onAsked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        currentOnAsked.value()
        currentOnDone.value()
    }
    return {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission && !permissionAlreadyAsked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onDone()
        }
    }
}
