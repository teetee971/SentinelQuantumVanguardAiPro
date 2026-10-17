package com.sentinel.quantum.ui.screens

import com.sentinel.quantum.security.NetworkRiskLevel
import com.sentinel.quantum.security.SentinelVpnController
import com.sentinel.quantum.ui.design.SentinelStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkVpnPresentationTest {

    @Test
    fun `network risk never promotes low heuristic risk to OK`() {
        assertEquals(SentinelStatus.BAD, NetworkVpnPresentation.networkStatus(NetworkRiskLevel.HIGH))
        assertEquals(SentinelStatus.WARN, NetworkVpnPresentation.networkStatus(NetworkRiskLevel.MEDIUM))
        assertEquals(SentinelStatus.UNKNOWN, NetworkVpnPresentation.networkStatus(NetworkRiskLevel.LOW))
    }

    @Test
    fun `network summary stays truthful and is dominated by strongest observed risk`() {
        assertEquals(
            SentinelStatus.UNKNOWN,
            NetworkVpnPresentation.aggregateNetworkStatus(emptyList())
        )
        assertEquals(
            SentinelStatus.UNKNOWN,
            NetworkVpnPresentation.aggregateNetworkStatus(
                listOf(NetworkRiskLevel.LOW, NetworkRiskLevel.LOW)
            )
        )
        assertEquals(
            SentinelStatus.WARN,
            NetworkVpnPresentation.aggregateNetworkStatus(
                listOf(NetworkRiskLevel.LOW, NetworkRiskLevel.MEDIUM)
            )
        )
        assertEquals(
            SentinelStatus.BAD,
            NetworkVpnPresentation.aggregateNetworkStatus(
                listOf(NetworkRiskLevel.LOW, NetworkRiskLevel.MEDIUM, NetworkRiskLevel.HIGH)
            )
        )
    }

    @Test
    fun `VPN is OK only after backend reports PROTECTED`() {
        SentinelVpnController.RuntimeState.entries.forEach { state ->
            val expected = when (state) {
                SentinelVpnController.RuntimeState.PROTECTED -> SentinelStatus.OK
                SentinelVpnController.RuntimeState.CONNECTING,
                SentinelVpnController.RuntimeState.DEGRADED -> SentinelStatus.WARN
                SentinelVpnController.RuntimeState.CONSENT_REQUIRED -> SentinelStatus.LIMITED
                SentinelVpnController.RuntimeState.READY_NO_GATEWAY -> SentinelStatus.LOCKED
                SentinelVpnController.RuntimeState.FAILED -> SentinelStatus.BAD
                SentinelVpnController.RuntimeState.DISCONNECTED -> SentinelStatus.UNKNOWN
            }
            assertEquals("Unexpected mapping for $state", expected, NetworkVpnPresentation.vpnStatus(state))
        }
    }

    @Test
    fun `unavailable gateway remains locked and never protected`() {
        val unavailable = listOf(
            SentinelVpnController.GatewayStatus.PLANNED,
            SentinelVpnController.GatewayStatus.PROVISIONING,
            SentinelVpnController.GatewayStatus.DRAINING,
            SentinelVpnController.GatewayStatus.OFFLINE,
            SentinelVpnController.GatewayStatus.REVOKED
        )
        unavailable.forEach { status ->
            assertEquals(SentinelStatus.LOCKED, NetworkVpnPresentation.gatewayStatus(status))
        }
        assertEquals(
            SentinelStatus.WARN,
            NetworkVpnPresentation.gatewayStatus(SentinelVpnController.GatewayStatus.DEGRADED)
        )
        assertEquals(
            SentinelStatus.OK,
            NetworkVpnPresentation.gatewayStatus(SentinelVpnController.GatewayStatus.AVAILABLE)
        )
    }
}
