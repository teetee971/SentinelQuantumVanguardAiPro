package com.sentinel.quantum.security

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelVpnRuntimeCoordinatorTest {
    private val now = 2_000_000_000_000L

    @Test fun connectsThroughVerifiedCatalogProvisioningAndTunnel() = runBlocking {
        val sequenceStore = FakeSequenceStore(3)
        val gateway = gateway("fr-par-01", "FR", 15)
        val catalog = catalog(sequence = 4, gateways = listOf(gateway))
        val provisionCalls = mutableListOf<List<Any>>()
        val tunnel = FakeTunnel()
        val config = "[Interface]\nPrivateKey = secret".toByteArray()

        val coordinator = SentinelVpnRuntimeCoordinator(
            catalogVerifier = SentinelVpnRuntimeCoordinator.CatalogVerifier { _, highest, _ ->
                assertEquals(3L, highest)
                SignedVpnGatewayCatalogVerifier.Result(true, "VPN_CATALOG_ACCEPTED", catalog)
            },
            sequenceStore = sequenceStore,
            identityProvider = SentinelVpnRuntimeCoordinator.IdentityProvider {
                SentinelVpnRuntimeCoordinator.IdentityMaterial(
                    publicKeyBase64 = "pub",
                    privateKeyBase64 = "priv"
                )
            },
            provisioner = SentinelVpnRuntimeCoordinator.Provisioner { gatewayId, publicKey, sequence, token ->
                provisionCalls += listOf(gatewayId, publicKey, sequence, token)
                VpnProvisioningClient.FetchResult(
                    accepted = true,
                    reason = "VPN_PROVISIONING_FETCH_OK",
                    statusCode = 201,
                    responseBody = "{}"
                )
            },
            configurationBuilder = SentinelVpnRuntimeCoordinator.ConfigurationBuilder { _, selected, publicKey, privateKey, _ ->
                assertEquals("fr-par-01", selected.id)
                assertEquals("pub", publicKey)
                assertEquals("priv", privateKey)
                VpnProvisioningContract.Result(
                    accepted = true,
                    reason = "VPN_PROVISIONING_READY",
                    configuration = config
                )
            },
            tunnelBridge = tunnel
        )

        val result = coordinator.connect(
            signedCatalogEnvelope = "signed",
            countryCode = "FR",
            accessToken = "A".repeat(32),
            now = now
        )

        assertTrue(result.accepted)
        assertEquals(SentinelVpnController.RuntimeState.PROTECTED, result.state)
        assertEquals("fr-par-01", result.gatewayId)
        assertEquals(4L, result.catalogSequence)
        assertEquals(4L, sequenceStore.value)
        assertEquals(
            listOf("fr-par-01", "pub", 4L, "A".repeat(32)),
            provisionCalls.single()
        )
        assertTrue(config.all { it == 0.toByte() })
        assertEquals("fr-par-01", tunnel.lastGateway?.id)
    }

    @Test fun acceptedCatalogWithoutAvailableGatewayFailsClosedButAdvancesSequence() = runBlocking {
        val sequenceStore = FakeSequenceStore(1)
        val planned = gateway(
            id = "fr-par-01",
            country = "FR",
            latency = 15,
            status = SentinelVpnController.GatewayStatus.PLANNED
        )
        val coordinator = coordinator(
            catalog = catalog(2, listOf(planned)),
            sequenceStore = sequenceStore
        )

        val result = coordinator.connect(
            signedCatalogEnvelope = "signed",
            countryCode = "FR",
            accessToken = "A".repeat(32),
            now = now
        )

        assertFalse(result.accepted)
        assertEquals("VPN_RUNTIME_NO_AVAILABLE_GATEWAY", result.reason)
        assertEquals(2L, sequenceStore.value)
    }

    @Test fun catalogSequencePersistenceFailureStopsBeforeProvisioning() = runBlocking {
        var provisioned = false
        val coordinator = SentinelVpnRuntimeCoordinator(
            catalogVerifier = SentinelVpnRuntimeCoordinator.CatalogVerifier { _, _, _ ->
                SignedVpnGatewayCatalogVerifier.Result(
                    true,
                    "VPN_CATALOG_ACCEPTED",
                    catalog(5, listOf(gateway("fr-par-01", "FR", 10)))
                )
            },
            sequenceStore = object : SentinelVpnRuntimeCoordinator.CatalogSequenceStore {
                override fun load(): Long = 4
                override fun save(sequence: Long): Boolean = false
            },
            identityProvider = SentinelVpnRuntimeCoordinator.IdentityProvider {
                error("identity must not be requested")
            },
            provisioner = SentinelVpnRuntimeCoordinator.Provisioner { _, _, _, _ ->
                provisioned = true
                error("must not provision")
            },
            configurationBuilder = SentinelVpnRuntimeCoordinator.ConfigurationBuilder { _, _, _, _, _ ->
                error("must not build")
            },
            tunnelBridge = FakeTunnel()
        )

        val result = coordinator.connect("signed", "FR", "A".repeat(32), now)
        assertFalse(result.accepted)
        assertEquals("VPN_RUNTIME_CATALOG_SEQUENCE_STORE_FAILED", result.reason)
        assertFalse(provisioned)
    }

    @Test fun provisioningFailureNeverStartsTunnel() = runBlocking {
        val tunnel = FakeTunnel()
        val coordinator = SentinelVpnRuntimeCoordinator(
            catalogVerifier = SentinelVpnRuntimeCoordinator.CatalogVerifier { _, _, _ ->
                SignedVpnGatewayCatalogVerifier.Result(
                    true,
                    "VPN_CATALOG_ACCEPTED",
                    catalog(3, listOf(gateway("fr-par-01", "FR", 10)))
                )
            },
            sequenceStore = FakeSequenceStore(2),
            identityProvider = SentinelVpnRuntimeCoordinator.IdentityProvider {
                SentinelVpnRuntimeCoordinator.IdentityMaterial("pub", "priv")
            },
            provisioner = SentinelVpnRuntimeCoordinator.Provisioner { _, _, _, _ ->
                VpnProvisioningClient.FetchResult(
                    accepted = false,
                    reason = "VPN_PROVISIONING_NETWORK_ERROR"
                )
            },
            configurationBuilder = SentinelVpnRuntimeCoordinator.ConfigurationBuilder { _, _, _, _, _ ->
                error("must not build config")
            },
            tunnelBridge = tunnel
        )

        val result = coordinator.connect("signed", "FR", "A".repeat(32), now)
        assertFalse(result.accepted)
        assertEquals("VPN_PROVISIONING_NETWORK_ERROR", result.reason)
        assertNull(tunnel.lastGateway)
    }

    private fun coordinator(
        catalog: SignedVpnGatewayCatalogVerifier.Catalog,
        sequenceStore: FakeSequenceStore
    ) = SentinelVpnRuntimeCoordinator(
        catalogVerifier = SentinelVpnRuntimeCoordinator.CatalogVerifier { _, _, _ ->
            SignedVpnGatewayCatalogVerifier.Result(true, "VPN_CATALOG_ACCEPTED", catalog)
        },
        sequenceStore = sequenceStore,
        identityProvider = SentinelVpnRuntimeCoordinator.IdentityProvider {
            error("identity must not be requested")
        },
        provisioner = SentinelVpnRuntimeCoordinator.Provisioner { _, _, _, _ ->
            error("provisioning must not be requested")
        },
        configurationBuilder = SentinelVpnRuntimeCoordinator.ConfigurationBuilder { _, _, _, _, _ ->
            error("configuration must not be built")
        },
        tunnelBridge = FakeTunnel()
    )

    private fun catalog(
        sequence: Long,
        gateways: List<SignedVpnGatewayCatalogVerifier.Gateway>
    ) = SignedVpnGatewayCatalogVerifier.Catalog(
        catalogId = SignedVpnGatewayCatalogVerifier.EXPECTED_CATALOG_ID,
        sequence = sequence,
        issuedAtMs = now - 1_000,
        expiresAtMs = now + 60_000,
        issuerId = "sentinel-vpn",
        keyId = "trusted",
        gateways = gateways
    )

    private fun gateway(
        id: String,
        country: String,
        latency: Int,
        status: SentinelVpnController.GatewayStatus = SentinelVpnController.GatewayStatus.AVAILABLE
    ) = SignedVpnGatewayCatalogVerifier.Gateway(
        id = id,
        countryCode = country,
        status = status,
        endpointHostname = if (status == SentinelVpnController.GatewayStatus.AVAILABLE) "vpn-$id.example.com" else null,
        endpointPort = if (status == SentinelVpnController.GatewayStatus.AVAILABLE) 51820 else null,
        ipv4 = true,
        ipv6 = true,
        loadPercent = if (status == SentinelVpnController.GatewayStatus.AVAILABLE) 20 else null,
        latencyMs = if (status == SentinelVpnController.GatewayStatus.AVAILABLE) latency else null,
        healthCheckedAtMs = if (status == SentinelVpnController.GatewayStatus.AVAILABLE) now - 1_000 else null,
        dnsServerAddresses = if (status == SentinelVpnController.GatewayStatus.AVAILABLE) {
            setOf("10.73.0.1", "fd73:1::1")
        } else {
            emptySet()
        }
    )

    private class FakeSequenceStore(initial: Long) : SentinelVpnRuntimeCoordinator.CatalogSequenceStore {
        var value = initial
        override fun load(): Long = value
        override fun save(sequence: Long): Boolean {
            if (sequence < value) return false
            value = sequence
            return true
        }
    }

    private class FakeTunnel : SentinelVpnRuntimeCoordinator.TunnelBridge {
        var lastGateway: SentinelVpnController.GatewayDescriptor? = null

        override suspend fun connect(
            gateway: SentinelVpnController.GatewayDescriptor,
            configuration: ByteArray
        ): SentinelVpnController.OperationResult {
            lastGateway = gateway
            return SentinelVpnController.OperationResult(
                SentinelVpnController.RuntimeState.PROTECTED,
                "TUNNEL_UP"
            )
        }

        override suspend fun disconnect(): SentinelVpnController.OperationResult =
            SentinelVpnController.OperationResult(
                SentinelVpnController.RuntimeState.DISCONNECTED,
                "TUNNEL_DOWN"
            )

        override fun prepareConsentIntent() = null

        override fun currentState(): SentinelVpnController.RuntimeState =
            SentinelVpnController.RuntimeState.DISCONNECTED
    }
}
