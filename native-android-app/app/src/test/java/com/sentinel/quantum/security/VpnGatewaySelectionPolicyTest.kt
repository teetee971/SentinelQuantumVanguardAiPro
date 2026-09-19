package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VpnGatewaySelectionPolicyTest {
    private val now = 1_000_000L

    @Test
    fun exposesOnlyCountriesWithSelectableGateways() {
        val catalog = catalog(
            gateway("fr-par-01", "FR", SentinelVpnController.GatewayStatus.AVAILABLE, 20, 40),
            gateway("de-fra-01", "DE", SentinelVpnController.GatewayStatus.PLANNED, null, null)
        )

        val choices = VpnGatewaySelectionPolicy.availableCountries(catalog, now)

        assertEquals(listOf("FR"), choices.map { it.countryCode })
        assertEquals("fr-par-01", choices.single().bestGatewayId)
    }

    @Test
    fun selectsBestGatewayInsideExplicitCountry() {
        val catalog = catalog(
            gateway("fr-par-02", "FR", SentinelVpnController.GatewayStatus.AVAILABLE, 60, 15),
            gateway("fr-par-01", "FR", SentinelVpnController.GatewayStatus.AVAILABLE, 30, 70)
        )

        assertEquals(
            "fr-par-01",
            VpnGatewaySelectionPolicy.select(catalog, "FR", now)?.id
        )
    }

    @Test
    fun rejectsMalformedOrUnavailableCountry() {
        val catalog = catalog(
            gateway("fr-par-01", "FR", SentinelVpnController.GatewayStatus.AVAILABLE, 20, 40)
        )

        assertNull(VpnGatewaySelectionPolicy.select(catalog, "fr", now))
        assertNull(VpnGatewaySelectionPolicy.select(catalog, "US", now))
    }

    @Test
    fun expiredCatalogExposesNoCountry() {
        val expired = catalog(
            gateway("fr-par-01", "FR", SentinelVpnController.GatewayStatus.AVAILABLE, 20, 40),
            expiresAt = now
        )

        assertEquals(emptyList<VpnGatewaySelectionPolicy.CountryChoice>(), VpnGatewaySelectionPolicy.availableCountries(expired, now))
        assertNull(VpnGatewaySelectionPolicy.select(expired, "FR", now))
    }

    private fun catalog(
        vararg gateways: SignedVpnGatewayCatalogVerifier.Gateway,
        expiresAt: Long = now + 60_000L
    ) = SignedVpnGatewayCatalogVerifier.Catalog(
        catalogId = SignedVpnGatewayCatalogVerifier.EXPECTED_CATALOG_ID,
        sequence = 7,
        issuedAtMs = now - 1_000L,
        expiresAtMs = expiresAt,
        issuerId = "sentinel",
        keyId = "k1",
        gateways = gateways.toList()
    )

    private fun gateway(
        id: String,
        country: String,
        status: SentinelVpnController.GatewayStatus,
        latency: Int?,
        load: Int?
    ) = SignedVpnGatewayCatalogVerifier.Gateway(
        id = id,
        countryCode = country,
        status = status,
        endpointHostname = if (status == SentinelVpnController.GatewayStatus.AVAILABLE) "$id.example.net" else null,
        endpointPort = if (status == SentinelVpnController.GatewayStatus.AVAILABLE) 51820 else null,
        ipv4 = true,
        ipv6 = true,
        loadPercent = load,
        latencyMs = latency,
        healthCheckedAtMs = if (status == SentinelVpnController.GatewayStatus.AVAILABLE) now - 1_000L else null,
        dnsServerAddresses = if (status == SentinelVpnController.GatewayStatus.AVAILABLE) setOf("10.73.0.1") else emptySet()
    )
}
