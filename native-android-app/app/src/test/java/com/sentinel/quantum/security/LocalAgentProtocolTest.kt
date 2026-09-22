package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAgentProtocolTest {

    @Test
    fun productionEndpointRequiresHttps() {
        assertTrue(
            LocalAgentProtocol.isEndpointAllowed(
                LocalAgentEndpointPolicy("https://agent.example.test/api/v1")
            )
        )
        assertFalse(
            LocalAgentProtocol.isEndpointAllowed(
                LocalAgentEndpointPolicy("http://192.168.1.20:8080")
            )
        )
    }

    @Test
    fun developmentHttpIsLimitedToLoopback() {
        assertTrue(
            LocalAgentProtocol.isEndpointAllowed(
                LocalAgentEndpointPolicy(
                    endpoint = "http://127.0.0.1:8080",
                    allowLoopbackHttpForDevelopment = true
                )
            )
        )
        assertFalse(
            LocalAgentProtocol.isEndpointAllowed(
                LocalAgentEndpointPolicy(
                    endpoint = "http://192.168.1.20:8080",
                    allowLoopbackHttpForDevelopment = true
                )
            )
        )
    }

    @Test
    fun credentialsAndFragmentsAreRejected() {
        assertFalse(
            LocalAgentProtocol.isEndpointAllowed(
                LocalAgentEndpointPolicy("https://user:pass@agent.example.test/api")
            )
        )
        assertFalse(
            LocalAgentProtocol.isEndpointAllowed(
                LocalAgentEndpointPolicy("https://agent.example.test/api#token")
            )
        )
    }

    @Test
    fun snapshotMetadataIsBoundedAndVersioned() {
        assertTrue(
            LocalAgentProtocol.isSnapshotMetadataValid(
                LocalAgentSnapshotMetadata(
                    schemaVersion = 1,
                    generatedAtMs = 10_000L,
                    deviceCount = 50,
                    eventCount = 100,
                    payloadBytes = 64_000L
                ),
                nowMs = 20_000L
            )
        )

        assertFalse(
            LocalAgentProtocol.isSnapshotMetadataValid(
                LocalAgentSnapshotMetadata(
                    schemaVersion = 2,
                    generatedAtMs = 10_000L,
                    deviceCount = 50,
                    eventCount = 100,
                    payloadBytes = 64_000L
                ),
                nowMs = 20_000L
            )
        )

        assertFalse(
            LocalAgentProtocol.isSnapshotMetadataValid(
                LocalAgentSnapshotMetadata(
                    schemaVersion = 1,
                    generatedAtMs = 10_000L,
                    deviceCount = LocalAgentProtocol.MAX_DEVICE_COUNT + 1,
                    eventCount = 100,
                    payloadBytes = 64_000L
                ),
                nowMs = 20_000L
            )
        )
    }
}
