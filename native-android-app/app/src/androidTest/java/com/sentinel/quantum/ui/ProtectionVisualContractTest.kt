package com.sentinel.quantum.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.navigation.compose.rememberNavController
import com.sentinel.quantum.ui.screens.PhoneSecurityScreen
import org.junit.Rule
import org.junit.Test

class ProtectionVisualContractTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun protectionLandingExposesPremiumCommandCenterAndPrimaryActions() {
        composeRule.setContent {
            MaterialTheme {
                PhoneSecurityScreen(navController = rememberNavController())
            }
        }

        composeRule.onNodeWithTag("protection-command-center").assertIsDisplayed()
        composeRule.onNodeWithTag("protection-analysis-action").assertExists()
        composeRule.onNodeWithTag("protection-app-permissions-action").assertExists()
        composeRule.onNodeWithTag("protection-network-action").assertExists()
        composeRule.onNodeWithTag("protection-vpn-action").assertExists()
    }

    @Test
    fun protectionPrimaryActionsRemainInSemanticsAtLargeFontScale() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.8f)
            ) {
                MaterialTheme {
                    PhoneSecurityScreen(navController = rememberNavController())
                }
            }
        }

        composeRule.onNodeWithTag("protection-command-center").assertExists()
        composeRule.onNodeWithTag("protection-analysis-action").assertExists()
        composeRule.onNodeWithTag("protection-app-permissions-action").assertExists()
        composeRule.onNodeWithTag("protection-network-action").assertExists()
        composeRule.onNodeWithTag("protection-vpn-action").assertExists()
    }
}
