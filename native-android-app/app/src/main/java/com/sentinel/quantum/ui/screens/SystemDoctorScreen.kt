package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.security.SentinelDeviceDiagnostic
import com.sentinel.quantum.security.SentinelSystemDoctor
import com.sentinel.quantum.ui.design.SentinelD1
import com.sentinel.quantum.ui.design.SentinelHero
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                Text(
                    if (isScanning) {
                        "Analyse…"
                    } else if (scan == null) {
                        "Lancer le scan local"
                    } else {
                        "Relancer le scan"
                    }
                )
            }

            scan?.let { result ->
                val malwareEvidence = result.evidence.filter { evidence ->
                    evidence.id.startsWith(MALWARE_EVIDENCE_PREFIX)
                }
                val malwareReport = SentinelDeviceDiagnostic.Report(malwareEvidence)

                SentinelSectionHeader(
                    title = "Résultat local",
                    subtitle = "Signal maximal observé : " +
                        diagnosticStatusLabel(result.report.highestObservedRisk)
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

                MalwareSummaryCard(malwareReport)

                LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (malwareEvidence.isNotEmpty()) {
                        item(key = "malware_header") {
                            SentinelSectionHeader(
                                title = "Preuves antimalware",
                                subtitle = "Heuristiques, réputation et Play Protect sont distingués."
                            )
                        }
                        items(
                            malwareEvidence.sortedByDescending { it.status.priority },
                            key = { it.id }
                        ) { evidence ->
                            EvidenceCard(evidence)
                        }
                    }

                    val otherEvidence = result.evidence.filterNot { evidence ->
                        evidence.id.startsWith(MALWARE_EVIDENCE_PREFIX)
                    }
                    if (otherEvidence.isNotEmpty()) {
                        item(key = "system_header") {
                            SentinelSectionHeader(
                                title = "Autres contrôles système",
                                subtitle = "Système, stockage, réseau, capacités et VPN."
                            )
                        }
                        items(otherEvidence, key = { it.id }) { evidence ->
                            EvidenceCard(evidence)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MalwareSummaryCard(report: SentinelDeviceDiagnostic.Report) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Protection antimalware", style = MaterialTheme.typography.titleMedium)
            Text(
                malwareSummary(report),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Critiques : ${report.criticalCount} · Suspects : ${report.warningCount} · Non résolus : ${report.unresolvedCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun malwareSummary(report: SentinelDeviceDiagnostic.Report): String =
    when {
        report.criticalCount > 0 ->
            "Au moins un indicateur critique est confirmé. Consultez les preuves avant toute remédiation."
        report.warningCount > 0 ->
            "Des signaux suspects ont été observés. Ils nécessitent une vérification et ne prouvent pas seuls une infection."
        !report.isObservationComplete ->
            "Aucune menace critique confirmée, mais la couverture antimalware est incomplète. L’appareil ne peut pas être déclaré sain."
        else ->
            "Aucun indicateur malveillant n’a été détecté dans le périmètre effectivement couvert."
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

private val SentinelDeviceDiagnostic.Status.priority: Int
    get() = when (this) {
        SentinelDeviceDiagnostic.Status.CRITICAL -> 5
        SentinelDeviceDiagnostic.Status.WARNING -> 4
        SentinelDeviceDiagnostic.Status.UNKNOWN -> 3
        SentinelDeviceDiagnostic.Status.NOT_ACCESSIBLE -> 2
        SentinelDeviceDiagnostic.Status.OK -> 1
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

private const val MALWARE_EVIDENCE_PREFIX = "sentinel.malware."
