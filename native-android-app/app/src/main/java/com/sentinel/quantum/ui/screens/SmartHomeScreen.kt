package com.sentinel.quantum.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Radar
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
import com.sentinel.quantum.navigation.Screen
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
    var showCompatibilityCatalog by remember { mutableStateOf(false) }

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
                    title = "Voir les appareils réellement observés",
                    body = "Sentinel ne fabrique pas de topologie. Commencez par un scan local Wi-Fi/Bluetooth ; une relation n’est dite vérifiée qu’après observation ou confirmation explicite.",
                    badges = listOf(
                        "Local" to SentinelD1.Success,
                        "Zéro topologie supposée" to SentinelD1.Cyan
                    )
                )
            }

            item {
                Button(
                    onClick = { navController.navigate(Screen.NetworkSurveillance.route) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Radar, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scanner les appareils à proximité")
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = { openSystemSettings(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Router, null); Spacer(Modifier.width(6.dp)); Text("Wi-Fi")
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
                    title = "Compatibilités prévues",
                    subtitle = "Catalogue technique, pas inventaire de votre maison. Une marque listée n’est pas une preuve qu’un appareil correspondant est présent ou pilotable."
                )
                OutlinedButton(
                    onClick = { showCompatibilityCatalog = !showCompatibilityCatalog },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (showCompatibilityCatalog) {
                            "Masquer le catalogue"
                        } else {
                            "Voir le catalogue de compatibilité (${integrations.size})"
                        }
                    )
                }
            }

            if (showCompatibilityCatalog) {
                items(integrations, key = { it.brand.name }) { integration ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(integration.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Découverte prévue : " + integration.discoveryMode)
                            Text("Contrôle prévu : " + integration.controlMode, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Transports possibles : " + integration.supportedTransports.joinToString { it.name.replace('_', ' ') },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
                SentinelSectionHeader(
                    title = "Topologie observée",
                    subtitle = "Aucune relation ne doit être déduite d’un simple catalogue de compatibilité."
                )
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Aucune topologie vérifiée dans cet écran", fontWeight = FontWeight.Bold)
                        Text(
                            "Le scanner Réseau & appareils proches affiche les réseaux et appareils réellement visibles. Une relation routeur → appareil, bridge → équipement ou téléphone → accessoire ne sera ajoutée ici qu’avec une preuve observable ou une confirmation explicite.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(
                            onClick = { navController.navigate(Screen.NetworkSurveillance.route) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Ouvrir le scanner local")
                        }
                    }
                }
            }
        }
    }
}
