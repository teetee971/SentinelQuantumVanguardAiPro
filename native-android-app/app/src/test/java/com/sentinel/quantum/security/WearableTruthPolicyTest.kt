package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearableTruthPolicyTest {
    private val now = 100_000L

    private fun observation(
        platform: WearablePlatformLinkState = WearablePlatformLinkState.DISCOVERED,
        channel: WearableChannelState = WearableChannelState.NOT_NEGOTIATED,
        proofAt: Long? = null,
        ttlMs: Long = 10_000L,
        transports: Set<WearableTransport> = setOf(WearableTransport.BLUETOOTH_LE)
    ) = WearableObservation(
        stableId = "wearable-test-id",
        displayName = "Test watch",
        transports = transports,
        platformLinkState = platform,
        channelProof = WearableChannelProof(channel, proofAt, ttlMs),
        capabilities = setOf(
            WearableCapability.CONNECTION_STATE,
            WearableCapability.SENTINEL_ALERTS,
            WearableCapability.QUICK_ACTIONS
        )
    )

    @Test fun platformAndSentinelChannelRemainOrthogonal() {
        val truth = WearableTruthPolicy.evaluate(
            observation(platform = WearablePlatformLinkState.COMPANION_ASSOCIATED),
            now
        )
        assertEquals(WearablePlatformLinkState.COMPANION_ASSOCIATED, truth.platformLinkState)
        assertFalse(truth.sentinelReachable)
        assertFalse(WearableCapability.SENTINEL_ALERTS in truth.capabilities)
    }

    @Test fun vendorBridgeCanBeActiveWithoutBluetoothBond() {
        val truth = WearableTruthPolicy.evaluate(
            observation(
                platform = WearablePlatformLinkState.NONE,
                channel = WearableChannelState.ACTIVE,
                proofAt = now - 1,
                transports = setOf(WearableTransport.VENDOR_BRIDGE)
            ),
            now
        )
        assertTrue(truth.sentinelReachable)
        assertTrue(WearableCapability.SENTINEL_ALERTS in truth.capabilities)
    }

    @Test fun activeChannelWithoutFreshProofFailsClosedAsStale() {
        val truth = WearableTruthPolicy.evaluate(
            observation(channel = WearableChannelState.ACTIVE, proofAt = now - 10_001, ttlMs = 10_000),
            now
        )
        assertEquals(WearableChannelState.STALE, truth.channelState)
        assertFalse(truth.sentinelReachable)
        assertFalse(WearableCapability.QUICK_ACTIONS in truth.capabilities)
    }

    @Test fun futureDatedProofFailsClosed() {
        val truth = WearableTruthPolicy.evaluate(
            observation(channel = WearableChannelState.ACTIVE, proofAt = now + 1),
            now
        )
        assertFalse(truth.sentinelReachable)
        assertEquals(WearableChannelState.STALE, truth.channelState)
    }

    @Test fun authenticatedButNotActiveDoesNotEnableSentinelCapabilities() {
        val truth = WearableTruthPolicy.evaluate(
            observation(
                platform = WearablePlatformLinkState.COMPANION_ASSOCIATED,
                channel = WearableChannelState.AUTHENTICATED,
                proofAt = now - 1
            ),
            now
        )
        assertFalse(truth.sentinelReachable)
        assertFalse(WearableCapability.SENTINEL_ALERTS in truth.capabilities)
    }

    @Test fun freshActiveChannelEnablesNegotiatedSentinelCapabilities() {
        val truth = WearableTruthPolicy.evaluate(
            observation(channel = WearableChannelState.ACTIVE, proofAt = now - 1),
            now
        )
        assertTrue(truth.sentinelReachable)
        assertTrue(WearableCapability.SENTINEL_ALERTS in truth.capabilities)
        assertTrue(WearableCapability.QUICK_ACTIONS in truth.capabilities)
    }
}
