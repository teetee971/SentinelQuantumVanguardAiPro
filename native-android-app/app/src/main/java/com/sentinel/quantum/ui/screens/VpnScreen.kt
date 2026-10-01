package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.ui.design.SentinelD1
import com.sentinel.quantum.ui.design.SentinelHero
import com.sentinel.quantum.ui.design.SentinelPanel
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelTopBar

/**
 * Truthful VPN status surface.
 *
 * Sentinel contains a fail-closed WireGuard Android client foundation, but no validated Sentinel
 * exit gateway is currently provisioned. This screen therefore exposes no synthetic country,
 * connect action or protected state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnScreen(navController: NavController) {
    Scaffold(
        topBar = {
            SentinelTopBar(
                title = "VPN Sentinel",
                subtitle = "WireGuard · état fail-closed",
                onBack = { navController.popBackStack() }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SentinelHero(
                eyebrow = "VPN défensif",
                title = "Client WireGuard intégré",
                body = "Le client Android et ses garde-fous fail-closed sont intégrés. Aucune passerelle Sentinel de sortie n’est actuellement provisionnée et validée.",
                badges = listOf(
                    "Client intégré" to SentinelD1.Cyan,
                    "Passerelle absente" to SentinelD1.Warning
                )
            )
            SentinelSectionHeader(
                title = "État réel",
                subtitle = "Aucun état protégé ne peut être affiché tant qu’un tunnel vers une passerelle Sentinel validée n’est pas établi."
            )
            SentinelPanel {
                Icon(Icons.Default.Lock, contentDescription = null, tint = SentinelD1.Warning)
                Text(
                    "Service VPN public non opérationnel : aucune passerelle Sentinel disponible et validée.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Connexion indisponible tant qu’aucune passerelle Sentinel validée n’est provisionnée.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            SentinelSectionHeader(
                title = "Ce qu’il manque",
                subtitle = "Une action de connexion ne sera affichée que lorsque toute la chaîne sera réellement vérifiable."
            )
            Text("• Une passerelle Sentinel provisionnée, signée et joignable.")
            Text("• Un catalogue de régions vérifié — aucun pays fictif n’est proposé.")
            Text("• Le consentement VPN Android de l’utilisateur.")
            Text("• Un handshake WireGuard réellement établi avant d’afficher « protégé ».")
            Text(
                "L’intégration du client ne constitue pas à elle seule un service VPN opérationnel.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
