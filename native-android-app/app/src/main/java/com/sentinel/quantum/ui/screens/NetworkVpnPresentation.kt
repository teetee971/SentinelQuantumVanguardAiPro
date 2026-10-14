package com.sentinel.quantum.ui.screens

import com.sentinel.quantum.security.NetworkRiskLevel
import com.sentinel.quantum.security.SentinelVpnController
import com.sentinel.quantum.ui.design.SentinelStatus

/**
 * Pure presentation bridge between network/VPN runtime truth and the D.1 status vocabulary.
 *
 * A low heuristic risk is deliberately not promoted to OK: absence of a local warning signal is
 * not proof that a network is safe. Likewise, VPN may only become OK after the WireGuard backend
 * has actually reported the tunnel UP through RuntimeState.PROTECTED.
 */
object NetworkVpnPresentation {

    fun networkStatus(riskLevel: NetworkRiskLevel): SentinelStatus = when (riskLevel) {
        NetworkRiskLevel.HIGH -> SentinelStatus.BAD
        NetworkRiskLevel.MEDIUM -> SentinelStatus.WARN
        NetworkRiskLevel.LOW -> SentinelStatus.UNKNOWN
    }

    fun aggregateNetworkStatus(levels: List<NetworkRiskLevel>): SentinelStatus {
        if (levels.any { it == NetworkRiskLevel.HIGH }) return SentinelStatus.BAD
        if (levels.any { it == NetworkRiskLevel.MEDIUM }) return SentinelStatus.WARN
        return SentinelStatus.UNKNOWN
    }

    fun vpnStatus(state: SentinelVpnController.RuntimeState): SentinelStatus = when (state) {
        SentinelVpnController.RuntimeState.READY_NO_GATEWAY -> SentinelStatus.LOCKED
        SentinelVpnController.RuntimeState.CONSENT_REQUIRED -> SentinelStatus.LIMITED
        SentinelVpnController.RuntimeState.CONNECTING -> SentinelStatus.WARN
        SentinelVpnController.RuntimeState.PROTECTED -> SentinelStatus.OK
        SentinelVpnController.RuntimeState.DEGRADED -> SentinelStatus.WARN
        SentinelVpnController.RuntimeState.FAILED -> SentinelStatus.BAD
        SentinelVpnController.RuntimeState.DISCONNECTED -> SentinelStatus.UNKNOWN
    }

    fun gatewayStatus(status: SentinelVpnController.GatewayStatus): SentinelStatus = when (status) {
        SentinelVpnController.GatewayStatus.AVAILABLE -> SentinelStatus.OK
        SentinelVpnController.GatewayStatus.DEGRADED -> SentinelStatus.WARN
        SentinelVpnController.GatewayStatus.PLANNED,
        SentinelVpnController.GatewayStatus.PROVISIONING,
        SentinelVpnController.GatewayStatus.DRAINING,
        SentinelVpnController.GatewayStatus.OFFLINE,
        SentinelVpnController.GatewayStatus.REVOKED -> SentinelStatus.LOCKED
    }
}
