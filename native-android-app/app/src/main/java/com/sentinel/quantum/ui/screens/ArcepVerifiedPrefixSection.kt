package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.security.ArcepVerifiedPrefixCatalog
import com.sentinel.quantum.security.CallBlocklistStore

@Composable
internal fun ArcepVerifiedPrefixSection(
    store: CallBlocklistStore,
    onRulesChanged: (CallBlocklistStore.Snapshot, String) -> Unit
) {
    var enabled by remember { mutableStateOf(store.isArcepVerifiedBlockingEnabled()) }
    var expanded by remember { mutableStateOf(false) }

    HorizontalDivider()
    Text("Protection ARCEP contre les appels automatisés", fontWeight = FontWeight.Bold)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (enabled) "Blocage ARCEP activé" else "Blocage ARCEP désactivé",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${ArcepVerifiedPrefixCatalog.entries.size} plages officielles · activation volontaire",
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
                                    "Protection ARCEP activée : les plages affichées sont maintenant bloquées."
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
                "Ces racines correspondent aux numéros polyvalents vérifiés pouvant être utilisés par des systèmes automatisés d’appels ou de messages. Elles ne prouvent ni une fraude ni l’identité de l’appelant.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (expanded) "Masquer les préfixes ARCEP" else "Afficher les préfixes ARCEP")
            }

            if (expanded) {
                ArcepVerifiedPrefixCatalog.entries.forEach { entry ->
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                listOf(entry.flag, entry.territory)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" "),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "National ${entry.nationalRoot} · moteur ${entry.e164Prefix}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(if (enabled) "Bloqué" else "Inactif") }
                        )
                    }
                }
            }
        }
    }
}
