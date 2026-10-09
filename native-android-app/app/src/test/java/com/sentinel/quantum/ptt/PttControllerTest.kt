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

    @Test fun lateConnectedEventAfterExplicitDisconnectCannotResurrectReady() {
        val transport = FakePttTransport()
        val controller = PttController(transport)
        controller.connect()
        controller.disconnect()

        transport.emit(PttTransport.Event.Connected)

        assertEquals(PttState.DISCONNECTED, controller.state)
        assertFalse(controller.pressToTalk())
    }

    @Test fun eachConnectionAttemptGetsANewListenerGeneration() {
        val transport = FakePttTransport()
        val controller = PttController(transport)

        controller.connect()
        controller.disconnect()
        controller.connect()

        assertEquals(3, transport.listenerRegistrationCount)
    }

    @Test fun staleConnectionCallbacksCannotControlReconnectedSession() {
        val transport = FakePttTransport()
        val controller = PttController(transport)

        controller.connect()
        controller.disconnect()
        controller.connect()

        transport.emitFromRegistration(1, PttTransport.Event.Connected)
        assertEquals(PttState.CONNECTING, controller.state)
        transport.emit(PttTransport.Event.Connected)
        assertEquals(PttState.READY, controller.state)
        transport.emitFromRegistration(1, PttTransport.Event.RemoteAudioStarted)
        assertEquals(PttState.READY, controller.state)
    }

    @Test fun remoteAudioEventOutsideLiveSessionCannotManufactureReceivingState() {
        val transport = FakePttTransport()
        val controller = PttController(transport)

        transport.emit(PttTransport.Event.RemoteAudioStarted)
        assertEquals(PttState.DISCONNECTED, controller.state)

        controller.connect()
        transport.emit(PttTransport.Event.Failure("auth_failed"))
        transport.emit(PttTransport.Event.RemoteAudioStarted)
        assertEquals(PttState.ERROR, controller.state)
        assertEquals("auth_failed", controller.lastFailure)
    }

    @Test fun synchronousConnectExceptionFailsClosedInsteadOfStickingConnecting() {
        val transport = FakePttTransport().apply { throwOnConnect = true }
        val controller = PttController(transport)

        controller.connect()

        assertEquals(PttState.ERROR, controller.state)
        assertEquals("connect_failed", controller.lastFailure)
        assertFalse(controller.pressToTalk())
    }

    @Test fun synchronousTransmitStartExceptionKeepsMicrophoneGateClosed() {
        val transport = FakePttTransport().apply { throwOnStart = true }
        val controller = PttController(transport)
        controller.connect()
        transport.emit(PttTransport.Event.Connected)

        assertFalse(controller.pressToTalk())

        assertEquals(PttState.READY, controller.state)
        assertEquals("transmit_start_failed", controller.lastFailure)
        assertFalse(transport.transmitting)
    }

    @Test fun synchronousTransmitStopExceptionTearsDownSessionAndFailsClosed() {
        val transport = FakePttTransport()
        val controller = PttController(transport)
        controller.connect()
        transport.emit(PttTransport.Event.Connected)
        assertTrue(controller.pressToTalk())
        transport.throwOnStop = true

        controller.releaseToTalk()

        assertEquals(PttState.ERROR, controller.state)
        assertEquals("transmit_stop_failed", controller.lastFailure)
        assertTrue(transport.disconnectCount > 0)
        assertFalse(controller.pressToTalk())
    }

    @Test fun telecomCallInterruptsTransmissionAndNeverAutoResumes() {
        val transport = FakePttTransport()
        val controller = PttController(transport)
        controller.connect()
        transport.emit(PttTransport.Event.Connected)
        assertTrue(controller.pressToTalk())

        controller.onTelecomCallPresenceChanged(true)

        assertEquals(PttState.DISCONNECTED, controller.state)
        assertFalse(transport.transmitting)
        assertEquals("telecom_call_active", controller.lastFailure)
        assertFalse(controller.pressToTalk())

        controller.onTelecomCallPresenceChanged(false)

        assertEquals(PttState.DISCONNECTED, controller.state)
        assertFalse(controller.pressToTalk())
    }

    private class FakePttTransport : PttTransport {
        private var listener: ((PttTransport.Event) -> Unit)? = null
        private val registrations = mutableListOf<(PttTransport.Event) -> Unit>()
        var transmitting = false
            private set
        var listenerRegistrationCount = 0
            private set
        var disconnectCount = 0
            private set
        var throwOnConnect = false
        var throwOnStart = false
        var throwOnStop = false

        override fun setEventListener(listener: (PttTransport.Event) -> Unit) {
            this.listener = listener
            registrations += listener
            listenerRegistrationCount += 1
        }

        override fun connect() {
            if (throwOnConnect) error("connect boom")
        }

        override fun disconnect() {
            disconnectCount += 1
            transmitting = false
        }

        override fun startTransmitting(): Boolean {
            if (throwOnStart) error("start boom")
            transmitting = true
            return true
        }

        override fun stopTransmitting() {
            if (throwOnStop) error("stop boom")
            transmitting = false
        }

        fun emit(event: PttTransport.Event) {
            listener?.invoke(event)
        }

        fun emitFromRegistration(index: Int, event: PttTransport.Event) {
            registrations[index](event)
        }
    }
}
