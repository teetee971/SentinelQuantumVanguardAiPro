package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

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
            TopAppBar(
                title = { Text("VPN Sentinel") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Text("Client VPN WireGuard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Le client Android et ses garde-fous fail-closed sont intégrés. Aucune passerelle Sentinel de sortie n’est actuellement provisionnée et validée.")
                }
            }
            Text("État", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Service VPN public non opérationnel : aucune passerelle Sentinel AVAILABLE validée.")
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("Connexion indisponible")
            }
            Text("Pays VPN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Aucun pays n’est proposé tant qu’un catalogue signé et une passerelle réellement disponible ne permettent pas une sélection vérifiée.")
            Text(
                "Un état « protégé » ne pourra être affiché qu’après consentement VPN Android, provisionnement sécurisé et établissement réel du tunnel WireGuard.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
