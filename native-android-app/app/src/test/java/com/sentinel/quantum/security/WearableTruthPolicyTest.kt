package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearableTruthPolicyTest {
    private fun observation(
        bonded: Boolean = false,
        associated: Boolean = false,
        connected: Boolean = false,
        transports: Set<WearableTransport> = setOf(WearableTransport.BLUETOOTH_LE)
    ) = WearableObservation(
        stableId = "wearable-test-id",
        displayName = "Test watch",
        transports = transports,
        bonded = bonded,
        companionAssociated = associated,
        sentinelChannelConnected = connected,
        capabilities = setOf(
            WearableCapability.CONNECTION_STATE,
            WearableCapability.SENTINEL_ALERTS,
            WearableCapability.QUICK_ACTIONS
        )
    )

    @Test fun discoveryNeverClaimsPairingOrSentinelIntegration() {
        val truth = WearableTruthPolicy.evaluate(observation())
        assertEquals(WearableIntegrationState.DISCOVERED, truth.state)
        assertTrue(WearableCapability.CONNECTION_STATE in truth.capabilities)
        assertFalse(WearableCapability.SENTINEL_ALERTS in truth.capabilities)
        assertFalse(WearableCapability.QUICK_ACTIONS in truth.capabilities)
    }

    @Test fun bluetoothBondStillDoesNotClaimSentinelChannel() {
        val truth = WearableTruthPolicy.evaluate(observation(bonded = true))
        assertEquals(WearableIntegrationState.BONDED, truth.state)
        assertFalse(WearableCapability.SENTINEL_ALERTS in truth.capabilities)
    }

    @Test fun companionAssociationRemainsDistinctFromLiveSentinelConnection() {
        val truth = WearableTruthPolicy.evaluate(observation(bonded = true, associated = true))
        assertEquals(WearableIntegrationState.COMPANION_ASSOCIATED, truth.state)
        assertFalse(WearableCapability.QUICK_ACTIONS in truth.capabilities)
    }

    @Test fun sentinelCapabilitiesRequireLiveSentinelChannel() {
        val truth = WearableTruthPolicy.evaluate(
            observation(bonded = true, associated = true, connected = true)
        )
        assertEquals(WearableIntegrationState.SENTINEL_CONNECTED, truth.state)
        assertTrue(WearableCapability.SENTINEL_ALERTS in truth.capabilities)
        assertTrue(WearableCapability.QUICK_ACTIONS in truth.capabilities)
    }

    @Test fun modelIsVendorNeutral() {
        val truth = WearableTruthPolicy.evaluate(
            observation(
                bonded = true,
                associated = true,
                connected = true,
                transports = setOf(WearableTransport.VENDOR_BRIDGE)
            )
        )
        assertEquals(WearableIntegrationState.SENTINEL_CONNECTED, truth.state)
    }
}
