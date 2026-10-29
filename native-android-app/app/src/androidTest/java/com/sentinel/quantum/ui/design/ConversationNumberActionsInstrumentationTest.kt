package com.sentinel.quantum.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConversationNumberActionsInstrumentationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun actionsFitNarrowHeaderAndForwardOnlyTheDisplayedNumber() {
        var dialed: String? = null
        var added: String? = null
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                SentinelQuantumTheme {
                    Column(Modifier.width(280.dp).verticalScroll(rememberScrollState())) {
                        SentinelCommunicationHeader("Messages", "+33 6 12 34 56 78", "Source locale", compact = true) {
                            ConversationNumberActions("+33 6 12 34 56 78", { dialed = it }, { added = it })
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag("conversation-call-number").performScrollTo().performClick()
        rule.onNodeWithTag("conversation-add-contact").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals("+33612345678", dialed)
            assertEquals("+33612345678", added)
        }
        val header = rule.onNodeWithTag("d1-communication-header").fetchSemanticsNode().boundsInRoot
        for (tag in listOf("conversation-call-number", "conversation-add-contact")) {
            val button = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue(button.left >= header.left && button.right <= header.right)
        }
    }

    @Test fun unsupportedSenderShowsAReasonInsteadOfInactiveActions() {
        rule.setContent {
            SentinelQuantumTheme { ConversationNumberActions("BANQUE", {}, {}) }
        }
        rule.onNodeWithTag("conversation-call-number").assertDoesNotExist()
        rule.onNodeWithTag("conversation-add-contact").assertDoesNotExist()
        rule.onNodeWithTag("conversation-number-unavailable").assertIsDisplayed()
    }
}
