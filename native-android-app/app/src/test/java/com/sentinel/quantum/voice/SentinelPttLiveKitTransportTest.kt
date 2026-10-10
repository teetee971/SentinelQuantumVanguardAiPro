package com.sentinel.quantum.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelPttLiveKitTransportTest {
    @Test fun connectsMutedAndDelegatesMicrophoneChanges() = runBlocking {
        var connectCalls = 0
        var disconnectCalls = 0
        val microphoneChanges = mutableListOf<Boolean>()

        val transport = SentinelPttLiveKitTransport(
            connectMuted = {
                connectCalls++
                Result.success(Unit)
            },
            setMicrophoneEnabled = { enabled ->
                microphoneChanges += enabled
                Result.success(Unit)
            },
            disconnectSession = {
                disconnectCalls++
            }
        )

        assertTrue(transport.connect().isSuccess)
        assertEquals(1, connectCalls)
        assertTrue(transport.setMicrophoneEnabled(true).isSuccess)
        assertTrue(transport.setMicrophoneEnabled(false).isSuccess)
        assertEquals(listOf(true, false), microphoneChanges)

        transport.disconnect()
        assertEquals(1, disconnectCalls)
    }

    @Test fun propagatesMutedConnectionFailureWithoutPretendingSuccess() = runBlocking {
        var microphoneTouched = false
        val expected = IllegalStateException("signaling unavailable")
        val transport = SentinelPttLiveKitTransport(
            connectMuted = { Result.failure(expected) },
            setMicrophoneEnabled = {
                microphoneTouched = true
                Result.success(Unit)
            },
            disconnectSession = {}
        )

        val result = transport.connect()

        assertTrue(result.isFailure)
        assertEquals(expected, result.exceptionOrNull())
        assertFalse(microphoneTouched)
    }
}
