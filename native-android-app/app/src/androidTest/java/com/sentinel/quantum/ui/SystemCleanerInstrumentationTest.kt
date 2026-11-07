package com.sentinel.quantum.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import com.sentinel.quantum.ui.screens.SystemCleanerScreen
import org.junit.Rule
import org.junit.Test

/** Never confirms cleanup or modifies phone data. Requires physical/emulated execution. */
class SystemCleanerInstrumentationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun cleanupIsDisabledBeforeScanAndSelection() {
        compose.setContent { SystemCleanerScreen(rememberNavController()) }
        compose.onNodeWithText("Nettoyer la sélection").assertIsNotEnabled()
    }
    @Test fun initialScreenRequiresExplicitScan() {
        compose.setContent { SystemCleanerScreen(rememberNavController()) }
        compose.onNodeWithText("Scanner les fichiers temporaires").assertIsDisplayed()
        compose.onNodeWithText("Aucun scan effectué.").assertIsDisplayed()
    }
}
