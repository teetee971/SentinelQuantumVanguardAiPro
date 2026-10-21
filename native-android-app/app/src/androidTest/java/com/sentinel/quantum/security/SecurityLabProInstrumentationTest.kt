package com.sentinel.quantum.security

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sentinel.quantum.ui.screens.SecurityLabProScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android UI smoke test. Does not select APKs, install apps, trigger calls or send SMS.
 * Compile in CI and execute only on an authorized emulator/test device.
 */
@RunWith(AndroidJUnit4::class)
class SecurityLabProInstrumentationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun securityLabOpensWithSafePickerControls() {
        rule.setContent {
            MaterialTheme {
                SecurityLabProScreen(navController = rememberNavController())
            }
        }
        rule.onNodeWithText("Security Lab Pro").assertExists()
        rule.onNodeWithText("Choisir APK de référence").assertExists()
        rule.onNodeWithText("Choisir APK à comparer").assertExists()
        rule.onNodeWithText("Comparer les deux APK").assertExists().assertIsNotEnabled()
    }
}
