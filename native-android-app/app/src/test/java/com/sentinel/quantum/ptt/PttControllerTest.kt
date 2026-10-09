package com.sentinel.quantum.ptt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PttControllerTest {
    @Test fun cannotTransmitBeforeTransportIsConnected() {
        val transport = FakePttTransport()
        val controller = PttController(transport)

        assertFalse(controller.pressToTalk())
        assertEquals(PttState.DISCONNECTED, controller.state)
        assertFalse(transport.transmitting)
    }

    @Test fun connectedTransportEnablesHalfDuplexTransmissionUntilRelease() {
        val transport = FakePttTransport()
        val controller = PttController(transport)

        controller.connect()
        transport.emit(PttTransport.Event.Connected)
        assertEquals(PttState.READY, controller.state)

        assertTrue(controller.pressToTalk())
        assertEquals(PttState.TRANSMITTING, controller.state)
        assertTrue(transport.transmitting)

        controller.releaseToTalk()
        assertEquals(PttState.READY, controller.state)
        assertFalse(transport.transmitting)
    }

    @Test fun incomingAudioBlocksLocalTransmissionForHalfDuplexSafety() {
        val transport = FakePttTransport()
        val controller = PttController(transport)
        controller.connect()
        transport.emit(PttTransport.Event.Connected)
        transport.emit(PttTransport.Event.RemoteAudioStarted)

        assertEquals(PttState.RECEIVING, controller.state)
        assertFalse(controller.pressToTalk())
        assertFalse(transport.transmitting)

        transport.emit(PttTransport.Event.RemoteAudioStopped)
        assertEquals(PttState.READY, controller.state)
    }

    @Test fun transportLossStopsTransmissionImmediatelyAndCannotFakeReady() {
        val transport = FakePttTransport()
        val controller = PttController(transport)
        controller.connect()
        transport.emit(PttTransport.Event.Connected)
        assertTrue(controller.pressToTalk())

        transport.emit(PttTransport.Event.Disconnected("network_lost"))

        assertEquals(PttState.DISCONNECTED, controller.state)
        assertFalse(transport.transmitting)
        assertEquals("network_lost", controller.lastFailure)
    }

    private class FakePttTransport : PttTransport {
        private var listener: ((PttTransport.Event) -> Unit)? = null
        var transmitting = false
            private set

        override fun setEventListener(listener: (PttTransport.Event) -> Unit) {
            this.listener = listener
        }

        override fun connect() = Unit

        override fun disconnect() {
            transmitting = false
        }

        override fun startTransmitting(): Boolean {
            transmitting = true
            return true
        }

        override fun stopTransmitting() {
            transmitting = false
        }

        fun emit(event: PttTransport.Event) {
            listener?.invoke(event)
        }
    }
}
