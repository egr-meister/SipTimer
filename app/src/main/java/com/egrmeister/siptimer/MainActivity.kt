package com.egrmeister.siptimer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.egrmeister.siptimer.ui.SipTimerRoot
import com.egrmeister.siptimer.ui.theme.SipTimerTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Incremented whenever a reminder notification is tapped, so the UI navigates to Today. */
    private val openTodayRequests = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent?.action == ACTION_OPEN_TODAY) openTodayRequests.value++
        val container = (application as SipTimerApp).container
        setContent {
            SipTimerTheme {
                SipTimerRoot(container = container, openTodayRequests = openTodayRequests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_TODAY) openTodayRequests.value++
    }

    override fun onResume() {
        super.onResume()
        (application as SipTimerApp).container.refreshNotificationPermission()
    }

    companion object {
        const val ACTION_OPEN_TODAY = "com.egrmeister.siptimer.action.OPEN_TODAY"
    }
}
