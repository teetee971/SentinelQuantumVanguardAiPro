package com.sentinel.quantum.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sentinel.quantum.data.ThemeMode
import com.sentinel.quantum.ui.screens.CallBlockingScreen
import com.sentinel.quantum.ui.screens.CallFilterHistoryScreen
import com.sentinel.quantum.ui.screens.CommercialPhoneCoreHomeScreen
import com.sentinel.quantum.ui.screens.CommercialPhoneCoreProtectionScreen
import com.sentinel.quantum.ui.screens.CommercialPhoneCoreSettingsScreen
import com.sentinel.quantum.ui.screens.CommunicationsHubScreen
import com.sentinel.quantum.ui.screens.LocalLogsScreen
import com.sentinel.quantum.ui.screens.NumberSearchScreen
import com.sentinel.quantum.ui.screens.PhoneProtectionListsScreen

@Composable
internal fun CommercialPhoneCoreNavGraph(
    navController: NavHostController,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) { CommercialPhoneCoreHomeScreen(navController) }
        composable(Screen.CommunicationsHub.route) { CommunicationsHubScreen(navController) }
        composable(Screen.PhoneSecurity.route) { CommercialPhoneCoreProtectionScreen(navController) }
        composable(Screen.Settings.route) {
            CommercialPhoneCoreSettingsScreen(navController, themeMode, onThemeModeChange)
        }
        composable(Screen.Search.route) { NumberSearchScreen() }
        composable(Screen.CallBlocking.route) { CallBlockingScreen(navController) }
        composable(Screen.PhoneLists.route) { PhoneProtectionListsScreen(navController) }
        composable(Screen.CallFilterHistory.route) { CallFilterHistoryScreen(navController) }
        composable(Screen.LocalLogs.route) { LocalLogsScreen(navController) }
    }
}
