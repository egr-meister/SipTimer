package com.egrmeister.siptimer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.egrmeister.siptimer.AppContainer
import com.egrmeister.siptimer.ui.common.LocalUndo
import com.egrmeister.siptimer.ui.common.UndoMessenger
import com.egrmeister.siptimer.ui.entry.EntryEditorScreen
import com.egrmeister.siptimer.ui.history.DayDetailScreen
import com.egrmeister.siptimer.ui.history.HistoryScreen
import com.egrmeister.siptimer.ui.settings.PrivacyScreen
import com.egrmeister.siptimer.ui.settings.SettingsScreen
import com.egrmeister.siptimer.ui.setup.SetupScreen
import com.egrmeister.siptimer.ui.theme.SipColors
import com.egrmeister.siptimer.ui.today.TodayScreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

object Routes {
    const val SETUP = "setup"
    const val TODAY = "today"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val DAY = "day/{date}"
    const val ENTRY = "entry?id={id}"

    fun day(date: LocalDate) = "day/$date"
    fun entry(id: Long?) = if (id == null) "entry" else "entry?id=$id"
}

private val TABS = listOf(Routes.TODAY to "Today", Routes.HISTORY to "History", Routes.SETTINGS to "Settings")

@Composable
fun SipTimerRoot(container: AppContainer, openTodayRequests: StateFlow<Int>) {
    val setupDone by container.settings.setupDone.collectAsStateWithLifecycle<Boolean?>(initialValue = null)
    val done = setupDone ?: run {
        Box(Modifier.fillMaxSize())
        return
    }
    val nav = rememberNavController()
    // Decided once: later changes (setup finished, data cleared) navigate explicitly.
    val startDestination = remember { if (done) Routes.TODAY else Routes.SETUP }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undo = remember(snackbar, scope) {
        UndoMessenger { message, onUndo ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(
                    message = message,
                    actionLabel = if (onUndo != null) "Undo" else null,
                    withDismissAction = onUndo == null,
                    duration = if (onUndo != null) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed && onUndo != null) {
                    container.appScope.launch { onUndo() }
                }
            }
        }
    }

    val openToday by openTodayRequests.collectAsStateWithLifecycle()
    LaunchedEffect(openToday, done) {
        if (openToday > 0 && done) {
            nav.navigate(Routes.TODAY) {
                popUpTo(Routes.TODAY) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showTabs = currentRoute in setOf(Routes.TODAY, Routes.HISTORY, Routes.SETTINGS, Routes.DAY, Routes.PRIVACY)

    CompositionLocalProvider(LocalUndo provides undo) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (showTabs) NavigationStrip(nav, currentRoute)
            },
            containerColor = MaterialTheme.colorScheme.background,
            // Edge-to-edge: system bars and display cutouts on every side; the IME is handled per form.
            contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = startDestination,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            ) {
                composable(Routes.SETUP) {
                    SetupScreen(onFinished = {
                        nav.navigate(Routes.TODAY) {
                            popUpTo(nav.graph.id) { inclusive = true }
                        }
                    })
                }
                composable(Routes.TODAY) {
                    TodayScreen(
                        onAddWater = { nav.navigate(Routes.entry(null)) },
                        onEditEntry = { nav.navigate(Routes.entry(it)) },
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(onOpenDay = { nav.navigate(Routes.day(it)) })
                }
                composable(Routes.DAY, arguments = listOf(navArgument("date") { type = NavType.StringType })) { entry ->
                    val date = entry.arguments?.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        ?: LocalDate.now()
                    DayDetailScreen(
                        date = date,
                        onBack = { nav.popBackStack() },
                        onChangeDate = { d ->
                            nav.navigate(Routes.day(d)) {
                                popUpTo(Routes.DAY) { inclusive = true }
                            }
                        },
                        onEditEntry = { nav.navigate(Routes.entry(it)) },
                    )
                }
                composable(
                    Routes.ENTRY,
                    arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
                ) { entry ->
                    val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
                    EntryEditorScreen(entryId = id, onClose = { nav.popBackStack() })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onOpenPrivacy = { nav.navigate(Routes.PRIVACY) },
                        onCleared = {
                            nav.navigate(Routes.SETUP) {
                                popUpTo(nav.graph.id) { inclusive = true }
                            }
                        },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Routes.PRIVACY) {
                    PrivacyScreen(onBack = { nav.popBackStack() })
                }
            }
        }
    }
}

/** Compact navigation strip: Today, History, Settings. */
@Composable
private fun NavigationStrip(nav: NavHostController, currentRoute: String?) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().selectableGroup()) {
                TABS.forEach { (route, label) ->
                    val selected = when (route) {
                        Routes.HISTORY -> currentRoute == Routes.HISTORY || currentRoute == Routes.DAY
                        Routes.SETTINGS -> currentRoute == Routes.SETTINGS || currentRoute == Routes.PRIVACY
                        else -> currentRoute == route
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = {
                                    if (!selected || currentRoute != route) {
                                        nav.navigate(route) {
                                            popUpTo(Routes.TODAY) { saveState = route != Routes.TODAY }
                                            launchSingleTop = true
                                        }
                                    }
                                },
                            ),
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) SipColors.Teal else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }
}
