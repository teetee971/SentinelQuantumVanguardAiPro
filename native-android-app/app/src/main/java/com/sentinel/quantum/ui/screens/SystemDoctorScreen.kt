package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
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
import com.sentinel.quantum.security.SentinelDeviceUtilitiesOrchestrator
import com.sentinel.quantum.security.SentinelSystemDoctor
import com.sentinel.quantum.ui.design.SentinelCoreModule
import com.sentinel.quantum.ui.design.MotionLevel
import com.sentinel.quantum.ui.design.SentinelHolographicScene
import com.sentinel.quantum.ui.design.SentinelHolographicSceneModel
import com.sentinel.quantum.ui.design.SentinelSceneKind
import com.sentinel.quantum.ui.design.SentinelReferenceMetric
import com.sentinel.quantum.ui.design.SentinelReferenceMetricStrip
import com.sentinel.quantum.ui.design.SentinelStatus
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelTopBar
import com.sentinel.quantum.ui.design.visual

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemDoctorScreen(navController: NavController) {
    val context = LocalContext.current
    val doctor = remember { SentinelSystemDoctor(context) }
    val deviceUtilities = remember { SentinelDeviceUtilitiesOrchestrator(context) }
    var scan by remember { mutableStateOf<SentinelSystemDoctor.Scan?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var cleanupDiscovery by remember {
        mutableStateOf<SentinelDeviceUtilitiesOrchestrator.Discovery?>(null)
    }
    var cleanupRun by remember {
        mutableStateOf<SentinelDeviceUtilitiesOrchestrator.CleanupRun?>(null)
    }
    var isCleanupBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val doctorStatus = scan?.let { result ->
        when (result.report.highestObservedRisk) {
            SentinelDeviceDiagnostic.Status.CRITICAL -> SentinelStatus.BAD
            SentinelDeviceDiagnostic.Status.WARNING -> SentinelStatus.WARN
            SentinelDeviceDiagnostic.Status.OK -> SentinelStatus.OK
            SentinelDeviceDiagnostic.Status.UNKNOWN,
            SentinelDeviceDiagnostic.Status.NOT_ACCESSIBLE -> SentinelStatus.UNKNOWN
        }
    } ?: SentinelStatus.UNKNOWN
    // The storage metric must use the actual storage evidence collected by the
    // system scan, not a separate cache-cleanup discovery action.
    val storageEvidence = scan?.evidence?.firstOrNull { it.id == "storage.primary_free" }
    val storageStatus = when (storageEvidence?.status) {
        SentinelDeviceDiagnostic.Status.OK -> SentinelStatus.OK
        SentinelDeviceDiagnostic.Status.WARNING -> SentinelStatus.WARN
        SentinelDeviceDiagnostic.Status.CRITICAL -> SentinelStatus.BAD
        SentinelDeviceDiagnostic.Status.UNKNOWN,
        SentinelDeviceDiagnostic.Status.NOT_ACCESSIBLE,
        null -> SentinelStatus.UNKNOWN
    }
    val doctorModules = listOf(
        SentinelCoreModule(
            id = "permissions",
            label = "Permissions · ${if (scan == null) "INCONNU" else "OBSERVÉ"}",
            status = doctorStatus,
            icon = Icons.Default.Key,
            enabled = false
        ),
        SentinelCoreModule(
            id = "roles",
            label = "Rôles Android · INCONNU",
            status = SentinelStatus.UNKNOWN,
            icon = Icons.Default.Security,
            enabled = false
        ),
        SentinelCoreModule(
            id = "storage",
            label = if (storageEvidence == null) "Stockage · INCONNU" else "Stockage · ${storageStatus.visual().label}",
            status = storageStatus,
            icon = Icons.Default.Storage,
            enabled = false
        ),
        SentinelCoreModule(
            id = "battery",
            label = "Batterie · INCONNU",
            status = SentinelStatus.UNKNOWN,
            icon = Icons.Default.BatteryChargingFull,
            enabled = false
        )
    )

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

            SentinelHolographicScene(
                model = SentinelHolographicSceneModel(
                    kind = SentinelSceneKind.DEVICE_ANALYSIS,
                    status = doctorStatus,
                    title = "ANALYSE DE L’APPAREIL",
                    evidenceLabel = "Diagnostic système",
                    evidenceSource = "signaux Android accessibles",
                    progress = if (scan == null) null else 1f,
                    nodes = doctorModules.map {
                        com.sentinel.quantum.ui.design.SentinelHolographicSceneNode(
                            label = it.label,
                            status = it.status
                        )
                    }
                ),
                motionLevel = MotionLevel.STATIC,
                modifier = Modifier.testTag("system-doctor-command-center")
            )
            SentinelReferenceMetricStrip(
                metrics = listOf(
                    SentinelReferenceMetric(
                        label = "Permissions",
                        value = doctorModules[0].status.visual().label,
                        detail = "diagnostic Android",
                        status = doctorModules[0].status
                    ),
                    SentinelReferenceMetric(
                        label = "Rôles",
                        value = "INCONNU",
                        detail = "gérés par Android",
                        status = SentinelStatus.UNKNOWN
                    ),
                    SentinelReferenceMetric(
                        label = "Stockage",
                        value = doctorModules[2].status.visual().label,
                        detail = "espace de stockage Android mesuré",
                        status = doctorModules[2].status
                    ),
                    SentinelReferenceMetric(
                        label = "Batterie",
                        value = "INCONNU",
                        detail = "aucune inférence",
                        status = SentinelStatus.UNKNOWN
                    )
                ),
                modifier = Modifier.testTag("system_doctor_reference_metrics")
            )
            SentinelSectionHeader(
                title = "Utilitaires de stockage",
                subtitle = "Uniquement les caches appartenant à Sentinel · suppression vérifiée"
            )
            Button(
                onClick = {
                    if (!isCleanupBusy) {
                        isCleanupBusy = true
                        cleanupRun = null
                        scope.launch {
                            cleanupDiscovery = withContext(Dispatchers.IO) { deviceUtilities.discover() }
                            isCleanupBusy = false
                        }
                    }
                },
                enabled = !isCleanupBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isCleanupBusy) "Analyse des caches…" else "Analyser les caches Sentinel")
            }

            cleanupDiscovery?.let { discovery ->
                Text(
                    "${discovery.plan.candidateCount} fichier(s) candidat(s) · " +
                        "${formatBytes(discovery.plan.knownReclaimableBytes)} connus récupérables" +
                        if (discovery.plan.unknownSizeCount > 0) {
                            " · ${discovery.plan.unknownSizeCount} taille(s) inconnue(s)"
                        } else {
                            ""
                        },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (discovery.plan.directlyExecutableCount > 0) {
                    Button(
                        onClick = {
                            if (!isCleanupBusy) {
                                isCleanupBusy = true
                                scope.launch {
                                    cleanupRun = withContext(Dispatchers.IO) {
                                        deviceUtilities.cleanup(discovery)
                                    }
                                    cleanupDiscovery = withContext(Dispatchers.IO) {
                                        deviceUtilities.discover()
                                    }
                                    isCleanupBusy = false
                                }
                            }
                        },
                        enabled = !isCleanupBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Nettoyer les fichiers Sentinel vérifiables")
                    }
                }
            }

            cleanupRun?.let { run ->
                val summary = run.summary
                Text(
                    if (summary.isFullyVerified) {
                        "Nettoyage vérifié : ${summary.verifiedRemovedCount}/${summary.requestedCount} · " +
                            "${formatBytes(summary.knownVerifiedFreedBytes)} libérés"
                    } else {
                        "Nettoyage partiel : ${summary.verifiedRemovedCount}/${summary.requestedCount} vérifiés · " +
                            "${summary.unresolvedCount} non résolu(s) · ${summary.failedCount} échec(s)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (summary.isFullyVerified) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
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

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f Go".format(bytes.toDouble() / (1024L * 1024L * 1024L))
    bytes >= 1024L * 1024L -> "%.2f Mo".format(bytes.toDouble() / (1024L * 1024L))
    bytes >= 1024L -> "%.1f Ko".format(bytes.toDouble() / 1024L)
    else -> "$bytes o"
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
