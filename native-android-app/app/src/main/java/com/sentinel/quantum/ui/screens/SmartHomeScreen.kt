package com.sentinel.quantum.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.smarthome.SmartHomeIntegrationRegistry
import com.sentinel.quantum.ui.design.SentinelD1
import com.sentinel.quantum.ui.design.SentinelHero
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartHomeScreen(navController: NavController) {
    val context = LocalContext.current
    val integrations = SmartHomeIntegrationRegistry.integrations
    var handoffStatus by remember { mutableStateOf<String?>(null) }

    fun openSystemSettings(action: String, label: String) {
        runCatching { context.startActivity(Intent(action)) }
            .onSuccess { handoffStatus = null }
            .onFailure { handoffStatus = "Impossible d’ouvrir les réglages $label sur cet appareil." }
    }

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = "Appareils & Maison",
                subtitle = "Inventaire, liaisons & intégrations",
                onBack = { navController.popBackStack() }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SentinelHero(
                    eyebrow = "Smart Home",
                    title = "Cartographier les équipements autorisés",
                    body = "Inventorier les équipements autorisés et afficher leur liaison vérifiée : routeur, bridge, Matter ou Bluetooth.",
                    badges = listOf(
                        "Local" to SentinelD1.Success,
                        "Liaisons vérifiables" to SentinelD1.Cyan
                    )
                )
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = { openSystemSettings(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Router, null); Spacer(Modifier.width(6.dp)); Text("WiFi")
                    }
                    FilledTonalButton(
                        onClick = { openSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Bluetooth, null); Spacer(Modifier.width(6.dp)); Text("Bluetooth")
                    }
                }
            }

            handoffStatus?.let { message ->
                item {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            item {
                SentinelSectionHeader(
                    title = "Intégrations",
                    subtitle = "Une marque affichée ici n’implique pas que tous ses modèles soient pilotables. Sentinel active uniquement les chemins vérifiés."
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
                SentinelSectionHeader(
                    title = "Topologie",
                    subtitle = "Les relations restent non vérifiées tant qu’elles ne sont pas observées ou confirmées."
                )
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
