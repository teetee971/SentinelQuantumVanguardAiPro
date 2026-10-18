package com.sentinel.quantum.ui.screens

import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.security.InternationalNumberResolver
import com.sentinel.quantum.telecom.ConnectorStatus
import com.sentinel.quantum.telecom.GlobalTelecomIntelligenceRepository
import com.sentinel.quantum.telecom.OkHttpOfficialSourceTransport
import com.sentinel.quantum.telecom.TelecomObservation
import com.sentinel.quantum.telecom.TelecomSyncOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sentinel.quantum.ui.design.MotionLevel
import com.sentinel.quantum.ui.design.SentinelD1
import com.sentinel.quantum.ui.design.SentinelGlassCard
import com.sentinel.quantum.ui.design.SentinelHolographicScene
import com.sentinel.quantum.ui.design.SentinelHolographicSceneModel
import com.sentinel.quantum.ui.design.SentinelReferenceMetric
import com.sentinel.quantum.ui.design.SentinelReferenceMetricStrip
import com.sentinel.quantum.ui.design.SentinelSceneKind
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelStatus
import com.sentinel.quantum.ui.design.SentinelStatusChip
import com.sentinel.quantum.ui.design.SentinelTopBar
import com.sentinel.quantum.ui.design.SentinelVisualTokens

