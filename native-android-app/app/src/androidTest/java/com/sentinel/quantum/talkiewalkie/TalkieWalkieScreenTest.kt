package com.sentinel.quantum.talkiewalkie

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class TalkieWalkieScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun listeningShowsHoldToTalkWithoutTransmitClaim() {
        composeRule.setContent {
            TalkieWalkieScreen(
                uiState = state(TalkieWalkieState.LISTENING),
                actions = NoOpActions
            )
        }

        composeRule.onNodeWithText("Maintenir pour parler").assertIsDisplayed()
        composeRule.onAllNodesWithText("Vous parlez").assertCountEquals(0)
    }

    @Test
    fun requestingFloorNeverDisplaysTransmitTruth() {
        composeRule.setContent {
            TalkieWalkieScreen(
                uiState = state(TalkieWalkieState.REQUESTING_FLOOR),
                actions = NoOpActions
            )
        }

        composeRule.onNodeWithText("Demande du canal…").assertIsDisplayed()
        composeRule.onAllNodesWithText("Vous parlez").assertCountEquals(0)
    }

    @Test
    fun onlyAuthoritativeTransmittingShowsTransmitTruth() {
        composeRule.setContent {
            TalkieWalkieScreen(
                uiState = state(TalkieWalkieState.TRANSMITTING),
                actions = NoOpActions
            )
        }

        composeRule.onNodeWithText("Vous parlez").assertIsDisplayed()
    }

    @Test
    fun pressAndReleaseRouteThroughActions() {
        val actions = RecordingActions()
        composeRule.setContent {
            TalkieWalkieScreen(
                uiState = state(TalkieWalkieState.LISTENING),
                actions = actions
            )
        }

        composeRule.onNodeWithContentDescription("Bouton pousser-pour-parler")
            .performTouchInput {
                down(center)
                up()
            }

        composeRule.runOnIdle {
            assertEquals(1, actions.pressCount)
            assertEquals(1, actions.releaseCount)
        }
    }

    @Test
    fun composableSurfaceDoesNotOwnTransport() {
        assertFalse(
            TalkieWalkieScreenActions::class.java.declaredFields.any { field ->
                TalkieWalkieTransport::class.java.isAssignableFrom(field.type)
            }
        )
    }

    private fun state(pttState: TalkieWalkieState) = TalkieWalkieUiState(
        pttState = pttState,
        networkQuality = NetworkQuality.GOOD,
        channelName = "Canal Alpha",
        participantCount = 3,
        activeSpeakerName = null,
        audioRouteLabel = "Téléphone"
    )

    private object NoOpActions : TalkieWalkieScreenActions {
        override fun onPressToTalk() = Unit
        override fun onReleaseToTalk() = Unit
        override fun onAudioRouteClick() = Unit
    }

    private class RecordingActions : TalkieWalkieScreenActions {
        var pressCount = 0
        var releaseCount = 0

        override fun onPressToTalk() {
            pressCount += 1
        }

        override fun onReleaseToTalk() {
            releaseCount += 1
        }

        override fun onAudioRouteClick() = Unit
    }
}
