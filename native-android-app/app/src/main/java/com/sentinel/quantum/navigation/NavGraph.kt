package com.sentinel.quantum.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sentinel.quantum.ui.screens.*

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
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
        composable(Screen.About.route) {
            AboutScreen(navController = navController)
        }
        composable(Screen.Compliance.route) {
            ComplianceScreen(navController = navController)
        }
    }
}
