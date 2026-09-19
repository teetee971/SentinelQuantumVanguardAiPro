package com.sentinel.quantum.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.smarthome.SmartHomeIntegrationRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartHomeScreen(navController: NavController) {
    val context = LocalContext.current
    val integrations = SmartHomeIntegrationRegistry.integrations

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appareils & Maison") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Home, null)
                            Text("Sentinel Smart Home", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Text("Inventorier les équipements autorisés et afficher leur liaison vérifiée : routeur, bridge, Matter ou Bluetooth.")
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Router, null); Spacer(Modifier.width(6.dp)); Text("WiFi")
                    }
                    FilledTonalButton(
                        onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Bluetooth, null); Spacer(Modifier.width(6.dp)); Text("Bluetooth")
                    }
                }
            }

            item {
                Text("Intégrations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Une marque affichée ici n’implique pas encore que tous ses modèles soient pilotables. Sentinel active uniquement les chemins vérifiés.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(integrations, key = { it.brand.name }) { integration ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(integration.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Découverte : " + integration.discoveryMode)
                        Text("Contrôle : " + integration.controlMode, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Transports : " + integration.supportedTransports.joinToString { it.name.replace('_', ' ') },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                HorizontalDivider()
                Text("Topologie", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Téléphone Sentinel", fontWeight = FontWeight.Bold)
                        Text("└─ Routeur / point d’accès WiFi")
                        Text("   ├─ appareils WiFi")
                        Text("   ├─ bridge Philips Hue → ampoules / capteurs")
                        Text("   └─ Matter → équipements compatibles")
                        Text("└─ Bluetooth → montre / écouteurs / capteurs")
                        Text(
                            "Les relations seront marquées « vérifiées » uniquement lorsqu’elles sont observables ou confirmées.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
