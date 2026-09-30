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
                    "Client prêt" to SentinelD1.Cyan,
                    "Infrastructure bloquée" to SentinelD1.Warning
                )
            )
            SentinelSectionHeader(
                title = "État réel",
                subtitle = "Aucun état protégé ne peut être affiché tant qu’un tunnel vers une passerelle Sentinel validée n’est pas établi."
            )
            SentinelPanel {
                Icon(Icons.Default.Lock, contentDescription = null, tint = SentinelD1.Warning)
                Text(
                    "Service VPN public non opérationnel : aucune passerelle Sentinel AVAILABLE validée.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("Connexion indisponible")
            }
            SentinelSectionHeader(
                title = "Pays VPN",
                subtitle = "Catalogue désactivé tant qu’aucune infrastructure signée et disponible ne peut être prouvée."
            )
            Text("Aucun pays n’est proposé tant qu’un catalogue signé et une passerelle réellement disponible ne permettent pas une sélection vérifiée.")
            Text(
                "Un état « protégé » ne pourra être affiché qu’après consentement VPN Android, provisionnement sécurisé et établissement réel du tunnel WireGuard.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
