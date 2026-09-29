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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.security.SentinelCleanupBatch
import com.sentinel.quantum.security.SentinelCleanupPlan
import com.sentinel.quantum.security.SentinelCleanupPolicy
import com.sentinel.quantum.security.SentinelDeviceDiagnostic
import com.sentinel.quantum.security.SentinelOwnedCleanupCollector
import com.sentinel.quantum.security.SentinelOwnedCleanupExecutor
import com.sentinel.quantum.security.SentinelSystemDoctor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemDoctorScreen(navController: NavController) {
    val context = LocalContext.current
    val doctor = remember(context) { SentinelSystemDoctor(context) }
    val cleanupCollector = remember(context) { SentinelOwnedCleanupCollector(context) }
    val cleanupExecutor = remember(context) { SentinelOwnedCleanupExecutor(context) }
    val scope = rememberCoroutineScope()

    var scan by remember { mutableStateOf<SentinelSystemDoctor.Scan?>(null) }
    var cleanupDiscovery by remember {
        mutableStateOf<SentinelOwnedCleanupCollector.Discovery?>(null)
    }
    var cleanupResult by remember {
        mutableStateOf<SentinelCleanupBatch.Summary?>(null)
    }
    var cleanupBusy by remember { mutableStateOf(false) }
    var cleanupMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cleanupCollector) {
        cleanupBusy = true
        val discovered = withContext(Dispatchers.IO) {
            runCatching { cleanupCollector.discoverDetailed() }
        }
        discovered.fold(
            onSuccess = {
                cleanupDiscovery = it
                cleanupMessage = null
            },
            onFailure = {
                cleanupDiscovery = null
                cleanupMessage = "Analyse du cache impossible à vérifier."
            }
        )
        cleanupBusy = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostic système Sentinel") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Analyse locale des signaux accessibles. Une zone non observable reste explicitement inconnue.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = { scan = doctor.scan() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (scan == null) "Lancer le scan local" else "Relancer le scan")
            }

            CleanupCard(
                discovery = cleanupDiscovery,
                result = cleanupResult,
                busy = cleanupBusy,
                message = cleanupMessage,
                onRefresh = {
                    if (cleanupBusy) return@CleanupCard
                    scope.launch {
                        cleanupBusy = true
                        cleanupMessage = null
                        val discovered = withContext(Dispatchers.IO) {
                            runCatching { cleanupCollector.discoverDetailed() }
                        }
                        discovered.fold(
                            onSuccess = { cleanupDiscovery = it },
                            onFailure = {
                                cleanupDiscovery = null
                                cleanupMessage = "Analyse du cache impossible à vérifier."
                            }
                        )
                        cleanupBusy = false
                    }
                },
                onCleanup = {
                    val snapshot = cleanupDiscovery ?: return@CleanupCard
                    if (cleanupBusy) return@CleanupCard
                    scope.launch {
                        cleanupBusy = true
                        cleanupMessage = null
                        val cleaned = withContext(Dispatchers.IO) {
                            runCatching {
                                val executable = snapshot.candidates.filter {
                                    SentinelCleanupPolicy.canExecuteDirectly(it)
                                }
                                val results = executable.map(cleanupExecutor::execute)
                                SentinelCleanupBatch.summarize(results) to
                                    cleanupCollector.discoverDetailed()
                            }
                        }
                        cleaned.fold(
                            onSuccess = { (summary, refreshed) ->
                                cleanupResult = summary
                                cleanupDiscovery = refreshed
                            },
                            onFailure = {
                                cleanupMessage = "Nettoyage interrompu : résultat non vérifiable."
                            }
                        )
                        cleanupBusy = false
                    }
                }
            )

            scan?.let { result ->
                Text(
                    "Risque observé : " + result.report.highestObservedRisk.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (result.report.isObservationComplete) {
                        "Couverture : observations demandées complètes"
                    } else {
                        "Couverture : partielle — certaines zones restent inconnues ou inaccessibles"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
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
private fun CleanupCard(
    discovery: SentinelOwnedCleanupCollector.Discovery?,
    result: SentinelCleanupBatch.Summary?,
    busy: Boolean,
    message: String?,
    onRefresh: () -> Unit,
    onCleanup: () -> Unit
) {
    val plan = discovery?.let { SentinelCleanupPlan.summarize(it.candidates) }
    val canCleanup = discovery?.isComplete == true &&
        plan != null &&
        plan.directlyExecutableCount > 0 &&
        !busy

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Nettoyage local Sentinel", style = MaterialTheme.typography.titleMedium)
            Text(
                "Portée : caches appartenant à Sentinel uniquement. Aucun espace privé d’une autre application n’est parcouru.",
                style = MaterialTheme.typography.bodySmall
            )

            when {
                discovery == null -> {
                    Text(
                        message ?: if (busy) {
                            "Analyse du cache Sentinel en cours…"
                        } else {
                            "Inventaire du cache non disponible."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                plan != null -> {
                    Text(
                        if (discovery.isComplete) {
                            "Couverture : ${discovery.rootCount} racine(s) cache observée(s)."
                        } else {
                            "Couverture partielle : ${discovery.unreadableRootCount}/${discovery.rootCount} racine(s) non vérifiable(s). Le nettoyage automatique reste bloqué."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Candidats : ${plan.candidateCount} · exécutables : ${plan.directlyExecutableCount} · taille connue : ${plan.knownReclaimableBytes} octets" +
                            if (plan.unknownSizeCount > 0) {
                                " · taille inconnue : ${plan.unknownSizeCount}"
                            } else {
                                ""
                            },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            result?.let { summary ->
                Text(
                    "Dernière exécution : ${summary.verifiedRemovedCount}/${summary.requestedCount} suppression(s) vérifiée(s) · " +
                        "${summary.failedCount} échec(s) · ${summary.unresolvedCount} non résolue(s) · " +
                        "${summary.knownVerifiedFreedBytes} octets libérés vérifiés" +
                        if (summary.unknownFreedSizeCount > 0) {
                            " · ${summary.unknownFreedSizeCount} taille(s) libérée(s) non quantifiable(s)"
                        } else {
                            ""
                        },
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = onRefresh,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "Analyse en cours…" else "Actualiser l’inventaire cache")
            }
            Button(
                onClick = onCleanup,
                enabled = canCleanup,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        busy -> "Opération en cours…"
                        discovery?.isComplete != true -> "Nettoyage bloqué : couverture incomplète"
                        plan?.directlyExecutableCount == 0 -> "Aucun fichier vérifié à nettoyer"
                        else -> "Nettoyer ${plan?.directlyExecutableCount ?: 0} fichier(s) Sentinel"
                    }
                )
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
            Text(evidence.status.name, style = MaterialTheme.typography.labelLarge)
            Text(evidence.summary)
            evidence.observedValue?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
