package com.sentinel.quantum.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PttSessionControllerTest {
    private class FakeTransport : PttSessionController.Transport {
        var connected = false
        var microphoneEnabled = false
        var microphoneChanges = 0

        override suspend fun connect(): Result<Unit> {
            connected = true
            microphoneEnabled = false
            return Result.success(Unit)
        }

        override suspend fun setMicrophoneEnabled(enabled: Boolean): Result<Unit> {
            check(connected) { "transport must be connected" }
            microphoneEnabled = enabled
            microphoneChanges++
            return Result.success(Unit)
        }

        override suspend fun disconnect() {
            microphoneEnabled = false
            connected = false
        }
    }

    @Test fun connectsReadyWithMicrophoneOffThenTransmitsOnlyWhilePressed() = runBlocking {
        val transport = FakeTransport()
        val controller = PttSessionController(transport)

        assertTrue(controller.connect().isSuccess)
        assertEquals(PttSessionController.State.READY, controller.state())
        assertTrue(transport.connected)
        assertFalse(transport.microphoneEnabled)
        assertEquals(0, transport.microphoneChanges)

        assertTrue(controller.press().isSuccess)
        assertEquals(PttSessionController.State.TRANSMITTING, controller.state())
        assertTrue(transport.microphoneEnabled)

        controller.release()
        assertEquals(PttSessionController.State.READY, controller.state())
        assertFalse(transport.microphoneEnabled)
        assertEquals(2, transport.microphoneChanges)
    }

    @Test fun cancelStopsTransmissionButKeepsSessionReady() = runBlocking {
        val transport = FakeTransport()
        val controller = PttSessionController(transport)

        assertTrue(controller.connect().isSuccess)
        assertTrue(controller.press().isSuccess)
        assertTrue(transport.microphoneEnabled)

        controller.cancel()

        assertEquals(PttSessionController.State.READY, controller.state())
        assertTrue(transport.connected)
        assertFalse(transport.microphoneEnabled)
    }
}
