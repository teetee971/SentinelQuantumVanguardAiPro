package com.sentinel.quantum.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.rememberNavController
import com.sentinel.quantum.navigation.NavGraph
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import org.junit.Rule
import org.junit.Test

class HomeVisualContractTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeExposesTruthfulCommandCenterSemantics() {
        composeRule.setContent {
            SentinelQuantumTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController, startDestination = Screen.Home.route)
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_security_hero").assertExists()
        composeRule.onNodeWithTag("home_module_phone_core").performScrollTo().assertExists()
        composeRule.onNodeWithTag("home_module_vpn").performScrollTo().assertExists()
        composeRule.onNodeWithTag("home_module_network").performScrollTo().assertExists()
        composeRule.onNodeWithTag("home_module_collective_defense").performScrollTo().assertExists()
        composeRule.onNodeWithTag("home_primary_action").performScrollTo().assertExists()
    }
}
