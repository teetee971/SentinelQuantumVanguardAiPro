package com.sentinel.quantum.ui.screens

import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.ui.design.MotionLevel
import com.sentinel.quantum.ui.design.SentinelEmptyState
import com.sentinel.quantum.ui.design.SentinelHolographicScene
import com.sentinel.quantum.ui.design.SentinelHolographicSceneModel
import com.sentinel.quantum.ui.design.SentinelSceneKind
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelStatus
import com.sentinel.quantum.ui.design.SentinelStatusChip
import com.sentinel.quantum.ui.design.SentinelTopBar
import com.sentinel.quantum.ui.design.SentinelVisualTokens

@Composable
fun ThreatMapScreen(navController: NavController) {
    val context = LocalContext.current
    val motionLevel = remember(context) {
        val animatorScale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        val powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        com.sentinel.quantum.ui.design.SentinelMotionPolicy.resolve(animatorScale, powerSave)
    }

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = "Carte des menaces",
                subtitle = "Géographie uniquement sur preuve",
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
                    status = SentinelStatus.UNKNOWN,
                    title = "CARTE MONDIALE DES MENACES",
                    evidenceLabel = "Données géographiques indisponibles",
                    evidenceSource = "aucune preuve géographique observée"
                ),
                motionLevel = motionLevel,
                modifier = Modifier.fillMaxWidth()
            )
            SentinelStatusChip(
                status = SentinelStatus.UNKNOWN,
                label = "MONDE · INCONNU",
                modifier = Modifier.fillMaxWidth()
            )
            SentinelSectionHeader(
                title = "Pourquoi la carte reste neutre",
                subtitle = "Une observation locale, un flux OSINT ou un signal communautaire ne fournit pas automatiquement une coordonnée mondiale vérifiée."
            )
            SentinelEmptyState(
                title = "Aucun événement géolocalisé prouvé",
                detail = "Sentinel n’affiche pas de points chauds, de pays ou de volumes mondiaux sans source, fraîcheur et localisation observables. La scène 3D reste une structure de visualisation, pas une carte de menaces fictive."
            )
            Button(
                onClick = { navController.navigate(Screen.OsintFeed.route) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Consulter les événements OSINT disponibles")
            }
            Button(
                onClick = { navController.navigate(Screen.CollectiveDefense.route) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Analyser un indicateur collectif")
            }
            Text(
                "Les données illustratives des maquettes officielles ne sont pas recopiées dans l’état runtime.",
                style = MaterialTheme.typography.bodySmall,
                color = SentinelVisualTokens.TextSecondary
            )
        }
    }
}
