package com.sentinel.quantum.talkiewalkie

import org.junit.Assert.assertEquals
import org.junit.Test

class TalkieWalkieStateMachineTest {
    private val machine = TalkieWalkieStateMachine()

    @Test
    fun legalTransmitPathRequiresFloorRequest() {
        var state = TalkieWalkieState.DISCONNECTED
        state = machine.transition(state, TalkieWalkieEvent.ConnectRequested)
        assertEquals(TalkieWalkieState.CONNECTING, state)

        state = machine.transition(state, TalkieWalkieEvent.Connected)
        assertEquals(TalkieWalkieState.LISTENING, state)

        state = machine.transition(state, TalkieWalkieEvent.PressToTalk)
        assertEquals(TalkieWalkieState.REQUESTING_FLOOR, state)

        state = machine.transition(state, TalkieWalkieEvent.FloorGranted)
        assertEquals(TalkieWalkieState.TRANSMITTING, state)

        state = machine.transition(state, TalkieWalkieEvent.ReleaseToTalk)
        assertEquals(TalkieWalkieState.LISTENING, state)
    }

    @Test(expected = IllegalStateException::class)
    fun listeningCannotEnterTransmittingWithoutFloorGrant() {
        machine.transition(TalkieWalkieState.LISTENING, TalkieWalkieEvent.FloorGranted)
    }

    @Test
    fun transportLossFromTransmitNeverReturnsToTransmit() {
        val reconnecting = machine.transition(
            TalkieWalkieState.TRANSMITTING,
            TalkieWalkieEvent.TransportLost
        )
        assertEquals(TalkieWalkieState.RECONNECTING, reconnecting)

        val listening = machine.transition(
            reconnecting,
            TalkieWalkieEvent.Reconnected
        )
        assertEquals(TalkieWalkieState.LISTENING, listening)
    }

    @Test
    fun explicitFailureFromTransmitIsFailClosed() {
        val failed = machine.transition(
            TalkieWalkieState.TRANSMITTING,
            TalkieWalkieEvent.FatalFailure
        )
        assertEquals(TalkieWalkieState.FAILED, failed)
    }
}
