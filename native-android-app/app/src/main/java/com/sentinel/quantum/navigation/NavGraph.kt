package com.sentinel.quantum.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sentinel.quantum.ui.screens.*

@Composable
fun NavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = "blocking"
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // Force l'appel de l'écran avec le formulaire et la liste Room
        composable("blocking") { CallBlockingScreen() }
        composable("logs") { LocalLogsScreen() }
        composable("osint") { OsintFeedScreen() }
        composable("sms") { SmsScannerScreen() }
        composable("audit") { SecurityAuditScreen() }
        composable("phone") { PhoneSecurityScreen() }
        composable("email") { EmailSecurityScreen() }
    }
}
