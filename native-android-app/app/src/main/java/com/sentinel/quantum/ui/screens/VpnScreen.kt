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
 * Truthful VPN control surface.
 *
 * The UI intentionally does not synthesize countries, gateways or a protected state. Connect
 * controls stay unavailable until a verified signed catalog/provisioning flow supplies an
 * AVAILABLE gateway and configuration to SentinelVpnController.
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
                    Text("VPN défensif WireGuard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Moteur Android intégré. La connexion reste verrouillée tant qu’aucune passerelle Sentinel n’est validée AVAILABLE par le catalogue signé.")
                }
            }
            Text("État", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Aucune passerelle de sortie validée disponible dans l’application.")
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("Connexion indisponible")
            }
            Text("Pays VPN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Les pays seront proposés depuis le catalogue signé. Sentinel n’affiche pas de pays fictif et n’annonce jamais « connecté » sans tunnel WireGuard réellement établi.")
            Text(
                "Pré-requis : consentement VPN Android, passerelle AVAILABLE, configuration provisionnée, routes IPv4/IPv6 complètes et DNS épinglé à la passerelle.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
