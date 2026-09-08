package com.sentinel.quantum.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.security.BluetoothDeviceKind
import com.sentinel.quantum.security.BluetoothRiskEvaluator
import com.sentinel.quantum.security.BluetoothScanner
import com.sentinel.quantum.security.DiscoveredBluetoothDevice
import com.sentinel.quantum.security.DiscoveredWifiNetwork
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.security.NetworkRiskLevel
import com.sentinel.quantum.security.NetworkTrustStore
import com.sentinel.quantum.security.WifiBand
import com.sentinel.quantum.security.WifiRiskEvaluator
import com.sentinel.quantum.security.WifiScanner
import com.sentinel.quantum.security.WifiSecurityType

private enum class SurveillanceTab(val label: String) {
    WIFI("WiFi"),
    BLUETOOTH("Bluetooth")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSurveillanceScreen(navController: NavController) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val logger = remember(context) { LocalLogger(context) }
    val wifiScanner = remember(context) { WifiScanner(context) }
    val bluetoothScanner = remember(context) { BluetoothScanner(context) }
    val trustStore = remember(context) { NetworkTrustStore(context) }

    var selectedTab by rememberSaveable { mutableStateOf(SurveillanceTab.WIFI) }
    var wifiNetworks by remember { mutableStateOf<List<DiscoveredWifiNetwork>>(emptyList()) }
    var bluetoothDevices by remember { mutableStateOf<List<DiscoveredBluetoothDevice>>(emptyList()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var trackerAlerted by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            wifiScanner.release()
            bluetoothScanner.stop()
        }
    }

    fun runWifiScan() {
        isScanning = true
        wifiScanner.scan(
            onResults = { results ->
                wifiNetworks = results
                isScanning = false
                statusMessage = if (results.isEmpty()) "Aucun réseau détecté pour l'instant." else null
                logger.log(
                    LocalLogger.LogLevel.INFO,
                    "NetworkSurveillance",
                    "Scan WiFi local terminé : ${results.size} réseau(x), " +
                        "${results.count { it.assessment.riskLevel == NetworkRiskLevel.HIGH }} à risque élevé."
                )
            },
            onError = { message ->
                isScanning = false
                statusMessage = message
            }
        )
    }

