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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemDoctorScreen(navController: NavController) {
    val context = LocalContext.current
    val doctor = remember { SentinelSystemDoctor(context) }
    var scan by remember { mutableStateOf<SentinelSystemDoctor.Scan?>(null) }

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
                onClick = { scan = doctor.scan() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (scan == null) "Lancer le scan local" else "Relancer le scan")
            }

            scan?.let { result ->
                SentinelSectionHeader(
                    title = "Résultat",
                    subtitle = "Risque observé : " + result.report.highestObservedRisk.name
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
            Text(evidence.status.name, style = MaterialTheme.typography.labelLarge)
            Text(evidence.summary)
            evidence.observedValue?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
