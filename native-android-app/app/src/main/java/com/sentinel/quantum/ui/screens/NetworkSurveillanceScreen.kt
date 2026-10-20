package com.sentinel.quantum.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.security.AndroidDefaultNetworkEvidence
import com.sentinel.quantum.security.AndroidLinkSummary
import com.sentinel.quantum.security.LocalLinkSummary
import com.sentinel.quantum.security.LocalNetworkObservation
import com.sentinel.quantum.security.NetworkEvidenceSource
import com.sentinel.quantum.security.BluetoothDeviceKind
import com.sentinel.quantum.security.BluetoothRiskEvaluator
import com.sentinel.quantum.security.BluetoothScanner
import com.sentinel.quantum.security.CovertDetectionSupport
import com.sentinel.quantum.security.CovertDeviceDetectionCapabilities
import com.sentinel.quantum.security.DiscoveredBluetoothDevice
import com.sentinel.quantum.security.DiscoveredWifiNetwork
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.security.NetworkRiskLevel
import com.sentinel.quantum.security.NetworkTrustStore
import com.sentinel.quantum.security.WifiBand
import com.sentinel.quantum.security.WifiRiskEvaluator
import com.sentinel.quantum.security.WifiScanResultTruth
import com.sentinel.quantum.security.WifiScanner
import com.sentinel.quantum.security.WifiSecurityType
import com.sentinel.quantum.ui.design.SentinelGlassCard
import com.sentinel.quantum.ui.design.SentinelHeroVisual
import com.sentinel.quantum.ui.design.SentinelMotionPolicy
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelStatus
import com.sentinel.quantum.ui.design.SentinelStatusChip
import com.sentinel.quantum.ui.design.SentinelTopBar

private enum class SurveillanceTab(val label: String) {
    WIFI("Wi-Fi"),
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
    val motionLevel = remember(context) {
        val animatorScale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
        val powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        SentinelMotionPolicy.resolve(animatorScale, powerSave)
    }

