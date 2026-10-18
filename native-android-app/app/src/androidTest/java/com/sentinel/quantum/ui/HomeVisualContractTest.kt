package com.sentinel.quantum.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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

        composeRule.onNodeWithTag("home_primary_action")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        composeRule.onNodeWithTag("home_security_hero").assertExists()
        composeRule.onNodeWithTag("sentinel_quantum_core").assertExists()
        listOf(
            "home_module_phone_core",
            "home_module_vpn",
            "home_module_network",
            "home_module_collective_defense"
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag)
                .performScrollTo()
                .assertIsDisplayed()
                .assertHasClickAction()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }
        composeRule.onNodeWithTag("home_activity_summary").performScrollTo().assertExists()
    }

    @Test
    fun primaryActionIsVisibleWithoutScrollingOnNarrowLargeFontHome() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                SentinelQuantumTheme {
                    Box(Modifier.width(320.dp)) {
                        val navController = rememberNavController()
                        NavGraph(navController = navController, startDestination = Screen.Home.route)
                    }
                }
            }
        }

        composeRule.onNodeWithTag("home_primary_action").assertIsDisplayed().assertHasClickAction()
    }
}
