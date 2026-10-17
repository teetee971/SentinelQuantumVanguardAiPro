package com.sentinel.quantum.security

import com.sentinel.quantum.integration.IntegrationCapabilityId
import com.sentinel.quantum.integration.IntegrationDiagnosticReason
import com.sentinel.quantum.integration.IntegrationState
import com.sentinel.quantum.integration.IntegrationTruthRuntime
import com.sentinel.quantum.wearable.security.VerifiedWearableHandshakeFixture
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class WearableSecureSessionRuntimeTest {
    private val key = "a".repeat(64)
    private val phoneHpke = "1".repeat(64)
    private val watchHpke = "2".repeat(64)

    @Before fun clearTruth() = IntegrationTruthRuntime.clearForTests()

    private fun observation() = WearableAdapterObservation(
        stableIdHint = "wearos-test",
        displayName = "Watch",
        transport = WearableTransport.WEAR_OS,
        platformLinkState = WearablePlatformLinkState.COMPANION_ASSOCIATED,
        availability = WearableAdapterAvailability.AVAILABLE,
        observedCapabilities = setOf(WearableCapability.CONNECTION_STATE)
    )

    private fun activeResult(): WearableSecureHandshakeResult {
        val session = WearableSession("session-1", 2, setOf(WearableCapability.SENTINEL_ALERTS), 1_000L)
        val proof = VerifiedWearableHandshakeFixture.create(
            "watch-1", key, session.sessionId, 2, setOf("SENTINEL_ALERTS"), phoneHpke, watchHpke
        )
        return WearableSecureHandshakeResult(
            state = WearableSecureHandshakeState.ACTIVE,
            session = session,
            verifiedProof = proof,
            wearableHpkePublicKeyEncoded = byteArrayOf(1)
        )
    }

    @Test fun platformObservationAloneStaysLocked() {
        val runtime = WearableSecureSessionRuntime(livenessTtlMs = 30_000L)
        runtime.observePlatform(observation(), 1_000L)
        assertLocked(1_000L)
    }

    @Test fun handshakeActivationStaysLockedUntilAuthenticatedTraffic() {
        val runtime = WearableSecureSessionRuntime(livenessTtlMs = 30_000L)
        runtime.activate(activeResult(), observation(), 1_000L)
        assertLocked(1_000L)
        runtime.onAuthenticatedTraffic("session-1", 20_000L)
        assertConnected(20_000L)
    }

    @Test fun platformPongCannotRefreshAuthenticatedTtl() {
        val runtime = WearableSecureSessionRuntime(livenessTtlMs = 30_000L)
        runtime.activate(activeResult(), observation(), 1_000L)
        runtime.onAuthenticatedTraffic("session-1", 2_000L)
        runtime.observePlatform(observation(), 20_000L)
        runtime.expireIfStale(32_000L)
        assertLocked(32_000L)
    }

    @Test fun sessionWithoutFirstLivenessExpiresLocked() {
        val runtime = WearableSecureSessionRuntime(livenessTtlMs = 30_000L)
        runtime.activate(activeResult(), observation(), 1_000L)
        runtime.expireIfStale(31_000L)
        assertLocked(31_000L)
    }

    @Test fun disconnectAndAuthenticationFailureRevokeImmediately() {
        val runtime = WearableSecureSessionRuntime(livenessTtlMs = 30_000L)
        runtime.activate(activeResult(), observation(), 1_000L)
        runtime.onAuthenticatedTraffic("session-1", 1_500L)
        runtime.disconnect(2_000L)
        assertLocked(2_000L)

        runtime.activate(activeResult(), observation(), 3_000L)
        runtime.onAuthenticatedTraffic("session-1", 3_500L)
        runtime.authenticationFailure(4_000L)
        assertLocked(4_000L)
    }

    @Test fun newRuntimeCannotResurrectOldProcessSession() {
        val first = WearableSecureSessionRuntime(livenessTtlMs = 30_000L)
        first.activate(activeResult(), observation(), 1_000L)
        first.onAuthenticatedTraffic("session-1", 1_500L)
        IntegrationTruthRuntime.clearForTests()
        val restarted = WearableSecureSessionRuntime(livenessTtlMs = 30_000L)
        restarted.observePlatform(observation(), 2_000L)
        assertLocked(2_000L)
    }

    private fun assertConnected(now: Long) {
        assertEquals(
            IntegrationState.CONNECTED,
            IntegrationTruthRuntime.snapshot(now).status(IntegrationCapabilityId.WEARABLE_RUNTIME).state
        )
    }

    private fun assertLocked(now: Long) {
        val status = IntegrationTruthRuntime.snapshot(now).status(IntegrationCapabilityId.WEARABLE_RUNTIME)
        assertEquals(IntegrationState.LOCKED_EXTERNAL_DEPENDENCY, status.state)
        assertEquals(IntegrationDiagnosticReason.IDENTITY_BINDING_MISSING, status.diagnosticReason)
    }
}
