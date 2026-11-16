package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class SignedVpnGatewayCatalogVerifierTest {
    private val trustedPair = keyPair("secp256r1")
    private val attackerPair = keyPair("secp256r1")
    private val now = 2_000_000_000_000L

    @Test fun acceptsFreshSignedCatalogAndSelectsQuickConnect() {
        val result = verifier().verify(
            envelope(payload(gateways = listOf(
                gateway("de-fra-01", "DE", latency = 18, load = 50),
                gateway("fr-par-01", "FR", latency = 22, load = 35)
            )), trustedPair),
            highestAcceptedSequence = 6,
            now = now
        )
        assertTrue(result.accepted)
        assertEquals(7L, result.catalog?.sequence)
        assertEquals("de-fra-01", result.catalog?.selectBestAvailable(now = now)?.id)
        assertEquals("fr-par-01", result.catalog?.selectBestAvailable("FR", now)?.id)
    }

    @Test fun rejectsForgeryReplayExpiryAndFutureIssue() {
        assertEquals(
            "VPN_CATALOG_SIGNATURE_INVALID",
            verifier().verify(envelope(payload(), attackerPair), 0, now).reason
        )
        assertEquals(
            "VPN_CATALOG_ROLLBACK_REJECTED",
            verifier().verify(envelope(payload(sequence = 4), trustedPair), 4, now).reason
        )
        assertEquals(
            "VPN_CATALOG_EXPIRED",
            verifier().verify(
                envelope(payload(issuedAt = now - 20_000, expiresAt = now - 1), trustedPair),
                0,
                now
            ).reason
        )
        assertEquals(
            "VPN_CATALOG_ISSUED_IN_FUTURE",
            verifier().verify(
                envelope(payload(issuedAt = now + 301_000, expiresAt = now + 600_000), trustedPair),
                0,
                now
            ).reason
        )
    }

    @Test fun rejectsAvailableGatewayWithoutFreshDualStackAndPinnedDns() {
        val cases = listOf(
            "fr-par-01|FR|AVAILABLE|vpn-fr.example.com|51820|1|0|20|15|${now - 1_000}|10.73.0.1,fd73::1",
            "fr-par-01|FR|AVAILABLE|vpn-fr.example.com|51820|1|1|20|15|${now - 301_000}|10.73.0.1,fd73::1",
            "fr-par-01|FR|AVAILABLE|vpn-fr.example.com|51820|1|1|20|15|${now - 1_000}|-"
        )
        cases.forEach { line ->
            val result = verifier().verify(envelope(payload(gateways = listOf(line)), trustedPair), 0, now)
            assertFalse(result.accepted)
            assertEquals("VPN_CATALOG_SCHEMA_INVALID", result.reason)
        }
    }

    @Test fun rejectsPlaceholderEndpointAndDuplicateOrUnsortedGatewayIds() {
        val placeholder = gateway("fr-par-01", "FR")
            .replace("vpn-fr-par-01.example.com", "vpn-fr-par-01.example.invalid")
        assertEquals(
            "VPN_CATALOG_SCHEMA_INVALID",
            verifier().verify(envelope(payload(gateways = listOf(placeholder)), trustedPair), 0, now).reason
        )

        val unsorted = listOf(gateway("fr-par-02", "FR"), gateway("fr-par-01", "FR"))
        assertEquals(
            "VPN_CATALOG_SCHEMA_INVALID",
            verifier().verify(envelope(payload(gateways = unsorted), trustedPair), 0, now).reason
        )

        val duplicate = listOf(gateway("fr-par-01", "FR"), gateway("fr-par-01", "FR"))
        assertEquals(
            "VPN_CATALOG_SCHEMA_INVALID",
            verifier().verify(envelope(payload(gateways = duplicate), trustedPair), 0, now).reason
        )
    }

    @Test fun plannedGatewayMayRemainNonConnectableAndSelectorFailsClosed() {
        val planned = "fr-par-01|FR|PLANNED|-|-|1|1|-|-|-|-"
        val result = verifier().verify(
            envelope(payload(gateways = listOf(planned)), trustedPair),
            0,
            now
        )
        assertTrue(result.accepted)
        assertNull(result.catalog?.selectBestAvailable(now = now))
        assertNull(result.catalog?.selectBestAvailable("FR", now))
    }

    @Test fun controllerDescriptorIsDerivedOnlyFromVerifiedCatalogData() {
        val result = verifier().verify(envelope(payload(), trustedPair), 0, now)
        val descriptor = result.catalog!!.gateways.single().toControllerDescriptor()
        assertEquals("fr-par-01", descriptor.id)
        assertEquals("FR", descriptor.countryCode)
        assertEquals(SentinelVpnController.GatewayStatus.AVAILABLE, descriptor.status)
        assertEquals(setOf("10.73.0.1", "fd73:0:0:0:0:0:0:1"), descriptor.dnsServerAddresses)
    }

    private fun verifier() = SignedVpnGatewayCatalogVerifier(
        mapOf("trusted" to trustedPair.public),
        "sentinel-vpn"
    )

    private fun payload(
        sequence: Long = 7,
        issuedAt: Long = now - 1_000,
        expiresAt: Long = now + 3_600_000,
        gateways: List<String> = listOf(gateway("fr-par-01", "FR"))
    ): String = buildList {
        add(SignedVpnGatewayCatalogVerifier.DOMAIN)
        add("catalog_id=${SignedVpnGatewayCatalogVerifier.EXPECTED_CATALOG_ID}")
        add("sequence=$sequence")
        add("issued_at_ms=$issuedAt")
        add("expires_at_ms=$expiresAt")
        add("issuer_id=sentinel-vpn")
        add("key_id=trusted")
        gateways.forEach { add("gateway=$it") }
    }.joinToString("\n")

    private fun gateway(
        id: String,
        country: String,
        latency: Int = 15,
        load: Int = 20
    ): String {
        val host = "vpn-$id.example.com"
        return "$id|$country|AVAILABLE|$host|51820|1|1|$load|$latency|${now - 1_000}|10.73.0.1,fd73::1"
    }

    private fun envelope(payload: String, signer: KeyPair): String {
        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(signer.private)
            update(payloadBytes)
        }.sign()
        return "key_id=trusted\npayload_hex=${payloadBytes.encodeHex()}\nsignature_hex=${signature.encodeHex()}"
    }

    private fun keyPair(curve: String): KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec(curve))
        generateKeyPair()
    }

    private fun ByteArray.encodeHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
