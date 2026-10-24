package com.sentinel.quantum.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
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
        assertProtectionAction("protection-analysis-action")
        assertProtectionAction("protection-app-permissions-action")
        assertProtectionAction("protection-network-action")
        assertProtectionAction("protection-vpn-action")
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

        composeRule.onNodeWithTag("protection-command-center").assertIsDisplayed()
        assertProtectionAction("protection-analysis-action")
        assertProtectionAction("protection-app-permissions-action")
        assertProtectionAction("protection-network-action")
        assertProtectionAction("protection-vpn-action")
    }

    private fun assertProtectionAction(tag: String) {
        composeRule.onNodeWithTag(tag)
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }
}
