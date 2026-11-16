package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SentinelVpnControllerTest {

    @Test
    fun acceptsDualStackFullTunnelConfiguration() {
        val config = SentinelVpnController.parseAndValidateFullTunnelConfig(
            validConfig("0.0.0.0/0, ::/0").toByteArray()
        )

        assertEquals(1, config.peers.size)
        val routes = config.peers.single().allowedIps
        assertTrue(routes.contains(com.wireguard.config.InetNetwork.parse("0.0.0.0/0")))
        assertTrue(routes.contains(com.wireguard.config.InetNetwork.parse("::/0")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIpv4OnlyConfiguration() {
        SentinelVpnController.parseAndValidateFullTunnelConfig(
            validConfig("0.0.0.0/0").toByteArray()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIpv6OnlyConfiguration() {
        SentinelVpnController.parseAndValidateFullTunnelConfig(
            validConfig("::/0").toByteArray()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedConfigurationBeforeParsing() {
        SentinelVpnController.parseAndValidateFullTunnelConfig(
            ByteArray(SentinelVpnController.MAX_CONFIG_BYTES + 1) { 'A'.code.toByte() }
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNulBytes() {
        val bytes = validConfig("0.0.0.0/0, ::/0").toByteArray() + byteArrayOf(0)
        SentinelVpnController.parseAndValidateFullTunnelConfig(bytes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFullTunnelWithoutDns() {
        SentinelVpnController.parseAndValidateFullTunnelConfig(
            validConfig("0.0.0.0/0, ::/0", dns = "").toByteArray()
        )
    }

    @Test
    fun acceptsOnlyDnsPinnedToAvailableGateway() {
        val config = SentinelVpnController.parseAndValidateFullTunnelConfig(
            validConfig("0.0.0.0/0, ::/0", dns = "10.73.0.1, fd73::1").toByteArray()
        )
        val gateway = SentinelVpnController.GatewayDescriptor(
            id = "fr-par-01",
            countryCode = "FR",
            status = SentinelVpnController.GatewayStatus.AVAILABLE,
            dnsServerAddresses = setOf("10.73.0.1", "fd73::1")
        )

        SentinelVpnController.validateGatewayDns(config, gateway)
    }

    @Test
    fun rejectsUnpinnedExternalDns() {
        val config = SentinelVpnController.parseAndValidateFullTunnelConfig(
            validConfig("0.0.0.0/0, ::/0", dns = "1.1.1.1").toByteArray()
        )
        val gateway = SentinelVpnController.GatewayDescriptor(
            id = "fr-par-01",
            countryCode = "FR",
            status = SentinelVpnController.GatewayStatus.AVAILABLE,
            dnsServerAddresses = setOf("10.73.0.1")
        )

        assertThrows(IllegalArgumentException::class.java) {
            SentinelVpnController.validateGatewayDns(config, gateway)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAvailableGatewayWithoutDnsPins() {
        SentinelVpnController.GatewayDescriptor(
            id = "fr-par-01",
            countryCode = "FR",
            status = SentinelVpnController.GatewayStatus.AVAILABLE,
            dnsServerAddresses = emptySet()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHostnameAsGatewayDnsPin() {
        SentinelVpnController.GatewayDescriptor(
            id = "fr-par-01",
            countryCode = "FR",
            status = SentinelVpnController.GatewayStatus.AVAILABLE,
            dnsServerAddresses = setOf("resolver.example")
        )
    }

    private fun validConfig(allowedIps: String, dns: String = "1.1.1.1"): String = """
        [Interface]
        PrivateKey = AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=
        Address = 10.73.0.2/32, fd73::2/128
        ${if (dns.isNotBlank()) "DNS = $dns" else ""}

        [Peer]
        PublicKey = ISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0A=
        AllowedIPs = $allowedIps
        Endpoint = 192.0.2.10:51820
        PersistentKeepalive = 25
    """.trimIndent()
}
