package com.sentinel.quantum.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.quantum.SentinelDialerActivity
import com.sentinel.quantum.ui.design.SentinelD1
import com.sentinel.quantum.ui.design.SentinelGlassCard
import com.sentinel.quantum.ui.design.SentinelHeroVisual
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelStatus
import com.sentinel.quantum.ui.design.SentinelStatusChip
import com.sentinel.quantum.ui.design.SentinelTopBar

/** Number profile presentation matching the Sentinel visual direction. */
@Composable
fun NumberSearchScreen() {
    val context = LocalContext.current
    var number by rememberSaveable { mutableStateOf("") }
    val hasNumber = number.trim().isNotEmpty()

    Scaffold(
        topBar = { SentinelTopBar(title = "Sentinel", subtitle = "Fiche numéro") }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it.take(32) },
                modifier = Modifier.fillMaxWidth().testTag("number_profile_search"),
                label = { Text("Numéro à identifier") },
                placeholder = { Text("+33 6 12 34 56 78") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )
            Button(
                onClick = { context.startActivity(Intent(context, SentinelDialerActivity::class.java)) },
                modifier = Modifier.fillMaxWidth().testTag("number_profile_open_dialer")
            ) {
                Icon(Icons.Default.PhoneInTalk, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Rechercher dans Phone Core")
            }

            NumberProfileHero(number = number.trim().ifBlank { "Numéro inconnu" }, isInput = hasNumber)
            SentinelSectionHeader(title = "Identité et fiabilité", trailing = "Données disponibles")
            ReliabilityCard(hasNumber)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SourceCard("Provenance", if (hasNumber) "À vérifier" else "Aucune donnée", Icons.Default.Public, SentinelD1.Cyan, Modifier.weight(1f))
                SourceCard("Réseau", "Non mesuré", Icons.Default.LocationOn, SentinelD1.Unknown, Modifier.weight(1f))
            }

            SentinelSectionHeader(title = "Signalements et observations", trailing = "État local")
            SentinelGlassCard(modifier = Modifier.fillMaxWidth().testTag("number_profile_observations")) {
                ObservationRow("Démarchage commercial", "Aucun signalement observé")
                ObservationRow("Spam téléphonique", "Aucun signalement observé")
                ObservationRow("Tentative de fraude", "Aucune donnée disponible")
                Text("Les résultats définitifs nécessitent une recherche Phone Core et les permissions correspondantes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SentinelSectionHeader(title = "Actions sur ce numéro")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileAction("Autoriser", Icons.Default.CheckCircle, SentinelD1.Success, Modifier.weight(1f))
                ProfileAction("Bloquer", Icons.Default.Block, SentinelD1.Danger, Modifier.weight(1f))
                ProfileAction("Signaler", Icons.Default.Flag, SentinelD1.Warning, Modifier.weight(1f))
                ProfileAction("Ajouter", Icons.Default.AddCircleOutline, SentinelD1.Cyan, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NumberProfileHero(number: String, isInput: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SentinelHeroVisual(
            status = SentinelStatus.LIMITED,
            title = "$number\nNuméro inconnu",
            subtitle = if (isInput) "Recherche prête · aucune identité confirmée" else "Saisissez un numéro pour commencer",
            modifier = Modifier.testTag("number_profile_hero")
        )
        SentinelStatusChip(status = SentinelStatus.LIMITED, label = "Données limitées")
    }
}

@Composable
private fun ReliabilityCard(hasNumber: Boolean) {
    SentinelGlassCard(modifier = Modifier.fillMaxWidth().testTag("number_profile_reliability")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ReliabilityMetric("Score", "—", Icons.Default.Security, SentinelD1.Warning, Modifier.weight(1f))
            ReliabilityMetric("Fraîcheur", "—", Icons.Default.CheckCircle, SentinelD1.Cyan, Modifier.weight(1f))
            ReliabilityMetric("Confiance", if (hasNumber) "À vérifier" else "Inconnue", Icons.AutoMirrored.Filled.HelpOutline, SentinelD1.Unknown, Modifier.weight(1f))
        }
        Text("Aucun score ne sera calculé sans source vérifiable.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReliabilityMetric(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SourceCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, modifier: Modifier) {
    ElevatedCard(modifier = modifier, colors = CardDefaults.elevatedCardColors(containerColor = SentinelD1.Card.copy(alpha = 0.92f)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = accent)
            Text(title, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ObservationRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = SentinelD1.Warning, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, modifier: Modifier) {
    ElevatedCard(modifier = modifier.height(86.dp), colors = CardDefaults.elevatedCardColors(containerColor = accent.copy(alpha = 0.11f)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxSize().padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
