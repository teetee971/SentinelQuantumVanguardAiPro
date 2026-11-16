package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearableAdapterPolicyTest {
    private fun observation(
        availability: WearableAdapterAvailability = WearableAdapterAvailability.AVAILABLE,
        link: WearablePlatformLinkState = WearablePlatformLinkState.COMPANION_ASSOCIATED
    ) = WearableAdapterObservation(
        stableIdHint = "platform-node",
        displayName = "Watch",
        transport = WearableTransport.WEAR_OS,
        platformLinkState = link,
        availability = availability,
        observedCapabilities = setOf(
            WearableCapability.CONNECTION_STATE,
            WearableCapability.SENTINEL_ALERTS,
            WearableCapability.QUICK_ACTIONS,
            WearableCapability.BATTERY_STATUS
        )
    )

    private fun activeProof() = WearableChannelProof(
        state = WearableChannelState.ACTIVE,
        lastProofAtMs = 100,
        ttlMs = 1_000
    )

    @Test fun companionAssociationAloneNeverMeansSentinelReachable() {
        val truth = WearableAdapterPolicy.evaluate(
            observation(), null, emptySet(), 200
        )
        assertFalse(truth.sentinelReachable)
        assertFalse(WearableCapability.SENTINEL_ALERTS in truth.usableCapabilities)
        assertFalse(WearableCapability.QUICK_ACTIONS in truth.usableCapabilities)
        assertTrue(WearableCapability.BATTERY_STATUS in truth.usableCapabilities)
    }

    @Test fun unavailableAdapterCannotBeReachableEvenWithFreshChannelProof() {
        val truth = WearableAdapterPolicy.evaluate(
            observation(WearableAdapterAvailability.UNAVAILABLE),
            activeProof(),
            setOf(WearableCapability.SENTINEL_ALERTS),
            200
        )
        assertFalse(truth.sentinelReachable)
        assertFalse(WearableCapability.SENTINEL_ALERTS in truth.usableCapabilities)
    }

    @Test fun privilegedCapabilitiesRequireFreshChannelAndNegotiation() {
        val truth = WearableAdapterPolicy.evaluate(
            observation(),
            activeProof(),
            setOf(WearableCapability.SENTINEL_ALERTS),
            200
        )
        assertTrue(truth.sentinelReachable)
        assertTrue(WearableCapability.SENTINEL_ALERTS in truth.usableCapabilities)
        assertFalse(WearableCapability.QUICK_ACTIONS in truth.usableCapabilities)
    }

    @Test fun staleChannelRemovesPrivilegedCapabilities() {
        val truth = WearableAdapterPolicy.evaluate(
            observation(),
            activeProof(),
            setOf(WearableCapability.SENTINEL_ALERTS, WearableCapability.QUICK_ACTIONS),
            1_100
        )
        assertFalse(truth.sentinelReachable)
        assertFalse(WearableCapability.SENTINEL_ALERTS in truth.usableCapabilities)
        assertFalse(WearableCapability.QUICK_ACTIONS in truth.usableCapabilities)
    }

    @Test fun platformLinkStateRemainsIndependentFromSentinelReachability() {
        val truth = WearableAdapterPolicy.evaluate(
            observation(link = WearablePlatformLinkState.DISCOVERED),
            activeProof(),
            setOf(WearableCapability.SENTINEL_ALERTS),
            200
        )
        assertEquals(WearablePlatformLinkState.DISCOVERED, truth.platformLinkState)
        assertTrue(truth.sentinelReachable)
    }
}
