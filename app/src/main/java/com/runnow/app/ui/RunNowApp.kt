package com.runnow.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.runnow.app.WatchScreen
import com.runnow.app.ui.screens.HistoryScreen
import com.runnow.app.ui.screens.HomeScreen
import com.runnow.app.ui.screens.MeScreen
import com.runnow.app.ui.screens.ProgressScreen
import com.runnow.app.ui.screens.SetupScreen

/** Akar UI: tema, lalu pilih Setup atau kerangka 5 tab sesuai [Phase]. */
@Composable
fun RunNowApp(vm: AppViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    RunNowTheme(ui.theme) {
        Surface(modifier = Modifier.fillMaxSize(), color = RunTheme.colors.bg) {
            when (ui.phase) {
                Phase.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = RunTheme.colors.primary)
                }
                Phase.SETUP -> SetupScreen(ui, vm)
                Phase.READY -> MainScaffold(ui, vm)
            }
        }
    }
}

@Composable
private fun MainScaffold(ui: UiState, vm: AppViewModel) {
    val c = RunTheme.colors
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    Scaffold(
        containerColor = c.bg,
        bottomBar = {
            NavigationBar(containerColor = c.card, contentColor = c.grey) {
                for (t in Tab.entries) {
                    NavigationBarItem(
                        selected = current == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.primary,
                            selectedTextColor = c.primary,
                            indicatorColor = c.primaryDim,
                            unselectedIconColor = c.grey,
                            unselectedTextColor = c.grey,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.padding(pad)) {
            composable(Tab.HOME.route) { HomeScreen(ui, vm) }
            composable(Tab.HISTORY.route) { HistoryScreen(ui, vm) }
            composable(Tab.PROGRESS.route) { ProgressScreen(ui, vm) }
            composable(Tab.WATCH.route) { WatchScreen() }
            composable(Tab.ME.route) { MeScreen(ui, vm) }
        }
    }
}