    fun runBluetoothScan() {
        isScanning = true
        trackerAlerted = false
        bluetoothScanner.scan(
            onResults = { results ->
                bluetoothDevices = results
                if (!trackerAlerted && results.any { it.assessment.likelyTracker }) {
                    trackerAlerted = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
            onError = { message ->
                isScanning = false
                statusMessage = message
            },
            onScanFinished = {
                isScanning = false
                statusMessage = if (bluetoothDevices.isEmpty()) "Aucun appareil détecté pour l'instant." else null
                logger.log(
                    LocalLogger.LogLevel.INFO,
                    "NetworkSurveillance",
                    "Scan Bluetooth local terminé : ${bluetoothDevices.size} appareil(s), " +
                        "${bluetoothDevices.count { it.assessment.likelyTracker }} traceur(s) potentiel(s)."
                )
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.any { it }
        permissionDenied = !granted
        if (!granted) {
            statusMessage = "Autorisations refusées : le scan local ne peut pas s'exécuter."
        } else {
            statusMessage = null
            when (selectedTab) {
                SurveillanceTab.WIFI -> runWifiScan()
                SurveillanceTab.BLUETOOTH -> runBluetoothScan()
            }
        }
    }

    fun startScan() {
        statusMessage = null
        when (selectedTab) {
            SurveillanceTab.WIFI ->
                if (wifiScanner.hasPermissions()) {
                    runWifiScan()
                } else {
                    permissionLauncher.launch(wifiScanner.requiredPermissions)
                }
            SurveillanceTab.BLUETOOTH ->
                if (bluetoothScanner.hasPermissions()) {
                    runBluetoothScan()
                } else {
                    permissionLauncher.launch(bluetoothScanner.requiredPermissions)
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Screen.NetworkSurveillance.title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!isScanning) startScan() },
                icon = { Icon(Icons.Default.Radar, contentDescription = null) },
                text = { Text(if (isScanning) "Scan en cours…" else "Scanner") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                SurveillanceTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    EducationalCard(
                        title = "Analyse locale et défensive",
                        bullets = listOf(
                            "Lecture seule : aucune connexion, aucun appairage, aucune modification réseau.",
                            "Aucun résultat n'est envoyé sur Internet ni partagé.",
                            when (selectedTab) {
                                SurveillanceTab.WIFI -> WifiRiskEvaluator.OPEN_NETWORK_ADVICE
                                SurveillanceTab.BLUETOOTH -> BluetoothRiskEvaluator.TRACKER_ADVICE
                            }
                        )
                    )
                }

                statusMessage?.let { message ->
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(message, style = MaterialTheme.typography.bodyMedium)
                                if (permissionDenied) {
                                    OutlinedButton(
                                        onClick = {
                                            val intent = Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.fromParts("package", context.packageName, null)
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(intent)
                                        }
                                    ) {
                                        Text("Ouvrir les paramètres d'autorisations")
                                    }
                                }
                            }
                        }
                    }
                }

                when (selectedTab) {
                    SurveillanceTab.WIFI -> {
                        if (wifiNetworks.isEmpty()) {
                            item { EmptyStateCard("Lancez un scan pour lister les réseaux WiFi environnants.") }
                        } else {
                            items(wifiNetworks, key = { it.bssid + it.ssid }) { network ->
                                WifiNetworkCard(
                                    network = network,
                                    onAllow = {
                                        trustStore.allow(network.bssid)
                                        runWifiScan()
                                    },
                                    onBlock = {
                                        trustStore.block(network.bssid)
                                        runWifiScan()
                                    }
                                )
                            }
                        }
                    }

                    SurveillanceTab.BLUETOOTH -> {
                        if (bluetoothDevices.isEmpty()) {
                            item { EmptyStateCard("Lancez un scan pour lister les appareils Bluetooth environnants.") }
                        } else {
                            items(bluetoothDevices, key = { it.address }) { device ->
                                BluetoothDeviceCard(device)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiNetworkCard(
    network: DiscoveredWifiNetwork,
    onAllow: () -> Unit,
    onBlock: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = network.ssid,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                RiskBadge(network.assessment.riskLevel)
            }
            Text(
                text = "${securityLabel(network.assessment.securityType)} · " +
                    "${bandLabel(network.band)} · ${network.rssiDbm} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (network.bssid.isNotBlank()) {
                Text(
                    text = "BSSID : ${network.bssid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (network.assessment.captivePortalSuspected) {
                Text(
                    text = "⚠️ Réseau sans chiffrement avec indice de portail captif.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            network.assessment.reasons.forEach { reason ->
                Text("• $reason", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAllow, enabled = network.bssid.isNotBlank()) {
                    Text("Réseau de confiance")
                }
                OutlinedButton(onClick = onBlock, enabled = network.bssid.isNotBlank()) {
                    Text("Marquer suspect")
                }
            }
        }
    }
}

@Composable
private fun BluetoothDeviceCard(device: DiscoveredBluetoothDevice) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                RiskBadge(device.assessment.riskLevel)
            }
            Text(
                text = "${device.address} · ${device.rssiDbm} dBm · ${kindLabel(device)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (device.randomizedAddress) {
                Text(
                    text = "Adresse possiblement aléatoire (BLE) : elle peut changer entre deux scans.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            device.assessment.reasons.forEach { reason ->
                Text("• $reason", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RiskBadge(level: NetworkRiskLevel) {
    val (label, container) = when (level) {
        NetworkRiskLevel.HIGH -> "ÉLEVÉ" to MaterialTheme.colorScheme.errorContainer
        NetworkRiskLevel.MEDIUM -> "MOYEN" to MaterialTheme.colorScheme.tertiaryContainer
        NetworkRiskLevel.LOW -> "FAIBLE" to MaterialTheme.colorScheme.secondaryContainer
    }
    Surface(color = container, shape = MaterialTheme.shapes.small) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = contentColorFor(container),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EducationalCard(title: String, bullets: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            bullets.forEach { bullet ->
                Text("• $bullet", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp)
        )
    }
}

private fun securityLabel(type: WifiSecurityType): String = when (type) {
    WifiSecurityType.OPEN -> "Ouvert (non chiffré)"
    WifiSecurityType.WEP -> "WEP"
    WifiSecurityType.WPA -> "WPA"
    WifiSecurityType.WPA2 -> "WPA2"
    WifiSecurityType.WPA2_ENTERPRISE -> "WPA2-Enterprise"
    WifiSecurityType.WPA3 -> "WPA3"
    WifiSecurityType.UNKNOWN -> "Chiffrement inconnu"
}

private fun bandLabel(band: WifiBand): String = when (band) {
    WifiBand.BAND_2_4_GHZ -> "2,4 GHz"
    WifiBand.BAND_5_GHZ -> "5 GHz"
    WifiBand.BAND_6_GHZ -> "6 GHz"
    WifiBand.UNKNOWN -> "Bande inconnue"
}

private fun kindLabel(device: DiscoveredBluetoothDevice): String = when (device.kind) {
    BluetoothDeviceKind.AUDIO -> "Audio/vidéo"
    BluetoothDeviceKind.PHONE -> "Téléphone"
    BluetoothDeviceKind.COMPUTER -> "Ordinateur"
    BluetoothDeviceKind.WEARABLE -> "Objet porté"
    BluetoothDeviceKind.UNKNOWN -> "Type inconnu"
}
