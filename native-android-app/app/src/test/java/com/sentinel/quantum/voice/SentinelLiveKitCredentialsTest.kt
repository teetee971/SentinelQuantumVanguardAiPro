package com.sentinel.quantum.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelLiveKitCredentialsTest {
    private val token = "x".repeat(64)

    @Test fun acceptsOnlyTlsWebSocketEndpointWithBoundedToken() {
        assertTrue(
            SentinelLiveKitCallTransport.Credentials(
                serverUrl = "wss://voice.example.test",
                accessToken = token
            ).validate()
        )
        assertFalse(
            SentinelLiveKitCallTransport.Credentials(
                serverUrl = "ws://voice.example.test",
                accessToken = token
            ).validate()
        )
        assertFalse(
            SentinelLiveKitCallTransport.Credentials(
                serverUrl = "https://voice.example.test",
                accessToken = token
            ).validate()
        )
    }

    @Test fun rejectsMissingHostCredentialsFragmentsWhitespaceAndUnboundedTokens() {
        assertFalse(
            SentinelLiveKitCallTransport.Credentials(
                serverUrl = "wss:///missing-host",
                accessToken = token
            ).validate()
        )
        assertFalse(
            SentinelLiveKitCallTransport.Credentials(
                serverUrl = "wss://user:pass@voice.example.test",
                accessToken = token
            ).validate()
        )
        assertFalse(
            SentinelLiveKitCallTransport.Credentials(
                serverUrl = "wss://@voice.example.test",
                accessToken = token
            ).validate()
        )
        assertFalse(
            SentinelLiveKitCallTransport.Credentials(
                serverUrl = "wss://voice.example.test?access_token=secret",
                accessToken = token
            ).validate()
        )
        assertFalse(
            SentinelLiveKitCallTransport.Credentials(
                serverUrl = "wss://voice.example.test#debug",
                accessToken = token
            ).validate()
        )
        assertFalse(
            SentinelLiveKitCallTransport.Credentials(
                serverUrl = "wss://voice.example.test#",
                accessToken = token
            ).validate()
        )
        assertFalse(
            SentinelLiveKitCallTransport.Credentials(
                serverUrl = "wss://voice.example.test",
                accessToken = "contains space"
            ).validate()
        )
        assertFalse(
            SentinelLiveKitCallTransport.Credentials(
                serverUrl = "wss://voice.example.test",
                accessToken = "short"
            ).validate()
        )
        assertFalse(
            SentinelLiveKitCallTransport.Credentials(
                serverUrl = "wss://voice.example.test",
                accessToken = "x".repeat(16_385)
            ).validate()
        )
    }

    @Test fun deniedMicrophonePermissionFailsBeforeRoomCreation() = runBlocking {
        var roomFactoryInvocations = 0
        val transport = SentinelLiveKitCallTransport(
            voiceProcessor = LiveKitVoiceAudioProcessor(),
            permissionGranted = { false },
            roomFactory = {
                roomFactoryInvocations++
                error("Room factory must not run before microphone permission is granted")
            }
        )

        val result = transport.connect(
            SentinelLiveKitCallTransport.Credentials(
                serverUrl = "wss://voice.example.test",
                accessToken = token
            )
        )

        assertTrue(result.exceptionOrNull() is SecurityException)
        assertEquals(0, roomFactoryInvocations)
        assertEquals(SentinelLiveKitCallTransport.State.FAILED, transport.state())
    }
}
