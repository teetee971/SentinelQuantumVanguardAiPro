package com.sentinel.quantum.security

import com.wireguard.crypto.KeyPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnProvisioningContractTest {
    @Test fun vpnWireGuardKeysValidateAndFingerprintDeterministically() {
        val pair = KeyPair()
        val publicKey = pair.publicKey.toBase64()
        val privateKey = pair.privateKey.toBase64()

        assertTrue(VpnWireGuardIdentityStore.validWireGuardKey(publicKey))
        assertTrue(VpnWireGuardIdentityStore.validWireGuardKey(privateKey))
        val fingerprint = VpnWireGuardIdentityStore.fingerprint(publicKey)
        assertTrue(fingerprint.matches(Regex("[a-f0-9]{64}")))
        assertEquals(fingerprint, VpnWireGuardIdentityStore.fingerprint(publicKey))
    }

    @Test fun malformedVpnWireGuardKeysAreRejected() {
        assertFalse(VpnWireGuardIdentityStore.validWireGuardKey(""))
        assertFalse(VpnWireGuardIdentityStore.validWireGuardKey("A".repeat(44)))
        assertFalse(VpnWireGuardIdentityStore.validWireGuardKey("not-base64"))
    }

    @Test fun provisioningRejectsMismatchedDeviceKeyBeforeParsingResponse() {
        val device = KeyPair()
        val other = KeyPair()
        val gateway = plannedGateway()
        val result = VpnProvisioningContract.buildConfiguration(
            responseBody = "{}",
            gateway = gateway,
            devicePublicKeyBase64 = device.publicKey.toBase64(),
            devicePrivateKeyBase64 = other.privateKey.toBase64(),
            now = 2_000_000_000_000L
        )
        assertFalse(result.accepted)
        assertEquals("VPN_PROVISIONING_DEVICE_KEY_MISMATCH", result.reason)
    }

    @Test fun provisioningRejectsNonAvailableGatewayBeforeParsingResponse() {
        val device = KeyPair()
        val gateway = plannedGateway()
        val result = VpnProvisioningContract.buildConfiguration(
            responseBody = "{}",
            gateway = gateway,
            devicePublicKeyBase64 = device.publicKey.toBase64(),
            devicePrivateKeyBase64 = device.privateKey.toBase64(),
            now = 2_000_000_000_000L
        )
        assertFalse(result.accepted)
        assertEquals("VPN_PROVISIONING_GATEWAY_NOT_AVAILABLE", result.reason)
    }

    private fun plannedGateway() = SignedVpnGatewayCatalogVerifier.Gateway(
        id = "fr-par-01",
        countryCode = "FR",
        status = SentinelVpnController.GatewayStatus.PLANNED,
        endpointHostname = null,
        endpointPort = null,
        ipv4 = true,
        ipv6 = true,
        loadPercent = null,
        latencyMs = null,
        healthCheckedAtMs = null,
        dnsServerAddresses = emptySet()
    )
}
