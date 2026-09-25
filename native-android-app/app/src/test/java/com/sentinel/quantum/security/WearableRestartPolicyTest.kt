package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WearableRestartPolicyTest {
    private val binding = PersistedWearableBinding(
        stableId = "watch-1",
        source = WearableIdentitySource.APPLICATION_PUBLIC_KEY,
        keyFingerprintSha256 = "a".repeat(64)
    )

    @Test fun restartNeverRestoresActiveOrAuthenticatedChannel() {
        val recovered = WearableRestartPolicy.recover(binding)
        assertEquals(WearableChannelState.NOT_NEGOTIATED, recovered.channelProof.state)
        assertNull(recovered.channelProof.lastProofAtMs)
        assertFalse(recovered.channelProof.isFresh(100))
    }

    @Test fun restartMayRetainExpectedIdentityBindingOnly() {
        val recovered = WearableRestartPolicy.recover(binding)
        assertEquals("watch-1", recovered.expectedIdentity.stableId)
        assertEquals("a".repeat(64), recovered.expectedIdentity.keyFingerprintSha256)
        assertEquals(0L, recovered.expectedIdentity.verifiedAtMs)
    }

    @Test fun recoveredStateCannotExposeSentinelCapabilitiesWithoutFreshHandshake() {
        val recovered = WearableRestartPolicy.recover(binding)
        val truth = WearableTruthPolicy.evaluate(
            WearableObservation(
                stableId = binding.stableId,
                displayName = null,
                transports = emptySet(),
                platformLinkState = WearablePlatformLinkState.NONE,
                channelProof = recovered.channelProof,
                capabilities = setOf(
                    WearableCapability.SENTINEL_ALERTS,
                    WearableCapability.QUICK_ACTIONS,
                    WearableCapability.BATTERY_STATUS
                )
            ),
            nowMs = 100
        )
        assertFalse(truth.sentinelReachable)
        assertFalse(WearableCapability.SENTINEL_ALERTS in truth.capabilities)
        assertFalse(WearableCapability.QUICK_ACTIONS in truth.capabilities)
    }
}
