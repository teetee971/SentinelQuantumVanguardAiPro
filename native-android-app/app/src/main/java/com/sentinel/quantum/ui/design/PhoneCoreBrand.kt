package com.sentinel.quantum.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.R

/**
 * Compact D.1 identity for Phone Core surfaces.
 *
 * This is intentionally presentation-only: [status] must describe observed application state
 * supplied by the caller. The component never upgrades readiness or physical-validation truth.
 */
@Composable
fun PhoneCoreBrand(
    context: String,
    status: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .border(1.dp, SentinelD1.BorderStrong, RoundedCornerShape(18.dp))
            .clearAndSetSemantics {
                contentDescription = "Sentinel Phone Core, $context, $status"
            },
        shape = RoundedCornerShape(18.dp),
        color = SentinelD1.Panel
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(12.dp),
                color = SentinelD1.Card
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_sentinel_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.padding(7.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "SENTINEL · PHONE CORE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = SentinelD1.Cyan
                )
                Text(
                    text = context,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
