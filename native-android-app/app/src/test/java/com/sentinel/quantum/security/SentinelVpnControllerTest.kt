package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelVpnControllerTest {

    @Test
    fun acceptsDualStackFullTunnelConfiguration() {
        val config = SentinelVpnController.parseAndValidateFullTunnelConfig(
            validConfig("0.0.0.0/0, ::/0").toByteArray()
        )

        assertEquals(1, config.peers.size)
        val routes = config.peers.single().allowedIps.map { it.toString() }.toSet()
        assertTrue("0.0.0.0/0" in routes)
        assertTrue("::/0" in routes)
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

    private fun validConfig(allowedIps: String): String = """
        [Interface]
        PrivateKey = AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=
        Address = 10.73.0.2/32, fd73::2/128
        DNS = 1.1.1.1

        [Peer]
        PublicKey = ISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0A=
        AllowedIPs = $allowedIps
        Endpoint = 192.0.2.10:51820
        PersistentKeepalive = 25
    """.trimIndent()
}
