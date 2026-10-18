package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.ui.design.SentinelEmptyState
import com.sentinel.quantum.ui.design.SentinelHolographicScene
import com.sentinel.quantum.ui.design.SentinelHolographicSceneModel
import com.sentinel.quantum.ui.design.SentinelSceneKind
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelStatus
import com.sentinel.quantum.ui.design.SentinelStatusChip
import com.sentinel.quantum.ui.design.SentinelTopBar

@Composable
fun ThreatDetailScreen(navController: NavController) {
    val evidenceAvailable = false
    val threatStatus = if (evidenceAvailable) SentinelStatus.BAD else SentinelStatus.UNKNOWN

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = "Détail de la menace",
                subtitle = "Dossier de preuve",
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
                    kind = SentinelSceneKind.THREAT_DETAIL,
                    status = threatStatus,
                    title = "DÉTAIL MENACE",
                    evidenceLabel = if (evidenceAvailable) "Menace observée" else "Aucune menace confirmée",
                    evidenceSource = if (evidenceAvailable) "dossier d’analyse fourni" else "aucun dossier de preuve sélectionné"
                ),
                motionLevel = com.sentinel.quantum.ui.design.MotionLevel.STATIC,
                modifier = Modifier.fillMaxWidth()
            )
            SentinelStatusChip(
                status = threatStatus,
                label = if (evidenceAvailable) "CRITIQUE · OBSERVÉ" else "INCONNU · AUCUNE PREUVE",
                modifier = Modifier.fillMaxWidth()
            )
            SentinelSectionHeader(
                title = "Dossier non sélectionné",
                subtitle = "Le rouge critique est réservé à une détection réelle accompagnée d’une source et d’une justification."
            )
            SentinelEmptyState(
                title = "Pas de constat à examiner",
                detail = "Les images officielles illustrent un incident, mais cette application ne doit pas le présenter comme une menace détectée sans résultat provenant d’un contrôle local, d’un signal SMS ou d’une source autorisée."
            )
            Button(
                onClick = { navController.navigate(Screen.AppPermissionAnalyzer.route) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Analyser les permissions des applications")
            }
            Button(
                onClick = { navController.navigate(Screen.SmsScanner.route) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Analyser un SMS ou un lien")
            }
        }
    }
}
