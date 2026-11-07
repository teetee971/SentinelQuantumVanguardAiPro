package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sentinel.quantum.security.SentinelDeviceDiagnostic
import com.sentinel.quantum.security.SentinelSystemDoctor
import com.sentinel.quantum.ui.design.SentinelD1
import com.sentinel.quantum.ui.design.SentinelGlassCard
import com.sentinel.quantum.ui.design.SentinelHero
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemDoctorScreen(navController: NavController) {
    val context = LocalContext.current
    val doctor = remember { SentinelSystemDoctor(context) }
    var scan by remember { mutableStateOf<SentinelSystemDoctor.Scan?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = "Diagnostic système Sentinel",
                subtitle = "Observation locale · aucune inférence cachée",
                onBack = { navController.popBackStack() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SentinelHero(
                eyebrow = "Diagnostic",
                title = "Observer ce qu’Android expose réellement",
                body = "Analyse locale des signaux accessibles. Une zone non observable reste explicitement inconnue.",
                badges = listOf(
                    "Local" to SentinelD1.Success,
                    "Fail-closed" to SentinelD1.Cyan
                )
            )
            SystemAnalysisDashboard(scan = scan, isScanning = isScanning)
            Button(
                onClick = {
                    if (!isScanning) {
                        isScanning = true
                        scope.launch {
                            scan = withContext(Dispatchers.IO) { doctor.scan() }
                            isScanning = false
                        }
                    }
                },
                enabled = !isScanning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isScanning) "Analyse…" else if (scan == null) "Lancer le scan local" else "Relancer le scan")
            }

            scan?.let { result ->
                SentinelSectionHeader(
                    title = "Résultat local",
                    subtitle = "Signal maximal observé : " + diagnosticStatusLabel(result.report.highestObservedRisk)
                )
                Text(
                    if (result.report.isObservationComplete) {
                        "Couverture : complète pour les observations demandées."
                    } else {
                        "Conclusion globale : non déterminée — couverture partielle, certaines zones restent inconnues ou inaccessibles."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (result.report.isObservationComplete) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(result.evidence, key = { it.id }) { evidence ->
                        EvidenceCard(evidence)
                    }
                }
            }
        }
    }
}

@Composable
private fun EvidenceCard(evidence: SentinelDeviceDiagnostic.Evidence) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                diagnosticStatusLabel(evidence.status),
                style = MaterialTheme.typography.labelLarge
            )
            Text(evidence.summary)
            evidence.observedValue?.let { value ->
                Text(
                    diagnosticObservedValueLabel(value),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SystemAnalysisDashboard(
    scan: SentinelSystemDoctor.Scan?,
    isScanning: Boolean
) {
    val stateLabel = when {
        isScanning -> "Analyse en cours"
        scan == null -> "En attente"
        else -> "Analyse terminée"
    }
    SentinelGlassCard(
        modifier = Modifier.fillMaxWidth().testTag("device-analysis-dashboard")
    ) {
        Text("Analyse de l’appareil", style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)
        Text(
            "Observation locale des signaux exposés par Android · $stateLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeviceMetric("Applications", if (scan == null) "—" else scan.evidence.size.toString(), Icons.Default.Apps, Modifier.weight(1f))
            DeviceMetric("Système", if (scan == null) "—" else diagnosticStatusLabel(scan.report.highestObservedRisk), Icons.Default.Security, Modifier.weight(1f))
            DeviceMetric("Réseau", "Observé", Icons.Default.Wifi, Modifier.weight(1f))
            DeviceMetric("Mémoire", "Non exposée", Icons.Default.Memory, Modifier.weight(1f))
        }
        SentinelSectionHeader(title = "Résumé des détections", trailing = "Preuves locales")
        Text(
            if (scan == null) "Aucun résultat de scan disponible. Lancez l’analyse pour afficher les observations Android."
            else "${scan.evidence.size} observation(s) produite(s) · aucune classification malware déduite.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Analyse IA indisponible : aucun moteur de classification comportementale n’est raccordé à cet écran.",
            style = MaterialTheme.typography.labelSmall,
            color = SentinelD1.Warning
        )
    }
}

@Composable
private fun DeviceMetric(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier
) {
    Column(modifier, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = SentinelD1.Cyan, modifier = Modifier.size(24.dp))
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


private fun diagnosticStatusLabel(
    status: SentinelDeviceDiagnostic.Status
): String = when (status) {
    SentinelDeviceDiagnostic.Status.OK -> "OK"
    SentinelDeviceDiagnostic.Status.WARNING -> "Attention"
    SentinelDeviceDiagnostic.Status.CRITICAL -> "Critique"
    SentinelDeviceDiagnostic.Status.UNKNOWN -> "Inconnu"
    SentinelDeviceDiagnostic.Status.NOT_ACCESSIBLE -> "Non accessible"
}

private fun diagnosticObservedValueLabel(value: String): String = when (value) {
    "OVERLAY" -> "Superposition à l’écran"
    "ACCESSIBILITY_SERVICE" -> "Service d’accessibilité"
    "INSTALL_UNKNOWN_APPS" -> "Installation d’applications inconnues"
    "DEVICE_ADMIN" -> "Administration de l’appareil"
    "VPN" -> "VPN"
    "PROTECTED" -> "Tunnel Sentinel actif"
    "DEGRADED" -> "Tunnel Sentinel dégradé"
    "FAILED" -> "Dernière opération VPN en échec"
    "CONNECTING" -> "Connexion VPN en cours"
    "DISCONNECTED" -> "VPN Sentinel déconnecté"
    "READY_NO_GATEWAY" -> "Aucune passerelle Sentinel disponible"
    "CONSENT_REQUIRED" -> "Consentement VPN Android requis"
    "UNKNOWN" -> "Inconnu"
    else -> value
}
