package com.egrmeister.siptimer

import android.app.Application
import android.content.Context
import android.util.Log
import com.egrmeister.siptimer.data.AppDatabase
import com.egrmeister.siptimer.data.SettingsStore
import com.egrmeister.siptimer.data.WaterRepository
import com.egrmeister.siptimer.domain.ReminderEngine
import com.egrmeister.siptimer.domain.SystemTimeSource
import com.egrmeister.siptimer.domain.TimeSource
import com.egrmeister.siptimer.reminders.AndroidAlarmScheduler
import com.egrmeister.siptimer.reminders.AndroidNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SipTimerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifications.ensureChannel()
        // Restore the saved schedule after process recreation (no restart of the interval).
        container.appScope.launch { container.engine.recover() }
    }

    companion object {
        const val TAG = "SipTimer"
    }
}

/** Manual dependency injection. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val time: TimeSource = SystemTimeSource
    val database: AppDatabase = AppDatabase.build(appContext)
    val settings = SettingsStore(appContext)
    val repository = WaterRepository(database, time, Dispatchers.IO)
    val notifications = AndroidNotifications(appContext)
    val alarms = AndroidAlarmScheduler(appContext)
    val engine = ReminderEngine(
        store = settings,
        progress = repository,
        skips = repository,
        alarms = alarms,
        notifications = notifications,
        time = time,
        log = { Log.i(SipTimerApp.TAG, it) },
    )

    private val _notificationsAllowed = MutableStateFlow(notifications.canPost())
    val notificationsAllowed: StateFlow<Boolean> = _notificationsAllowed.asStateFlow()

    /** Called on every resume: picks up permission or channel changes made in system settings. */
    fun refreshNotificationPermission() {
        val allowed = notifications.canPost()
        if (allowed != _notificationsAllowed.value) {
            _notificationsAllowed.value = allowed
            appScope.launch { engine.recover() }
        }
    }
}
