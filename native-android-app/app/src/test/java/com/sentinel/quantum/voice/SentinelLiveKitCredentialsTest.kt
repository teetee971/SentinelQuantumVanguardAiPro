package com.sentinel.quantum.voice

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

    @Test fun rejectsMissingHostWhitespaceAndUnboundedTokens() {
        assertFalse(
            SentinelLiveKitCallTransport.Credentials(
                serverUrl = "wss:///missing-host",
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
    }
}
