package com.sentinel.quantum.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelLiveKitCallTransportCredentialsTest {
    private val token = "t".repeat(64)

    @Test fun acceptsOnlySecureWebSocketEndpointWithOpaqueToken() {
        assertTrue(
            SentinelLiveKitCallTransport.Credentials(
                serverUrl = "wss://voice.example.net",
                accessToken = token
            ).validate()
        )
    }

    @Test fun rejectsPlaintextUserInfoFragmentsAndMalformedTokens() {
        assertFalse(
            SentinelLiveKitCallTransport.Credentials(
                serverUrl = "ws://voice.example.net",
                accessToken = token
            ).validate()
        )
        assertFalse(
            SentinelLiveKitCallTransport.Credentials(
                serverUrl = "wss://user:pass@voice.example.net",
                accessToken = token
            ).validate()
        )
        assertFalse(
            SentinelLiveKitCallTransport.Credentials(
                serverUrl = "wss://voice.example.net#debug",
                accessToken = token
            ).validate()
        )
        assertFalse(
            SentinelLiveKitCallTransport.Credentials(
                serverUrl = "wss://voice.example.net",
                accessToken = "short"
            ).validate()
        )
        assertFalse(
            SentinelLiveKitCallTransport.Credentials(
                serverUrl = "wss://voice.example.net",
                accessToken = "x".repeat(40) + " invalid"
            ).validate()
        )
    }
}