@Composable
fun GlobalTelecomIntelligenceScreen(navController: NavController) {
    val context = LocalContext.current
    val motionLevel = remember(context) {
        val animatorScale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        val powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        com.sentinel.quantum.ui.design.SentinelMotionPolicy.resolve(animatorScale, powerSave)
    }
    val repository = remember { GlobalTelecomIntelligenceRepository() }
    val resolver = remember { InternationalNumberResolver() }
    var refreshToken by remember { mutableStateOf(0) }
    val dashboard = remember(refreshToken) { repository.dashboard(nowMs = System.currentTimeMillis()) }
    val sourceViews = remember(refreshToken) { repository.sources(nowMs = System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()
    var probeInProgress by remember { mutableStateOf(false) }
    var probeMessage by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val resolution = remember(query) {
        query.trim().takeIf { it.isNotEmpty() }?.let(resolver::resolve)
    }

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = "Intelligence téléphonique mondiale",
                subtitle = "Sources officielles · annotation non-enforçante",
                onBack = { navController.popBackStack() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SentinelHolographicScene(
                model = SentinelHolographicSceneModel(
                    kind = SentinelSceneKind.THREAT_GLOBE,
                    status = SentinelStatus.LIMITED,
                    title = "SENTINEL GLOBAL TELECOM INTELLIGENCE",
                    evidenceLabel = "Globe de contexte · synchronisation non observée",
                    evidenceSource = "registre institutionnel de référence",
                    nodes = sourceViews.take(8).map { view ->
                        com.sentinel.quantum.ui.design.SentinelHolographicSceneNode(
                            label = view.descriptor.id,
                            status = if (view.observation == TelecomObservation.REACHABLE_METADATA_ONLY) {
                                SentinelStatus.OK
                            } else {
                                SentinelStatus.LIMITED
                            }
                        )
                    }
                ),
                motionLevel = motionLevel,
                modifier = Modifier.fillMaxWidth()
            )

            SentinelStatusChip(
                status = SentinelStatus.LIMITED,
                label = "LIMITED · aucune donnée réglementaire synchronisée",
                modifier = Modifier.fillMaxWidth()
            )

            SentinelReferenceMetricStrip(
                metrics = listOf(
                    SentinelReferenceMetric("Pays / territoires", dashboard.referencedJurisdictions.toString(), "référencés localement", SentinelStatus.LIMITED),
                    SentinelReferenceMetric("Régulateurs", dashboard.indexedRegulators.toString(), "indexés, sans enforcement", SentinelStatus.LIMITED),
                    SentinelReferenceMetric("Sources vérifiées", dashboard.verifiedSources.toString(), "preuve de récupération", if (dashboard.verifiedSources > 0) SentinelStatus.OK else SentinelStatus.UNKNOWN),
                    SentinelReferenceMetric("Connecteurs actifs", dashboard.activeConnectors.toString(), "parseurs de données", if (dashboard.activeConnectors > 0) SentinelStatus.OK else SentinelStatus.LOCKED)
                )
            )

            SentinelSectionHeader(
                title = "Explorer un indicatif ou un numéro",
                subtitle = "Un numéro national exige un pays de contexte. Un indicatif partagé reste ambigu et ne prouve jamais l’origine réelle."
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(64) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Recherche par indicatif ou numéro" },
                singleLine = true,
                leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("+33, +1 202… ou numéro E.164") }
            )
            resolution?.let { result ->
                SentinelGlassCard(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(result.canonicalE164 ?: "Aucun format normalisé", style = MaterialTheme.typography.titleMedium)
                            Text(
                                result.matchingEntries.joinToString { it.name }.ifBlank { "Indicatif absent du répertoire local" },
                                color = SentinelVisualTokens.TextSecondary
                            )
                        }
                        SentinelStatusChip(
                            status = when (result.status) {
                                InternationalNumberResolver.Status.NORMALIZED -> SentinelStatus.OK
                                InternationalNumberResolver.Status.AMBIGUOUS -> SentinelStatus.WARN
                                InternationalNumberResolver.Status.NATIONAL_CONTEXT_REQUIRED -> SentinelStatus.LIMITED
                                InternationalNumberResolver.Status.INVALID -> SentinelStatus.BAD
                                InternationalNumberResolver.Status.UNAVAILABLE -> SentinelStatus.UNKNOWN
                            },
                            label = result.status.name
                        )
                    }
                    Text(
                        "Décision : annotation uniquement · aucune règle de blocage créée",
                        color = SentinelD1.Warning,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            SentinelSectionHeader(
                title = "Registre des sources",
                subtitle = "La présence d’un site officiel ne signifie pas qu’une API, une licence ou un jeu de données réutilisable est disponible."
            )
            Button(
                onClick = {
                    if (probeInProgress) return@Button
                    probeInProgress = true
                    probeMessage = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            repository.probeOfficialSource(
                                sourceId = "ARCEP",
                                transport = OkHttpOfficialSourceTransport(),
                                nowMs = System.currentTimeMillis()
                            )
                        }
                        probeMessage = when (result?.outcome) {
                            TelecomSyncOutcome.REACHABLE_METADATA_ONLY -> "ARCEP joignable · métadonnées conservées, aucun plan de numérotation inféré."
                            TelecomSyncOutcome.VALIDATED_DATASET -> "Jeu de données validé · autorité d’enforcement : aucune."
                            TelecomSyncOutcome.UNAVAILABLE -> "ARCEP indisponible · aucune donnée distante utilisée."
                            TelecomSyncOutcome.INVALID_SOURCE, null -> "Source non validée · aucune donnée distante utilisée."
                        }
                        probeInProgress = false
                        refreshToken++
                    }
                },
                enabled = !probeInProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (probeInProgress) "Vérification ARCEP…" else "Vérifier une source officielle (ARCEP)")
            }
            probeMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = SentinelVisualTokens.TextSecondary)
            }
            sourceViews.forEach { view ->
                SentinelGlassCard(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(view.descriptor.publisher, style = MaterialTheme.typography.titleMedium)
                            Text("${view.descriptor.id} · ${view.descriptor.jurisdiction}", color = SentinelVisualTokens.Cyan)
                            Text(view.descriptor.officialUrl, style = MaterialTheme.typography.bodySmall, color = SentinelVisualTokens.TextSecondary)
                        }
                        SentinelStatusChip(
                            status = if (view.descriptor.connectorStatus == ConnectorStatus.SYNCED) SentinelStatus.OK else SentinelStatus.LIMITED,
                            label = if (view.descriptor.connectorStatus == ConnectorStatus.SYNCED) "SYNCED" else "REFERENCE"
                        )
                    }
                    Text(
                        when (view.observation) {
                            TelecomObservation.REACHABLE_METADATA_ONLY -> "Site joignable ; aucun jeu de numérotation validé par ce connecteur."
                            TelecomObservation.VALIDATED_DATASET -> "Jeu de données validé ; autorité d’enforcement : aucune."
                            TelecomObservation.STALE -> "Dernière preuve expirée ; donnée hors ligne non utilisée comme preuve actuelle."
                            TelecomObservation.UNAVAILABLE -> "Source indisponible ; aucune donnée distante utilisée."
                            TelecomObservation.INVALID -> "En attente d’un connecteur et d’une licence validés."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SentinelVisualTokens.TextSecondary
                    )
                }
            }

            Button(onClick = { navController.navigate(Screen.CallBlocking.route) }, Modifier.fillMaxWidth()) {
                Text("Gérer les règles locales et les blocages explicites")
            }
            Button(onClick = { navController.navigate(Screen.ThreatMap.route) }, Modifier.fillMaxWidth()) {
                Text("Voir la carte D.1 des menaces")
            }
            Text(
                "Les indicatifs, territoires et sites sont des éléments de contexte. Ils ne constituent ni une identité vérifiée, ni une preuve de fraude, ni une autorisation de rejeter un appel.",
                style = MaterialTheme.typography.bodySmall,
                color = SentinelVisualTokens.TextSecondary
            )
        }
    }
}
