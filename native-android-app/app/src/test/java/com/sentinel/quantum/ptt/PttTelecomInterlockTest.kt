package com.sentinel.quantum.ptt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PttTelecomInterlockTest {
    @Test fun telecomStateBeforeAttachImmediatelyBlocksLatePttRuntime() {
        val interlock = PttTelecomInterlock()
        interlock.onTelecomCallPresenceChanged(true)

        val transport = FakePttTransport()
        val controller = PttController(transport)
        assertTrue(interlock.attach(controller))

        controller.connect()

        assertEquals(0, transport.connectCount)
        assertEquals(PttState.DISCONNECTED, controller.state)
        assertEquals("telecom_call_active", controller.lastFailure)

        interlock.onTelecomCallPresenceChanged(false)
        assertEquals(0, transport.connectCount)
        assertEquals(PttState.DISCONNECTED, controller.state)

        controller.connect()
        assertEquals(1, transport.connectCount)
        assertEquals(PttState.CONNECTING, controller.state)
    }

    @Test fun secondControllerCannotStealProcessPttOwnership() {
        val interlock = PttTelecomInterlock()
        val first = PttController(FakePttTransport())
        val secondTransport = FakePttTransport()
        val second = PttController(secondTransport)

        assertTrue(interlock.attach(first))
        assertFalse(interlock.attach(second))

        interlock.onTelecomCallPresenceChanged(true)
        second.connect()
        assertEquals(1, secondTransport.connectCount)

        assertTrue(interlock.detach(first))
        assertTrue(interlock.attach(second))
        assertEquals(PttState.DISCONNECTED, second.state)
        assertEquals("telecom_call_active", second.lastFailure)
    }

    @Test fun detachDisconnectsLiveControllerBeforeReleasingOwnership() {
        val interlock = PttTelecomInterlock()
        val firstTransport = FakePttTransport()
        val first = PttController(firstTransport)
        val second = PttController(FakePttTransport())

        assertTrue(interlock.attach(first))
        first.connect()
        firstTransport.emit(PttTransport.Event.Connected)
        assertEquals(PttState.READY, first.state)

        assertTrue(interlock.detach(first))

        assertEquals(1, firstTransport.disconnectCount)
        assertEquals(PttState.DISCONNECTED, first.state)
        assertTrue(interlock.attach(second))
    }

    @Test fun failedDisconnectKeepsOwnershipFailClosed() {
        val interlock = PttTelecomInterlock()
        val firstTransport = FakePttTransport(disconnectThrows = true)
        val first = PttController(firstTransport)
        val second = PttController(FakePttTransport())

        assertTrue(interlock.attach(first))
        first.connect()
        firstTransport.emit(PttTransport.Event.Connected)
        assertEquals(PttState.READY, first.state)

        assertFalse(interlock.detach(first))

        assertEquals(PttState.ERROR, first.state)
        assertEquals("disconnect_failed", first.lastFailure)
        assertFalse(interlock.attach(second))
    }

    private class FakePttTransport(
        private val disconnectThrows: Boolean = false
    ) : PttTransport {
        private var listener: ((PttTransport.Event) -> Unit)? = null
        var connectCount = 0
            private set
        var disconnectCount = 0
            private set

        override fun setEventListener(listener: (PttTransport.Event) -> Unit) {
            this.listener = listener
        }

        override fun connect() {
            connectCount += 1
        }

        override fun disconnect() {
            disconnectCount += 1
            if (disconnectThrows) error("disconnect failed")
        }

        override fun startTransmitting(): Boolean = true

        override fun stopTransmitting() = Unit

        fun emit(event: PttTransport.Event) {
            listener?.invoke(event)
        }
    }
}
