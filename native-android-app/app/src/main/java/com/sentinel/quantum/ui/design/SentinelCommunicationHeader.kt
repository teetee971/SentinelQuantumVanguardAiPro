package com.sentinel.quantum.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shared D.1 hierarchy for communication surfaces; no inferred risk or readiness state. */
@Composable
fun SentinelCommunicationHeader(section: String, title: String, description: String) {
    SentinelGlassCard(Modifier.fillMaxWidth().testTag("d1-communication-header")) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(section, style = MaterialTheme.typography.labelLarge, color = SentinelD1.Cyan)
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().semantics { heading() })
            Text(description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
        }
    }
}
