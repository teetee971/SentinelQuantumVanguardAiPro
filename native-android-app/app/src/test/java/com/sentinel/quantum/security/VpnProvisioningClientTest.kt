package com.sentinel.quantum.security

import com.wireguard.crypto.KeyPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnProvisioningClientTest {
    @Test fun accessTokenValidationIsBoundedAndCanonical() {
        assertTrue(VpnProvisioningClient.validAccessToken("A".repeat(32)))
        assertFalse(VpnProvisioningClient.validAccessToken("short"))
        assertFalse(VpnProvisioningClient.validAccessToken(" " + "A".repeat(32)))
        assertFalse(VpnProvisioningClient.validAccessToken("A".repeat(31) + "!"))
    }

    @Test fun jsonQuoteEscapesControlCharacters() {
        assertEquals(
            "\"a\\\"b\\\\c\\n\"",
            VpnProvisioningClient.quote("a\"b\\c\n")
        )
    }

    @Test fun invalidInputsFailBeforeAnyNetworkRequest() {
        val client = VpnProvisioningClient(
            endpointUrl = "https://vpn.example.com/v1/provision",
            allowedHosts = setOf("vpn.example.com")
        )
        val pair = KeyPair()

        val badGateway = client.provision(
            gatewayId = "INVALID!",
            devicePublicKeyBase64 = pair.publicKey.toBase64(),
            catalogSequence = 1,
            accessToken = "A".repeat(32)
        )
        assertFalse(badGateway.accepted)
        assertEquals("VPN_PROVISIONING_GATEWAY_ID_INVALID", badGateway.reason)

        val badToken = client.provision(
            gatewayId = "fr-par-01",
            devicePublicKeyBase64 = pair.publicKey.toBase64(),
            catalogSequence = 1,
            accessToken = "short"
        )
        assertFalse(badToken.accepted)
        assertEquals("VPN_PROVISIONING_ACCESS_TOKEN_INVALID", badToken.reason)

        val badSequence = client.provision(
            gatewayId = "fr-par-01",
            devicePublicKeyBase64 = pair.publicKey.toBase64(),
            catalogSequence = 0,
            accessToken = "A".repeat(32)
        )
        assertFalse(badSequence.accepted)
        assertEquals("VPN_PROVISIONING_CATALOG_SEQUENCE_INVALID", badSequence.reason)
    }
}
