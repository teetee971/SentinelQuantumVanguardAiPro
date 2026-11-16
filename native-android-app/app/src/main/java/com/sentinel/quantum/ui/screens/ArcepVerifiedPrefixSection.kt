package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.security.ArcepVerifiedPrefixCatalog
import com.sentinel.quantum.security.CallBlocklistStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArcepVerifiedPrefixSection(
    store: CallBlocklistStore,
    onRulesChanged: (CallBlocklistStore.Snapshot, String) -> Unit
) {
    var enabled by remember { mutableStateOf(store.isArcepVerifiedBlockingEnabled()) }
    var showSheet by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    HorizontalDivider()
    Text("Protection ARCEP contre les appels automatisés", fontWeight = FontWeight.Bold)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (enabled) "Protection ARCEP active" else "Protection ARCEP inactive",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (enabled) {
                            "${ArcepVerifiedPrefixCatalog.entries.size} plages sont appliquées au filtrage local."
                        } else {
                            "${ArcepVerifiedPrefixCatalog.entries.size} plages disponibles · aucune n’est appliquée automatiquement."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { requested ->
                        if (store.setArcepVerifiedBlockingEnabled(requested)) {
                            enabled = requested
                            onRulesChanged(
                                store.snapshot(),
                                if (requested) {
                                    "Protection ARCEP activée : les plages officielles sont maintenant appliquées au filtrage local."
                                } else {
                                    "Protection ARCEP désactivée : vos préfixes personnels restent inchangés."
                                }
                            )
                        } else {
                            onRulesChanged(
                                store.snapshot(),
                                "Impossible d’activer la protection ARCEP : capacité maximale de règles atteinte."
                            )
                        }
                    }
                )
            }

            Text(
                "Ces plages sont des racines réglementaires pouvant servir à des appels automatisés. Elles ne prouvent ni une fraude, ni l’identité, ni la localisation réelle de l’appelant.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = { showSheet = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Voir les ${ArcepVerifiedPrefixCatalog.entries.size} préfixes ARCEP")
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSheet = false
                query = ""
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.86f)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Préfixes ARCEP", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (enabled) "Protection active · ces plages sont actuellement appliquées." else "Protection inactive · liste informative uniquement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Rechercher") },
                    placeholder = { Text("Guadeloupe, 09475, +590…") }
                )

                val normalizedQuery = query.trim().lowercase()
                val entries = remember(normalizedQuery) {
                    ArcepVerifiedPrefixCatalog.entries.filter { entry ->
                        normalizedQuery.isBlank() ||
                            entry.territory.lowercase().contains(normalizedQuery) ||
                            entry.nationalRoot.contains(normalizedQuery) ||
                            entry.e164Prefix.contains(normalizedQuery)
                    }
                }
                Text(
                    "${entries.size} résultat(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries, key = { it.e164Prefix }) { entry ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        territoryCode(entry.territory),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(entry.territory, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${entry.nationalRoot}  →  ${entry.e164Prefix}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = if (enabled) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ) {
                                    Text(
                                        if (enabled) "Actif" else "Inactif",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun territoryCode(territory: String): String = when {
    territory.startsWith("France métropolitaine") -> "FR"
    territory.startsWith("Guadeloupe") -> "GP"
    territory.startsWith("Guyane") -> "GF"
    territory.startsWith("Martinique") -> "MQ"
    territory.startsWith("La Réunion") -> "RE"
    territory.startsWith("Mayotte") -> "YT"
    else -> "FR"
}
