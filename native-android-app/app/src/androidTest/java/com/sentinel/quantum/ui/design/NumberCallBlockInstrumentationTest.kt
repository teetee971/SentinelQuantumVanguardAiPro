package com.sentinel.quantum.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sentinel.quantum.security.CallScreeningActivationPolicy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Actual confirmation component, no real rules/provider/radio writes. */
class NumberCallBlockInstrumentationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun cancellationNeverMutatesAndConfirmationRunsOnce() {
        var confirmed = 0
        rule.setContent { MaterialTheme {
            NumberCallBlockAction("fixture", "+33612345678", false, false,
                CallScreeningActivationPolicy.State.AVAILABLE_NOT_HELD, null) { confirmed++ }
        } }
        rule.onNodeWithText("Bloquer les appels de ce numéro").performClick()
        rule.runOnIdle { assertEquals(0, confirmed) }
        rule.onNodeWithText("Annuler").performClick()
        rule.runOnIdle { assertEquals(0, confirmed) }
        rule.onNodeWithText("Bloquer les appels de ce numéro").performClick()
        rule.onNodeWithText("Confirmer le blocage d’appels").performClick()
        rule.runOnIdle { assertEquals(1, confirmed) }
    }

    @Test fun changingSelectedOwnerDropsPendingConfirmation() {
        val owner = mutableStateOf("A")
        var confirmed = 0
        rule.setContent { MaterialTheme {
            NumberCallBlockAction(owner.value, if (owner.value == "A") "+33612345678" else "+33687654321",
                false, false, null, null) { confirmed++ }
        } }
        rule.onNodeWithText("Bloquer les appels de ce numéro").performClick()
        rule.runOnIdle { owner.value = "B" }
        rule.onNodeWithText("Confirmer le blocage d’appels").assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, confirmed) }
    }

    @Test fun recordedRuleIsDistinctFromAndroidFilteringRole() {
        rule.setContent { MaterialTheme {
            NumberCallBlockAction("fixture", "+33612345678", true, false,
                CallScreeningActivationPolicy.State.AVAILABLE_NOT_HELD, null) { error("must not add twice") }
        } }
        rule.onNodeWithText("Présent dans votre liste de blocage").assertIsNotEnabled()
        rule.onNodeWithText("Filtrage Android non attribué à Sentinel.").assertIsDisplayed()
    }
}
