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

    data class VpnSurface(
        val status: SentinelStatus,
        val title: String,
        val subtitle: String,
        val badge: String,
        val explanationTitle: String,
        val explanation: String
    )

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

    fun vpnSurface(state: SentinelVpnController.RuntimeState?): VpnSurface {
        if (state == null) {
            return VpnSurface(
                status = SentinelStatus.UNKNOWN,
                title = "État runtime non observé",
                subtitle = "Aucune transition VPN Sentinel récente n’est disponible dans ce processus.",
                badge = "Observation manquante",
                explanationTitle = "Pourquoi l’état reste inconnu",
                explanation = "Le consentement Android ou la présence du client WireGuard ne prouve pas qu’un tunnel Sentinel est actif. Une observation PROTECTED doit venir du contrôleur runtime."
            )
        }
        return when (state) {
            SentinelVpnController.RuntimeState.PROTECTED -> VpnSurface(
                status = SentinelStatus.OK,
                title = "Tunnel confirmé",
                subtitle = "Le backend WireGuard Sentinel a confirmé le tunnel actif.",
                badge = "Tunnel actif",
                explanationTitle = "Preuve runtime",
                explanation = "L’état protégé est limité à l’observation locale du backend WireGuard et expire si aucune transition récente n’est disponible."
            )
            SentinelVpnController.RuntimeState.CONNECTING -> VpnSurface(
                status = SentinelStatus.WARN,
                title = "Connexion en cours",
                subtitle = "Le tunnel n’est pas encore confirmé actif.",
                badge = "En cours",
                explanationTitle = "État intermédiaire",
                explanation = "Aucune protection ne doit être annoncée avant le retour explicite PROTECTED du backend WireGuard."
            )
            SentinelVpnController.RuntimeState.CONSENT_REQUIRED -> VpnSurface(
                status = SentinelStatus.LIMITED,
                title = "Consentement requis",
                subtitle = "Android doit autoriser le service VPN avant toute connexion.",
                badge = "Accès requis",
                explanationTitle = "Pourquoi la connexion est limitée",
                explanation = "Le consentement Android n’est pas une preuve de tunnel actif."
            )
            SentinelVpnController.RuntimeState.READY_NO_GATEWAY -> VpnSurface(
                status = SentinelStatus.LOCKED,
                title = "Passerelle requise",
                subtitle = "Aucune passerelle Sentinel disponible et validée n’est exposée.",
                badge = "Passerelle absente",
                explanationTitle = "Pourquoi la connexion est verrouillée",
                explanation = "Une connexion exige une passerelle AVAILABLE, des DNS épinglés et une configuration validée."
            )
            SentinelVpnController.RuntimeState.DEGRADED -> VpnSurface(
                status = SentinelStatus.WARN,
                title = "Tunnel dégradé",
                subtitle = "Le contrôleur a observé un état dégradé.",
                badge = "Dégradé",
                explanationTitle = "Protection à vérifier",
                explanation = "L’état dégradé ne permet pas de présenter le tunnel comme pleinement protégé."
            )
            SentinelVpnController.RuntimeState.FAILED -> VpnSurface(
                status = SentinelStatus.BAD,
                title = "Connexion échouée",
                subtitle = "La dernière opération VPN Sentinel n’a pas abouti.",
                badge = "Échec",
                explanationTitle = "Protection indisponible",
                explanation = "Le dernier état connu est un échec ; aucune protection active n’est déduite."
            )
            SentinelVpnController.RuntimeState.DISCONNECTED -> VpnSurface(
                status = SentinelStatus.UNKNOWN,
                title = "Tunnel déconnecté",
                subtitle = "Aucun tunnel Sentinel actif n’est observé.",
                badge = "Déconnecté",
                explanationTitle = "Protection non active",
                explanation = "Cela ne décrit pas les éventuels VPN d’autres applications et ne constitue pas une preuve de sécurité réseau globale."
            )
        }
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
