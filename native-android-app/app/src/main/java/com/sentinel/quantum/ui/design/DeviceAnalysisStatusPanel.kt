package com.sentinel.quantum.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.ui.presentation.AnalysisLayerState
import com.sentinel.quantum.ui.presentation.DeviceAnalysisLayerUiState
import com.sentinel.quantum.ui.presentation.DeviceAnalysisUiState

/**
 * Shared D.1 summary for local device-analysis checks.
 *
 * Wording is deliberately limited to checks/evidence. This component must not label permission
 * heuristics or configuration warnings as malware, viruses, quarantine events, or blocked threats.
 */
@Composable
fun DeviceAnalysisStatusPanel(
    state: DeviceAnalysisUiState,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    SentinelGlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SentinelStatusChip(status = state.overallStatus)
        }

        AnalysisLayerRow("Installation", state.installation)
        AnalysisLayerRow("Autorisations", state.permissions)
        AnalysisLayerRow("Applications", state.applications)

        state.explicitFindingCount?.let { count ->
            Text(
                text = if (count == 0) {
                    "Aucun constat explicite dans les contrôles exécutés."
                } else {
                    "$count constat(s) explicite(s) dans les contrôles exécutés."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } ?: Text(
            text = "Le nombre global de constats n’est pas disponible pour tous les contrôles.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AnalysisLayerRow(
    label: String,
    layer: DeviceAnalysisLayerUiState
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(
                text = layer.state.userFacingLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SentinelStatusChip(
            status = layer.status(),
            label = layer.state.shortLabel()
        )
    }
}

private fun DeviceAnalysisLayerUiState.status(): SentinelStatus = when (state) {
    AnalysisLayerState.COMPLETE -> when (healthyEvidence) {
        true -> if ((findingCount ?: 0) > 0) SentinelStatus.WARN else SentinelStatus.OK
        false -> SentinelStatus.WARN
        null -> SentinelStatus.LIMITED
    }
    AnalysisLayerState.RUNNING -> SentinelStatus.LIMITED
    AnalysisLayerState.NOT_EXECUTED -> SentinelStatus.UNKNOWN
    AnalysisLayerState.UNAVAILABLE -> SentinelStatus.LIMITED
    AnalysisLayerState.FAILED -> SentinelStatus.WARN
}

private fun AnalysisLayerState.shortLabel(): String = when (this) {
    AnalysisLayerState.COMPLETE -> "Terminé"
    AnalysisLayerState.RUNNING -> "En cours"
    AnalysisLayerState.NOT_EXECUTED -> "Non exécuté"
    AnalysisLayerState.UNAVAILABLE -> "Indisponible"
    AnalysisLayerState.FAILED -> "Échec"
}

private fun AnalysisLayerState.userFacingLabel(): String = when (this) {
    AnalysisLayerState.COMPLETE -> "Contrôle exécuté avec un résultat observable."
    AnalysisLayerState.RUNNING -> "Contrôle local en cours d’exécution."
    AnalysisLayerState.NOT_EXECUTED -> "Ce contrôle n’a pas encore été exécuté."
    AnalysisLayerState.UNAVAILABLE -> "Ce contrôle n’est pas fourni par cette surface."
    AnalysisLayerState.FAILED -> "Le contrôle n’a pas pu produire de résultat exploitable."
}
