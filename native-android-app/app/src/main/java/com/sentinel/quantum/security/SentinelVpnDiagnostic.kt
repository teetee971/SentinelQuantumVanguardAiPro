package com.sentinel.quantum.security

/**
 * Maps Sentinel's own WireGuard runtime state to diagnostic evidence.
 * Android VPN consent is intentionally not treated as proof of an active tunnel.
 */
object SentinelVpnDiagnostic {
    fun capabilityObservation(
        state: SentinelVpnController.RuntimeState
    ): SentinelSensitiveCapabilityDiagnostic.Observation =
        when (state) {
            SentinelVpnController.RuntimeState.PROTECTED ->
                SentinelSensitiveCapabilityDiagnostic.Observation.ENABLED
            SentinelVpnController.RuntimeState.DISCONNECTED,
            SentinelVpnController.RuntimeState.READY_NO_GATEWAY,
            SentinelVpnController.RuntimeState.CONSENT_REQUIRED ->
                SentinelSensitiveCapabilityDiagnostic.Observation.DISABLED
            SentinelVpnController.RuntimeState.CONNECTING,
            SentinelVpnController.RuntimeState.DEGRADED,
            SentinelVpnController.RuntimeState.FAILED ->
                SentinelSensitiveCapabilityDiagnostic.Observation.NOT_OBSERVABLE
        }

    fun evidence(
        state: SentinelVpnController.RuntimeState,
        observedAtEpochMillis: Long
    ): SentinelDeviceDiagnostic.Evidence {
        val status = when (state) {
            SentinelVpnController.RuntimeState.PROTECTED -> SentinelDeviceDiagnostic.Status.OK
            SentinelVpnController.RuntimeState.DEGRADED -> SentinelDeviceDiagnostic.Status.WARNING
            SentinelVpnController.RuntimeState.FAILED -> SentinelDeviceDiagnostic.Status.WARNING
            SentinelVpnController.RuntimeState.CONNECTING -> SentinelDeviceDiagnostic.Status.UNKNOWN
            SentinelVpnController.RuntimeState.DISCONNECTED,
            SentinelVpnController.RuntimeState.READY_NO_GATEWAY,
            SentinelVpnController.RuntimeState.CONSENT_REQUIRED -> SentinelDeviceDiagnostic.Status.NOT_ACCESSIBLE
        }
        val summary = when (state) {
            SentinelVpnController.RuntimeState.PROTECTED -> "Tunnel VPN Sentinel confirmé actif par le contrôleur WireGuard."
            SentinelVpnController.RuntimeState.DEGRADED -> "Tunnel VPN Sentinel dans un état dégradé."
            SentinelVpnController.RuntimeState.FAILED -> "Dernière opération VPN Sentinel en échec."
            SentinelVpnController.RuntimeState.CONNECTING -> "Connexion VPN Sentinel en cours ; état final non établi."
            SentinelVpnController.RuntimeState.CONSENT_REQUIRED -> "Consentement VPN Android requis ; aucun tunnel Sentinel actif n'est établi."
            SentinelVpnController.RuntimeState.READY_NO_GATEWAY -> "Aucune passerelle Sentinel disponible ; aucun tunnel actif n'est établi."
            SentinelVpnController.RuntimeState.DISCONNECTED -> "VPN Sentinel déconnecté ; cela ne décrit pas les éventuels VPN d'autres applications."
        }
        return SentinelDeviceDiagnostic.Evidence(
            id = "sentinel.vpn.runtime",
            status = status,
            summary = summary,
            observedValue = state.name,
            observedAtEpochMillis = observedAtEpochMillis
        )
    }
}
