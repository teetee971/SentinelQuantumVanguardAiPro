package com.sentinel.quantum.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import com.sentinel.quantum.data.ThemeMode
import androidx.navigation.navArgument
import com.sentinel.quantum.ui.screens.*

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Home.route,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        composable(Screen.OsintFeed.route) {
            OsintFeedScreen(navController = navController)
        }
        composable(Screen.SecurityAudit.route) {
            SecurityAuditScreen(navController = navController)
        }
        composable(Screen.LocalLogs.route) {
            LocalLogsScreen(navController = navController)
        }
        composable(Screen.PhoneSecurity.route) {
            PhoneSecurityScreen(navController = navController)
        }
        composable(Screen.CallBlocking.route) {
            CallBlockingScreen(navController = navController)
        }
        composable(Screen.EmailSecurity.route) {
            EmailSecurityScreen(navController = navController)
        }
        composable(Screen.AppPermissionAnalyzer.route) {
            AppPermissionAnalyzerScreen(navController = navController)
        }
        composable(Screen.NetworkSurveillance.route) {
            NetworkSurveillanceScreen(navController = navController)
        }
        composable(Screen.About.route) {
            AboutScreen(navController = navController)
        }
        composable(Screen.Compliance.route) {
            ComplianceScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                navController = navController,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange
            )
        }
        composable(
            route = Screen.OsintDetail.route,
            arguments = listOf(navArgument(Screen.ARG_ITEM_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            OsintDetailScreen(
                navController = navController,
                itemId = backStackEntry.arguments?.getString(Screen.ARG_ITEM_ID).orEmpty()
            )
        }
    }
}
