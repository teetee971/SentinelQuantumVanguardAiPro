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

    @Test fun transmitRequestDoesNotClaimMicrophoneActiveBeforeConfirmation() {
        val transport = FakePttTransport()
        val controller = readyController(transport)

        assertTrue(controller.pressToTalk())

        assertEquals(PttState.TRANSMIT_REQUESTED, controller.state)
        assertFalse(transport.transmitting)
        assertEquals(1, transport.startRequestCount)

        transport.emit(PttTransport.Event.LocalTransmissionStarted)
        assertEquals(PttState.TRANSMITTING, controller.state)
        assertTrue(transport.transmitting)

        controller.releaseToTalk()
        assertEquals(PttState.TRANSMIT_STOPPING, controller.state)
        assertTrue(transport.transmitting)

        transport.emit(PttTransport.Event.LocalTransmissionStopped)
        assertEquals(PttState.READY, controller.state)
        assertFalse(transport.transmitting)
    }

    @Test fun synchronousMediaAcknowledgementsCannotRaceControllerState() {
        val transport = FakePttTransport().apply { synchronousStartAck = true }
        val controller = readyController(transport)

        assertTrue(controller.pressToTalk())
        assertEquals(PttState.TRANSMITTING, controller.state)
        assertTrue(transport.transmitting)

        transport.synchronousStopAck = true
        controller.releaseToTalk()

        assertEquals(PttState.READY, controller.state)
        assertFalse(transport.transmitting)
    }

    @Test fun releaseBeforeStartConfirmationReissuesStopAfterLateStart() {
        val transport = FakePttTransport()
        val controller = readyController(transport)

        assertTrue(controller.pressToTalk())
        controller.releaseToTalk()
        assertEquals(PttState.TRANSMIT_STOPPING, controller.state)
        assertEquals(1, transport.stopRequestCount)

        transport.emit(PttTransport.Event.LocalTransmissionStarted)

        assertEquals(PttState.TRANSMIT_STOPPING, controller.state)
        assertEquals(2, transport.stopRequestCount)

        transport.emit(PttTransport.Event.LocalTransmissionStopped)
        assertEquals(PttState.READY, controller.state)
        assertFalse(transport.transmitting)
    }

    @Test fun incomingAudioBlocksLocalTransmissionForHalfDuplexSafety() {
        val transport = FakePttTransport()
        val controller = readyController(transport)
        transport.emit(PttTransport.Event.RemoteAudioStarted)

        assertEquals(PttState.RECEIVING, controller.state)
        assertFalse(controller.pressToTalk())
        assertFalse(transport.transmitting)

        transport.emit(PttTransport.Event.RemoteAudioStopped)
        assertEquals(PttState.READY, controller.state)
    }

    @Test fun remoteAudioWinsPendingLocalTransmissionWithoutOverlap() {
        val transport = FakePttTransport()
        val controller = readyController(transport)
        assertTrue(controller.pressToTalk())

        transport.emit(PttTransport.Event.RemoteAudioStarted)

        assertEquals(PttState.TRANSMIT_STOPPING, controller.state)
        assertEquals(1, transport.stopRequestCount)
        assertFalse(transport.transmitting)

        transport.emit(PttTransport.Event.LocalTransmissionStopped)
        assertEquals(PttState.RECEIVING, controller.state)
    }

    @Test fun transportLossStopsTransmissionImmediatelyAndCannotFakeReady() {
        val transport = FakePttTransport()
        val controller = readyController(transport)
        assertTrue(controller.pressToTalk())
        transport.emit(PttTransport.Event.LocalTransmissionStarted)

        transport.emit(PttTransport.Event.Disconnected("network_lost"))

        assertEquals(PttState.DISCONNECTED, controller.state)
        assertFalse(transport.transmitting)
        assertTrue(transport.stopRequestCount > 0)
        assertEquals("network_lost", controller.lastFailure)
    }

    @Test fun disconnectDoesNotClaimTeardownBeforeTransportConfirmation() {
        val transport = FakePttTransport()
        val controller = readyController(transport)

        controller.disconnect()

        assertEquals(1, transport.disconnectCount)
        assertEquals(PttState.DISCONNECTING, controller.state)
        assertFalse(controller.pressToTalk())

        transport.emit(PttTransport.Event.Disconnected())

        assertEquals(PttState.DISCONNECTED, controller.state)
    }

    @Test fun lateConnectedEventAfterExplicitDisconnectCannotResurrectReady() {
        val transport = FakePttTransport()
        val controller = PttController(transport)
        controller.connect()
        controller.disconnect()

        assertEquals(PttState.DISCONNECTING, controller.state)
        transport.emit(PttTransport.Event.Connected)

        assertEquals(PttState.DISCONNECTING, controller.state)
        assertFalse(controller.pressToTalk())

        transport.emit(PttTransport.Event.Disconnected())
        assertEquals(PttState.DISCONNECTED, controller.state)
    }

    @Test fun eachConnectionAttemptGetsANewListenerGeneration() {
        val transport = FakePttTransport()
        val controller = PttController(transport)

        controller.connect()
        controller.disconnect()
        transport.emit(PttTransport.Event.Disconnected())
        controller.connect()

        assertEquals(3, transport.listenerRegistrationCount)
    }

    @Test fun staleConnectionCallbacksCannotControlReconnectedSession() {
        val transport = FakePttTransport()
        val controller = PttController(transport)

        controller.connect()
        controller.disconnect()
        transport.emit(PttTransport.Event.Disconnected())
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

    @Test fun transportFailureTearsDownLiveSessionBeforeLeavingError() {
        val transport = FakePttTransport()
        val controller = readyController(transport)

        transport.emit(PttTransport.Event.Failure("socket_lost"))

        assertEquals(PttState.ERROR, controller.state)
        assertEquals("socket_lost", controller.lastFailure)
        assertEquals(1, transport.disconnectCount)
        assertFalse(controller.pressToTalk())
    }

    @Test fun transportFailureBlocksReconnectUntilTeardownConfirmation() {
        val transport = FakePttTransport()
        val controller = readyController(transport)

        transport.emit(PttTransport.Event.Failure("socket_lost"))
        controller.connect()

        assertEquals(PttState.ERROR, controller.state)
        assertEquals(1, transport.connectCount)
        assertEquals("socket_lost", controller.lastFailure)

        transport.emit(PttTransport.Event.Disconnected())
        assertEquals(PttState.DISCONNECTED, controller.state)

        controller.connect()
        assertEquals(PttState.CONNECTING, controller.state)
        assertEquals(2, transport.connectCount)
    }

    @Test fun synchronousConnectExceptionFailsClosedInsteadOfStickingConnecting() {
        val transport = FakePttTransport().apply { throwOnConnect = true }
        val controller = PttController(transport)

        controller.connect()

        assertEquals(PttState.ERROR, controller.state)
        assertEquals("connect_failed", controller.lastFailure)
        assertFalse(controller.pressToTalk())
    }

    @Test fun synchronousTransmitStartRequestExceptionKeepsMicrophoneGateClosed() {
        val transport = FakePttTransport().apply { throwOnStartRequest = true }
        val controller = readyController(transport)

        assertFalse(controller.pressToTalk())

        assertEquals(PttState.READY, controller.state)
        assertEquals("transmit_start_request_failed", controller.lastFailure)
        assertFalse(transport.transmitting)
    }

    @Test fun synchronousTransmitStopRequestExceptionTearsDownSessionAndFailsClosed() {
        val transport = FakePttTransport()
        val controller = readyController(transport)
        assertTrue(controller.pressToTalk())
        transport.emit(PttTransport.Event.LocalTransmissionStarted)
        transport.throwOnStopRequest = true

        controller.releaseToTalk()

        assertEquals(PttState.ERROR, controller.state)
        assertEquals("transmit_stop_request_failed", controller.lastFailure)
        assertTrue(transport.disconnectCount > 0)
        assertFalse(controller.pressToTalk())
    }

    @Test fun telecomCallInterruptsTransmissionAndNeverAutoResumes() {
        val transport = FakePttTransport()
        val controller = readyController(transport)
        assertTrue(controller.pressToTalk())
        transport.emit(PttTransport.Event.LocalTransmissionStarted)

        controller.onTelecomCallPresenceChanged(true)

        assertEquals(PttState.DISCONNECTING, controller.state)
        assertEquals("telecom_call_active", controller.lastFailure)
        assertFalse(controller.pressToTalk())

        transport.emit(PttTransport.Event.Disconnected())
        assertEquals(PttState.DISCONNECTED, controller.state)
        assertFalse(transport.transmitting)
        assertEquals("telecom_call_active", controller.lastFailure)

        controller.onTelecomCallPresenceChanged(false)

        assertEquals(PttState.DISCONNECTED, controller.state)
        assertFalse(controller.pressToTalk())
    }

    private fun readyController(transport: FakePttTransport): PttController =
        PttController(transport).also { controller ->
            controller.connect()
            transport.emit(PttTransport.Event.Connected)
            assertEquals(PttState.READY, controller.state)
        }

    private class FakePttTransport : PttTransport {
        private var listener: ((PttTransport.Event) -> Unit)? = null
        private val registrations = mutableListOf<(PttTransport.Event) -> Unit>()
        var transmitting = false
            private set
        var listenerRegistrationCount = 0
            private set
        var connectCount = 0
            private set
        var disconnectCount = 0
            private set
        var startRequestCount = 0
            private set
        var stopRequestCount = 0
            private set
        var throwOnConnect = false
        var throwOnStartRequest = false
        var throwOnStopRequest = false
        var synchronousStartAck = false
        var synchronousStopAck = false
        var synchronousDisconnectAck = false

        override fun setEventListener(listener: (PttTransport.Event) -> Unit) {
            this.listener = listener
            registrations += listener
            listenerRegistrationCount += 1
        }

        override fun connect() {
            connectCount += 1
            if (throwOnConnect) error("connect boom")
        }

        override fun disconnect() {
            disconnectCount += 1
            if (synchronousDisconnectAck) emit(PttTransport.Event.Disconnected())
        }

        override fun requestStartTransmitting() {
            startRequestCount += 1
            if (throwOnStartRequest) error("start request boom")
            if (synchronousStartAck) emit(PttTransport.Event.LocalTransmissionStarted)
        }

        override fun requestStopTransmitting() {
            stopRequestCount += 1
            if (throwOnStopRequest) error("stop request boom")
            if (synchronousStopAck) emit(PttTransport.Event.LocalTransmissionStopped)
        }

        fun emit(event: PttTransport.Event) {
            when (event) {
                PttTransport.Event.LocalTransmissionStarted -> transmitting = true
                PttTransport.Event.LocalTransmissionStopped,
                is PttTransport.Event.Disconnected -> transmitting = false
                else -> Unit
            }
            listener?.invoke(event)
        }

        fun emitFromRegistration(index: Int, event: PttTransport.Event) {
            registrations[index](event)
        }
    }
}
