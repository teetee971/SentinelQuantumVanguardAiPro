package com.sentinel.quantum.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sentinel.quantum.ui.screens.AboutScreen
import com.sentinel.quantum.ui.screens.ComplianceScreen
import com.sentinel.quantum.ui.screens.HomeScreen
import com.sentinel.quantum.ui.screens.OsintFeedScreen

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
        composable(Screen.About.route) {
            AboutScreen(navController = navController)
        }
        composable(Screen.Compliance.route) {
            ComplianceScreen(navController = navController)
        }
    }
}