    var selectedTab by rememberSaveable { mutableStateOf(SurveillanceTab.WIFI) }
    var wifiNetworks by remember { mutableStateOf<List<DiscoveredWifiNetwork>>(emptyList()) }
    var bluetoothDevices by remember { mutableStateOf<List<DiscoveredBluetoothDevice>>(emptyList()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var defaultTransportObservation by remember { mutableStateOf<LocalNetworkObservation?>(null) }
    var localLinkSummary by remember { mutableStateOf<LocalLinkSummary?>(null) }
    var transportClockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(defaultTransportObservation, localLinkSummary) {
        transportClockMillis = System.currentTimeMillis()
        val expiry = listOfNotNull(defaultTransportObservation?.expiresAtMillis, localLinkSummary?.observedAtMillis?.plus(30_000L)).minOrNull() ?: return@LaunchedEffect
        val remainingMillis = expiry - transportClockMillis
        if (remainingMillis > 0L) {
            delay(remainingMillis)
            transportClockMillis = System.currentTimeMillis()
        }
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var covertDeviceAlerted by remember { mutableStateOf(false) }

    val visibleRiskLevels = when (selectedTab) {
        SurveillanceTab.WIFI -> wifiNetworks.map { it.assessment.riskLevel }
        SurveillanceTab.BLUETOOTH -> bluetoothDevices.map { it.assessment.riskLevel }
    }
    val aggregateStatus = NetworkVpnPresentation.aggregateNetworkStatus(visibleRiskLevels)
    val hasEvidence = visibleRiskLevels.isNotEmpty()
    val heroSubtitle = when {
        isScanning -> "Analyse locale en cours. Sentinel attend des observations Android avant d’établir un état."
        !hasEvidence -> "Aucune preuve de scan disponible. Lancez une analyse locale pour observer ce qu’Android rend réellement visible."
        aggregateStatus == SentinelStatus.BAD -> "Au moins un signal à risque élevé a été observé localement. Vérifiez les détails avant toute action."
        aggregateStatus == SentinelStatus.WARN -> "Des signaux intermédiaires nécessitent une vérification. Ils ne constituent pas à eux seuls une preuve de compromission."
        else -> "Aucun signal moyen ou élevé n’est présent dans les résultats visibles. Cela ne prouve pas que l’environnement est sûr."
    }

    DisposableEffect(Unit) {
        onDispose {
            wifiScanner.release()
            bluetoothScanner.stop()
        }
    }

    fun openWifiConnectionPanel() {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Settings.Panel.ACTION_WIFI
        } else {
            Settings.ACTION_WIFI_SETTINGS
        }
        runCatching { context.startActivity(Intent(action)) }
            .onFailure { statusMessage = "Impossible d’ouvrir les réglages Wi-Fi sur cet appareil." }
    }

    fun openBluetoothConnectionPanel() {
        runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            .onFailure { statusMessage = "Impossible d’ouvrir les réglages Bluetooth sur cet appareil." }
    }

    fun runWifiScan() {
        if (!wifiScanner.isWifiEnabled()) {
            isScanning = false
            statusMessage = "Le Wi-Fi est désactivé. Activez-le puis relancez le scan."
            return
        }
        isScanning = true
        wifiScanner.scan(
            onResults = { outcome ->
                val results = outcome.networks
                wifiNetworks = results
                isScanning = false
                statusMessage = WifiScanResultTruth.statusMessage(outcome.source, results.size)
                logger.log(
                    LocalLogger.LogLevel.INFO,
                    "NetworkSurveillance",
                    "Scan Wi-Fi local terminé : source=" + outcome.source.name +
                        ", " + results.size + " réseau(x), " +
                        results.count { it.assessment.riskLevel == NetworkRiskLevel.HIGH } +
                        " à risque élevé."
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
        covertDeviceAlerted = false
        bluetoothScanner.scan(
            onResults = { results ->
                bluetoothDevices = results
                if (!covertDeviceAlerted && results.any { it.assessment.likelyTracker || it.assessment.likelyCameraOrRecorder || it.assessment.likelyBeacon }) {
                    covertDeviceAlerted = true
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
    ) { _ ->
        val granted = when (selectedTab) {
            SurveillanceTab.WIFI -> wifiScanner.hasPermissions()
            SurveillanceTab.BLUETOOTH -> bluetoothScanner.hasPermissions()
        }
        permissionDenied = !granted
        if (!granted) {
            isScanning = false
            statusMessage = "Autorisation obligatoire manquante : le scan local ne peut pas s'exécuter."
        } else {
            statusMessage = null
            when (selectedTab) {
                SurveillanceTab.WIFI -> runWifiScan()
                SurveillanceTab.BLUETOOTH -> runBluetoothScan()
            }
        }
    }

    fun startScan() {
        defaultTransportObservation = AndroidDefaultNetworkEvidence.read(context)
        localLinkSummary = AndroidLinkSummary.read(context)
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
            SentinelTopBar(
                title = stringResource(Screen.NetworkSurveillance.titleRes),
                subtitle = "Wi-Fi, Bluetooth & détection locale",
                onBack = { navController.popBackStack() }
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
                    SentinelHeroVisual(
                        status = aggregateStatus,
                        title = if (selectedTab == SurveillanceTab.WIFI) "Observation Wi-Fi" else "Observation Bluetooth",
                        subtitle = heroSubtitle,
                        modifier = Modifier.testTag("network-command-center"),
                        motionLevel = motionLevel
                    )
                }

                item {
                    SentinelGlassCard(modifier = Modifier.fillMaxWidth().testTag("network-default-transport")) {
                        Text("Transport réseau actif", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val observation = defaultTransportObservation
                        val active = observation?.takeIf { it.isFresh(transportClockMillis) }
                        val sourceLabel = when (active?.source) {
                            NetworkEvidenceSource.ANDROID_WIFI -> "Wi-Fi"
                            NetworkEvidenceSource.ANDROID_CELLULAR -> "Données mobiles"
                            NetworkEvidenceSource.ANDROID_ETHERNET -> "Ethernet"
                            NetworkEvidenceSource.VPN -> "VPN (transport sous-jacent non déterminé)"
                            NetworkEvidenceSource.ANDROID_OTHER -> "Autre transport"
                            else -> "Non vérifié"
                        }
                        Text("Observation locale : $sourceLabel")
                        Text(
                            "Lecture ponctuelle lors du scan, non assimilable à une découverte d'appareils ou à un état de sécurité.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    SentinelGlassCard(modifier = Modifier.fillMaxWidth().testTag("network-link-summary")) {
                        Text("DNS et passerelle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val link = localLinkSummary?.takeIf { it.isFresh(transportClockMillis) }
                        if (link == null) {
                            Text("Non vérifié — lecture réseau indisponible ou expirée.")
                        } else {
                            Text("Serveurs DNS renseignés : " + if (link.dnsServersPresent) "Oui" else "Non observés")
                            Text("Route avec passerelle par défaut : " + if (link.defaultGatewayPresent) "Oui" else "Non observée")
                            Text("DNS privé actif : " + if (link.privateDnsActive) "Oui" else "Non")
                        }
                        Text("Informations locales Android uniquement. Une absence ne prouve pas une attaque.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                item {
                    SentinelGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("network-local-evidence")
                    ) {
                        SentinelStatusChip(status = aggregateStatus)
                        Text(
                            text = if (hasEvidence) {
                                "${visibleRiskLevels.size} résultat(s) actuellement visibles pour ce canal."
                            } else {
                                "Aucun résultat local disponible pour ce canal."
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "L’absence d’alerte ou un signal classé LOW reste non concluant : Sentinel ne transforme jamais cela en preuve de sûreté.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    SentinelSectionHeader(
                        title = if (selectedTab == SurveillanceTab.WIFI) "Wi-Fi à proximité" else "Appareils Bluetooth",
                        subtitle = "Scannez d’abord. Sentinel affiche ensuite uniquement ce qu’Android rend réellement observable."
                    )
                }

                item {
                    FilledTonalButton(
                        onClick = {
                            when (selectedTab) {
                                SurveillanceTab.WIFI -> openWifiConnectionPanel()
                                SurveillanceTab.BLUETOOTH -> openBluetoothConnectionPanel()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            if (selectedTab == SurveillanceTab.WIFI) Icons.Default.Wifi else Icons.Default.Bluetooth,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (selectedTab == SurveillanceTab.WIFI) "Choisir un réseau Wi-Fi"
                            else "Connecter / appairer un appareil Bluetooth"
                        )
                    }
                }

                statusMessage?.let { message ->
                    item {
                        SentinelGlassCard(modifier = Modifier.fillMaxWidth()) {
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

                when (selectedTab) {
                    SurveillanceTab.WIFI -> {
                        if (wifiNetworks.isEmpty()) {
                            item { EmptyStateCard("Lancez un scan pour lister les réseaux Wi-Fi environnants.") }
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

                item {
                    SentinelSectionHeader(
                        title = "Comprendre les résultats",
                        subtitle = "Limites Android et capacités de détection, sans surévaluer un signal."
                    )
                }
                item {
                    EducationalCard(
                        title = "Scanner et se connecter",
                        bullets = listOf(
                            "Le scan Sentinel analyse localement les réseaux et appareils visibles et signale les indices compatibles avec des traceurs, balises, caméras ou enregistreurs cachés.",
                            "La connexion ou l’appairage est confirmé dans le panneau sécurisé Android : Sentinel ne contourne pas les protections du système.",
                            when (selectedTab) {
                                SurveillanceTab.WIFI -> WifiRiskEvaluator.OPEN_NETWORK_ADVICE
                                SurveillanceTab.BLUETOOTH -> BluetoothRiskEvaluator.TRACKER_ADVICE
                            }
                        )
                    )
                }
                item {
                    EducationalCard(
                        title = "Détection d’appareils cachés",
                        bullets = CovertDeviceDetectionCapabilities.channels.map { capability ->
                            val status = when (capability.support) {
                                CovertDetectionSupport.ACTIVE_LOCAL -> "Actif localement"
                                CovertDetectionSupport.ACTIVE_HEURISTIC -> "Actif · heuristique"
                                CovertDetectionSupport.USER_CAMERA_REQUIRED -> "Caméra requise · module à activer"
                                CovertDetectionSupport.EXTERNAL_RF_HARDWARE_REQUIRED -> "Matériel RF externe requis"
                            }
                            "${capability.label} — $status. ${capability.limitation}"
                        }
                    )
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
    SentinelGlassCard(modifier = Modifier.fillMaxWidth()) {
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
            RiskBadge(network.assessment.riskLevel, "Risque")
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
        if (network.assessment.likelyCameraOrRecorder) {
            Text(
                text = "Appareil vidéo/enregistreur Wi-Fi potentiel — vérification physique recommandée.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (network.assessment.captivePortalSuspected) {
            Text(
                text = "Réseau sans chiffrement avec indice de portail captif.",
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

@Composable
private fun BluetoothDeviceCard(device: DiscoveredBluetoothDevice) {
    SentinelGlassCard(modifier = Modifier.fillMaxWidth()) {
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
            RiskBadge(device.assessment.riskLevel, "Risque")
        }
        Text(
            text = "${device.address} · ${device.rssiDbm} dBm · ${kindLabel(device)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (device.assessment.likelyTracker) {
            Text("Traceur d’objet potentiel détecté.", style = MaterialTheme.typography.bodySmall)
        }
        if (device.assessment.likelyCameraOrRecorder) {
            Text("Caméra ou enregistreur sans fil potentiel.", style = MaterialTheme.typography.bodySmall)
        }
        if (device.assessment.likelyBeacon) {
            Text("Balise BLE potentielle.", style = MaterialTheme.typography.bodySmall)
        }
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

@Composable
private fun RiskBadge(level: NetworkRiskLevel, prefix: String = "Risque") {
    val label = when (level) {
        NetworkRiskLevel.HIGH -> "$prefix : élevé"
        NetworkRiskLevel.MEDIUM -> "$prefix : moyen"
        NetworkRiskLevel.LOW -> "$prefix : non concluant"
    }
    SentinelStatusChip(
        status = NetworkVpnPresentation.networkStatus(level),
        label = label
    )
}

@Composable
private fun EducationalCard(title: String, bullets: List<String>) {
    SentinelGlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        bullets.forEach { bullet ->
            Text("• $bullet", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    SentinelGlassCard(modifier = Modifier.fillMaxWidth()) {
        SentinelStatusChip(status = SentinelStatus.UNKNOWN)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
