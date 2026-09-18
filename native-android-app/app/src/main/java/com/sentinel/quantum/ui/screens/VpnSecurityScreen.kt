package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.security.SentinelVpnController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnSecurityScreen(navController: NavController) {
    val context = LocalContext.current
    val controller = remember(context) { SentinelVpnController(context) }
    val runtimeState = controller.currentState()
    val consentRequired = remember { controller.prepareConsentIntent() != null }

    val plannedRegions = remember {
        listOf(
            "France (FR)", "Belgique (BE)", "Allemagne (DE)", "Pays-Bas (NL)",
            "Espagne (ES)", "Suisse (CH)", "Royaume-Uni (GB)", "Canada (CA)",
            "États-Unis (US)", "Japon (JP)", "Singapour (SG)"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VPN défensif Sentinel") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Client WireGuard Android",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Le client WireGuard est intégré. Aucune passerelle Sentinel de sortie n’est actuellement provisionnée et validée ; la connexion reste donc désactivée."
            )

            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FactRow("État du client", runtimeState.name)
                    FactRow("Passerelles vérifiées", "0")
                    FactRow("Consentement Android", if (consentRequired) "Non accordé / requis lors d’une future connexion" else "Déjà accordé")
                    FactRow("Full tunnel IPv4 + IPv6", "Obligatoire")
                    FactRow("DNS", "Doit être épinglé à la passerelle vérifiée")
                }
            }

            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Connexion rapide — aucune passerelle disponible")
            }

            Text(
                "Pays planifiés",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            plannedRegions.forEach { region ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(region)
                        Text("PLANNED", fontWeight = FontWeight.Bold)
                    }
                }
            }

            OutlinedButton(
                onClick = { navController.navigateUp() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Retour")
            }

            Text(
                "Un pays ne deviendra connectable qu’après provisionnement d’une passerelle réelle, vérification IPv4/IPv6, tests de fuite DNS, validation de géolocalisation de l’IP de sortie, reconnexion réseau et rotation/révocation des clés.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
