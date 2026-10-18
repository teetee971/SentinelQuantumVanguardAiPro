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
fun QuarantineScreen(navController: NavController) {
    Scaffold(
        topBar = {
            SentinelTopBar(
                title = "Quarantaine sécurisée",
                subtitle = "Confinement vérifiable uniquement",
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
                    kind = SentinelSceneKind.QUARANTINE_CHAMBER,
                    status = SentinelStatus.LOCKED,
                    title = "QUARANTAINE SÉCURISÉE",
                    evidenceLabel = "Confinement global non disponible",
                    evidenceSource = "aucun mécanisme applicatif observé"
                ),
                motionLevel = com.sentinel.quantum.ui.design.MotionLevel.STATIC,
                modifier = Modifier.fillMaxWidth()
            )
            SentinelStatusChip(
                status = SentinelStatus.LOCKED,
                label = "CONFINEMENT · VERROUILLÉ",
                modifier = Modifier.fillMaxWidth()
            )
            SentinelSectionHeader(
                title = "Portée réelle",
                subtitle = "La présence d’une branche de quarantaine dans un pipeline de réception ne signifie pas qu’une menace est isolée au niveau du système."
            )
            SentinelEmptyState(
                title = "Aucune menace confinée à afficher",
                detail = "Les contrôles Android actuels peuvent conserver certains MMS invalides ou dangereux dans un chemin privé. Cette surface ne transforme pas ce mécanisme en quarantaine antivirus et n’invente aucune menace isolée."
            )
            Button(
                onClick = { navController.navigate(Screen.SmsScanner.route) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ouvrir l’analyse SMS/MMS réelle")
            }
        }
    }
}
