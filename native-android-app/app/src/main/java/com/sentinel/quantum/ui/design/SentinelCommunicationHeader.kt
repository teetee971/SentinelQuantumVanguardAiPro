package com.sentinel.quantum.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.R

/** D.1-inspired decorative artwork is decorative: it carries no invented protection or identity claim. */
@Composable
fun SentinelCommunicationHeader(section: String, title: String, description: String, compact: Boolean = false,
    actions: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().testTag("d1-communication-header"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!compact) Image(painterResource(R.drawable.d1_orbital_globe), contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(176.dp), contentScale = ContentScale.Crop)
        SentinelGlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (compact) Image(painterResource(R.drawable.d1_unknown_identity), contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(section, style = MaterialTheme.typography.labelLarge, color = SentinelD1.Cyan)
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().semantics { heading() })
                    Text(description, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                }
            }
            actions()
        }
    }
}
