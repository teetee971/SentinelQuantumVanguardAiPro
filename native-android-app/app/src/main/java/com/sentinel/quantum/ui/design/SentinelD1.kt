package com.sentinel.quantum.ui.design

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object SentinelD1 {
    val Background = Color(0xFF080B10)
    val Panel = Color(0xFF0E131C)
    val Card = Color(0xFF111826)
    val Border = Color(0xFF1C2740)
    val BorderStrong = Color(0xFF2E3F63)
    val Cyan = Color(0xFF3FC7FF)
    val Success = Color(0xFF28D99A)
    val Warning = Color(0xFFFFB020)
    val Danger = Color(0xFFFF5470)
    val Unknown = Color(0xFF8B97B3)
}

private data class StateVisual(val color: Color, val icon: ImageVector)

@Composable
private fun stateVisual(state: SentinelState): StateVisual = when (state) {
    SentinelState.TO_CONFIGURE -> StateVisual(SentinelD1.Unknown, Icons.Default.Settings)
    SentinelState.READY -> StateVisual(SentinelD1.Success, Icons.Default.CheckCircle)
    SentinelState.TO_TEST -> StateVisual(SentinelD1.Cyan, Icons.Default.HourglassTop)
    // Validation is a semantic success, not a second brand accent.
    SentinelState.VALIDATED -> StateVisual(SentinelD1.Success, Icons.Default.Verified)
    SentinelState.PARTIAL -> StateVisual(SentinelD1.Warning, Icons.Default.ErrorOutline)
    SentinelState.DEGRADED -> StateVisual(SentinelD1.Warning, Icons.Default.HourglassTop)
    SentinelState.BLOCKED -> StateVisual(SentinelD1.Danger, Icons.Default.Block)
    SentinelState.UNAVAILABLE -> StateVisual(SentinelD1.Unknown, Icons.Default.Block)
}

@Composable
fun SentinelStateChip(
    state: SentinelState,
    modifier: Modifier = Modifier,
    label: String = PhoneCoreUiState.label(state)
) {
    val visual = stateVisual(state)
    val shape = RoundedCornerShape(999.dp)
    Surface(
        modifier = modifier
            .semantics { contentDescription = "État : $label" }
            .border(1.dp, visual.color.copy(alpha = 0.45f), shape),
        shape = shape,
        color = visual.color.copy(alpha = 0.14f),
        contentColor = visual.color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(visual.icon, contentDescription = null)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


@Composable
fun SentinelEvidenceProgress(
    label: String,
    completed: Int,
    required: Int,
    modifier: Modifier = Modifier
) {
    val safeRequired = required.coerceAtLeast(1)
    val safeCompleted = completed.coerceIn(0, safeRequired)
    val fraction = safeCompleted.toFloat() / safeRequired.toFloat()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$label : $safeCompleted sur $safeRequired"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = safeCompleted.toFloat(),
                    range = 0f..safeRequired.toFloat(),
                    steps = (safeRequired - 1).coerceAtLeast(0)
                )
            },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$safeCompleted/$safeRequired",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = if (safeCompleted == safeRequired) SentinelD1.Success else SentinelD1.Cyan,
            trackColor = SentinelD1.Border
        )
    }
}
