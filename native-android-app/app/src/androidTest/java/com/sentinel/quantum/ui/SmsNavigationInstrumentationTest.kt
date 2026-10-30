package com.sentinel.quantum.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.sentinel.quantum.SmsComposeActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real Activity navigation on a test profile; no SMS is sent or inserted into a provider. */
class SmsNavigationInstrumentationTest {
    @get:Rule val rule = createEmptyComposeRule()

    private fun conversationsIntent() = Intent(
        InstrumentationRegistry.getInstrumentation().targetContext, SmsComposeActivity::class.java
    ).setAction(Intent.ACTION_MAIN).putExtra(SmsComposeActivity.EXTRA_OPEN_CONVERSATIONS, true)

    @Test fun toolbarBackAfterRecreationKeepsTheDraftAndReturnsToTheList() {
        ActivityScenario.launch<SmsComposeActivity>(conversationsIntent()).use { scenario ->
            rule.onNodeWithText("Écrire").performClick()
            rule.onNodeWithText("Nouveau SMS").assertIsDisplayed()
            rule.onNodeWithText("MMS").performScrollTo().performClick()
            rule.onNodeWithText("Nouveau MMS").performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("phone_core_sms_body").performScrollTo().performTextInput("Brouillon local de test")
            scenario.recreate()
            rule.waitForIdle()
            rule.onNodeWithContentDescription("Retour").performClick()
            rule.onNodeWithText("Conversations").assertIsSelected()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            rule.onNodeWithText("Écrire").performClick()
            rule.onNodeWithTag("phone_core_sms_body").performScrollTo().assertTextContains("Brouillon local de test")
        }
    }

    @Test fun dispatcherBackFromAnInternalComposerReturnsToTheList() {
        ActivityScenario.launch<SmsComposeActivity>(conversationsIntent()).use { scenario ->
            rule.onNodeWithText("Écrire").performClick()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            rule.onNodeWithText("Conversations").assertIsSelected()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test fun externalSendToComposerBackReturnsToItsCaller() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
            .setClass(context, SmsComposeActivity::class.java)
            .putExtra("sms_body", "Brouillon externe sans destinataire")
        ActivityScenario.launch<SmsComposeActivity>(intent).use { scenario ->
            rule.onNodeWithText("Écrire").assertIsSelected()
            rule.onNodeWithContentDescription("Retour").performClick()
            rule.waitUntil(timeoutMillis = 5_000) { scenario.state == Lifecycle.State.DESTROYED }
        }
    }

    @Test fun externalSendToDispatcherBackReturnsToItsCaller() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
            .setClass(context, SmsComposeActivity::class.java)
            .putExtra("sms_body", "Brouillon externe sans destinataire")
        ActivityScenario.launch<SmsComposeActivity>(intent).use { scenario ->
            rule.onNodeWithText("Écrire").assertIsSelected()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            rule.waitUntil(timeoutMillis = 5_000) { scenario.state == Lifecycle.State.DESTROYED }
        }
    }
}
