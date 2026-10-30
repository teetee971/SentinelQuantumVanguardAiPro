package com.sentinel.quantum.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Actual confirmation UI, synthetic numbers; never opens a browser. */
class ContactPhoneWebSearchInstrumentationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun openingAndCancellingPreviewNeverLaunchesSearch() {
        var launches = 0
        rule.setContent { MaterialTheme { ContactPhoneWebSearchAction("fixture-A", "+12025550123", false, null) { launches++ } } }
        rule.runOnIdle { assertEquals(0, launches) }
        rule.onNodeWithText("Rechercher ce numéro sur le web").performClick()
        rule.onNodeWithText("+12025550123").assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, launches) }
        rule.onNodeWithText("Annuler").performClick()
        rule.runOnIdle { assertEquals(0, launches) }
        rule.onNodeWithText("Rechercher ce numéro sur le web").performClick()
        rule.onNodeWithText("Ouvrir la recherche").performClick()
        rule.runOnIdle { assertEquals(1, launches) }
    }

    @Test fun changingContactOrNumberDiscardsPendingConfirmation() {
        var owner by mutableStateOf("fixture-A")
        var number by mutableStateOf("+12025550123")
        var launches = 0
        rule.setContent { MaterialTheme { ContactPhoneWebSearchAction(owner, number, false, null) { launches++ } } }
        rule.onNodeWithText("Rechercher ce numéro sur le web").performClick()
        rule.runOnIdle { owner = "fixture-B" }
        rule.onNodeWithText("Ouvrir la recherche").assertDoesNotExist()
        rule.onNodeWithText("Rechercher ce numéro sur le web").performClick()
        rule.runOnIdle { number = "+12025550124" }
        rule.onNodeWithText("Ouvrir la recherche").assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, launches) }
    }
}
